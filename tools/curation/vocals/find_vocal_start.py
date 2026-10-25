"""
Finds where the singing starts in each song's iTunes preview, so a clip can start just before it.

Reads a JSON list of {"songId", "previewUrl"} and adds {"vocalStartMs", "clipStartMs",
"durationMs", "previewUrl"} per song to the results file (vocalStartMs is null when the preview
has no singing). previewUrl is the preview that was analysed: a song whose preview has changed is
checked again, so a clip start never applies to different audio. Songs already done are skipped,
so a run can be stopped and picked up again.

The clip starts LEAD_MS before the singing, so the shortest clip is music and the voice comes in
during the next one; a preview that opens mid-line keeps its start at 0. A clip never starts in
silence: if the music stops just before the voice, the clip starts where the silence ends.

The vocals are separated from the music with Demucs (htdemucs). Singing "starts" at the first
stretch of at least MIN_RUN_MS where the vocal track is within BELOW_PEAK_DB of the singer's own
loud level in the song: a soft entry under loud music counts, while the faint bleed separation
leaves in instrumental parts (30-50 dB under the voice) doesn't.

    .venv/Scripts/python vocals/find_vocal_start.py songs.json data/vocals/vocals.json
"""

import json
import os
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import av
import numpy as np
import torch
from demucs.apply import apply_model
from demucs.pretrained import get_model

SAMPLE_RATE = 44_100
FRAME_MS = 50
HOP_MS = 25
# Singing: within this much of the singer's loud level (the 95th percentile of the voice), above
# an absolute floor, and not buried far under the whole mix.
BELOW_PEAK_DB = 15.0
MIN_VOCAL_DB = -45.0
MAX_BELOW_MIX_DB = 25.0
# Singing, not a breath or a stray "hey": this long, allowing short gaps between words.
MIN_RUN_MS = 400
MAX_GAP_MS = 150
# The clip starts this long before the singing: the 0.5 s clip is music, the 1 s clip reaches the voice.
LEAD_MS = 600
# Quieter than this (whole mix, dBFS) is silence, and a clip doesn't start in it.
SILENCE_DB = -40.0
# The full clip always gets at least this long: a clip never starts later in the preview.
MIN_FULL_CLIP_MS = 10_000
# Bumped when the rules change. A song done under an older version whose vocal start still holds
# (RULES_SAME_VOCALS) only has its clip start worked out again, from its cached preview.
RULES = 3
RULES_SAME_VOCALS = {2}


def decode(path: Path) -> np.ndarray:
    """The preview as float32 stereo at 44.1 kHz, shape (2, samples)."""
    with av.open(str(path)) as container:
        resampler = av.AudioResampler(format="fltp", layout="stereo", rate=SAMPLE_RATE)
        chunks = []
        for frame in container.decode(audio=0):
            for out in resampler.resample(frame):
                chunks.append(out.to_ndarray())
        for out in resampler.resample(None):
            chunks.append(out.to_ndarray())
    return np.concatenate(chunks, axis=1).astype(np.float32)


