import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { paths } from "../config.ts";
import { answerSongs, connect } from "./publish.ts";

/** One answer song's clip start, as vocals/find_vocal_start.py found it. */
export interface VocalStart {
  /** Where the singing starts in the preview; null when the preview has none. */
  vocalStartMs: number | null;
  /** Where its clips start: a little before the singing, never in silence. */
  clipStartMs: number;
  durationMs: number;
  /** The preview that was analysed; a start only applies to this audio. */
  previewUrl?: string;
}

const LIST = "songs.json";
const RESULTS = "vocals.json";

/**
 * Writes the answer songs and their previews for the vocal finder to work through:
 *
 *     npm run vocals [-- --production]
 *     <venv python> vocals/find_vocal_start.py data/vocals/songs.json data/vocals/vocals.json
 *
 * With [production], each song already published is listed with the preview the live server
 * plays (it refreshes them), so the finder analyses exactly that audio. `publish` then gives each
 * song its clip start.
 */
export async function writeVocalsList(production = false): Promise<void> {
  const answers = await answerSongs();
  const live = new Map<string, string>();
  if (production) {
    const { db } = await connect(true);
    for (let i = 0; i < answers.length; i += 400) {
      const refs = answers.slice(i, i + 400).map((a) => db.doc(`songs/${a.tiered.songId}`));
      for (const snap of await db.getAll(...refs)) {
        const url = snap.get("previewUrl") as string | undefined;
        if (url) live.set(snap.id, url);
      }
    }
  }
  const songs = answers.map(({ tiered, track }) => ({ songId: tiered.songId, previewUrl: live.get(tiered.songId) ?? track.previewUrl }));
  await mkdir(paths.vocals, { recursive: true });
  await writeFile(path.join(paths.vocals, LIST), JSON.stringify(songs));
  const done = await readVocalStarts();
  const left = songs.filter((s) => done.get(s.songId)?.previewUrl !== s.previewUrl).length;
  console.log(`${songs.length} answer songs listed; ${left} not yet checked on this preview. Run:`);
  // A virtual environment keeps Python in Scripts on Windows, bin elsewhere.
  const python = process.platform === "win32" ? path.join(".venv", "Scripts", "python") : path.join(".venv", "bin", "python");
  console.log(`  ${python} vocals/find_vocal_start.py ${path.relative(process.cwd(), path.join(paths.vocals, LIST))} ${path.relative(process.cwd(), path.join(paths.vocals, RESULTS))}`);
}

/** The finder's results so far, by songId; empty before its first run. */
export async function readVocalStarts(): Promise<Map<string, VocalStart>> {
  // Only a file that isn't there yet counts as no results. One that can't be read must stop the
  // caller: publish would otherwise write every clip start as if nothing had been analysed.
  const text = await readFile(path.join(paths.vocals, RESULTS), "utf8").catch((error: NodeJS.ErrnoException) => {
    if (error.code === "ENOENT") return "{}";
    throw error;
  });
  return new Map(Object.entries(JSON.parse(text) as Record<string, VocalStart>));
}

/** How long before the singing a clip starts (vocals/find_vocal_start.py, LEAD_MS). */
const LEAD_MS = 600;

/**
 * True when a song's short clips can't reach a voice: its preview has no singing, or the singing
 * comes so late that the ten-second full-clip rule held the start back. Such a song plays only
 * intro music until the full clip, so it isn't scheduled.
 */
export function hasNoEarlySinging(found: VocalStart): boolean {
  return found.vocalStartMs === null || found.vocalStartMs - LEAD_MS > found.clipStartMs + 50;
}