def frame_db(mono: np.ndarray) -> np.ndarray:
    """Loudness per frame in dBFS."""
    frame = SAMPLE_RATE * FRAME_MS // 1000
    hop = SAMPLE_RATE * HOP_MS // 1000
    count = max(1, 1 + (len(mono) - frame) // hop)
    rms = np.array([np.sqrt(np.mean(mono[i * hop : i * hop + frame] ** 2) + 1e-12) for i in range(count)])
    return 20 * np.log10(rms)


def vocal_start_ms(vocals: np.ndarray, mix: np.ndarray) -> int | None:
    voice = frame_db(vocals.mean(axis=0))
    whole = frame_db(mix.mean(axis=0))
    audible = voice[voice > -60]
    if len(audible) == 0:
        return None
    peak = float(np.percentile(audible, 95))
    singing = (voice > max(MIN_VOCAL_DB, peak - BELOW_PEAK_DB)) & (voice > whole - MAX_BELOW_MIX_DB)
    need = MIN_RUN_MS // HOP_MS
    gap_allowed = MAX_GAP_MS // HOP_MS
    start, run, gap = None, 0, 0
    for i, on in enumerate(singing):
        if on:
            if start is None:
                start = i
            run += 1 + gap
            gap = 0
            if run >= need:
                return start * HOP_MS
        elif start is not None:
            gap += 1
            if gap > gap_allowed:
                start, run, gap = None, 0, 0
    return None


def clip_start_ms(vocal_start: int | None, mix: np.ndarray) -> int:
    """Where the clip starts: LEAD_MS before the singing, never in silence, never too near the end."""
    if vocal_start is None:
        return 0
    latest = max(0, mix.shape[1] * 1000 // SAMPLE_RATE - MIN_FULL_CLIP_MS)
    vocal_start = min(vocal_start, latest + LEAD_MS)
    start = max(0, vocal_start - LEAD_MS)
    whole = frame_db(mix.mean(axis=0))
    first, last = start // HOP_MS, vocal_start // HOP_MS
    silent = [i for i in range(first, min(last, len(whole))) if whole[i] < SILENCE_DB]
    # Start just after the last silent frame before the voice; but the ten-second full clip comes
    # first, so a start that would pass `latest` stays there, even if that's in the quiet bit.
    after_silence = (silent[-1] * HOP_MS + FRAME_MS) if silent else start
    return min(after_silence, latest)


def separate(model, wav: np.ndarray, device: str) -> np.ndarray:
    """The vocal stem, shape (2, samples)."""
    mix = torch.from_numpy(wav)
    ref = mix.mean(0)
    mean, std = ref.mean(), ref.std() + 1e-8
    sources = apply_model(model, ((mix - mean) / std)[None].to(device), device=device, split=True, overlap=0.25, progress=False)[0]
    sources = sources * std + mean
    return sources[model.sources.index("vocals")].cpu().numpy()


DOWNLOAD_THREADS = 4


def write_atomically(path: Path, data: bytes) -> None:
    """Writes beside [path], then swaps it in: a run stopped mid-write leaves the old file whole."""
    part = path.with_name(path.name + ".part")
    part.write_bytes(data)
    os.replace(part, path)


def save(results: dict, out_path: Path) -> None:
    write_atomically(out_path, json.dumps(results, indent=1).encode("utf-8"))


def download(song: dict, previews: Path, stale: bool = False) -> Path:
    """The song's preview on disk, fetched once; fetched again when [stale] (its URL changed)."""
    path = previews / f"{song['songId']}.m4a"
    if stale or not path.exists():
        with urllib.request.urlopen(song["previewUrl"], timeout=30) as response:
            data = response.read()
        # Never a half-written preview: one would be reused, and fail, on every later run.
        write_atomically(path, data)
    return path


def main() -> None:
    songs = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    out_path = Path(sys.argv[2])
    out_path.parent.mkdir(parents=True, exist_ok=True)
    results = json.loads(out_path.read_text(encoding="utf-8")) if out_path.exists() else {}
    previews = out_path.parent / "previews"
    previews.mkdir(exist_ok=True)

    device = "cuda" if torch.cuda.is_available() else "cpu"
    model = get_model("htdemucs")
    model.to(device).eval()

    # Results from before previewUrl was recorded were analysed from this same list's URLs.
    for song in songs:
        done = results.get(song["songId"])
        if done is not None and "previewUrl" not in done:
            done["previewUrl"] = song["previewUrl"]

    def analysed(song: dict) -> bool:
        done = results.get(song["songId"], {})
        return done.get("previewUrl") == song["previewUrl"]

    # Same preview, rules changed only in where the clip starts: no need to separate vocals again.
    recheck = [s for s in songs if analysed(s) and results[s["songId"]].get("rules") in RULES_SAME_VOCALS]
    for song in recheck:
        done = results[song["songId"]]
        path = previews / f"{song['songId']}.m4a"
        if not path.exists():
            continue
        wav = decode(path)
        done["clipStartMs"] = min(clip_start_ms(done["vocalStartMs"], wav), done["vocalStartMs"] or 0)
        done["rules"] = RULES
    if recheck:
        save(results, out_path)
        print(f"{len(recheck)} clip starts worked out again under the new rules", flush=True)

    todo = [s for s in songs if not (analysed(s) and results[s["songId"]].get("rules") == RULES)]
    print(f"{len(todo)} to check on {device} ({len(songs) - len(todo)} already done)", flush=True)

    # Downloads run ahead on their own threads, so the GPU isn't left waiting on the network.
    pool = ThreadPoolExecutor(max_workers=DOWNLOAD_THREADS)
    downloads = [pool.submit(download, song, previews, song["songId"] in results) for song in todo]
    for n, (song, fetched) in enumerate(zip(todo, downloads), 1):
        song_id = song["songId"]
        try:
            wav = decode(fetched.result())
            with torch.no_grad():
                vocals = separate(model, wav, device)
            vocal_start = vocal_start_ms(vocals, wav)
            results[song_id] = {
                "vocalStartMs": vocal_start,
                "clipStartMs": min(clip_start_ms(vocal_start, wav), vocal_start or 0),
                "durationMs": wav.shape[1] * 1000 // SAMPLE_RATE,
                "previewUrl": song["previewUrl"],
                "rules": RULES,
            }
        except Exception as error:  # One bad preview mustn't stop the run; it's retried next time.
            print(f"{song_id}: {error}", flush=True)
            continue
        if n % 10 == 0 or n == len(todo):
            save(results, out_path)
            print(f"{n}/{len(todo)}", flush=True)
    save(results, out_path)


if __name__ == "__main__":
    main()
