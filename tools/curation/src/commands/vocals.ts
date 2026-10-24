import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { paths } from "../config.ts";
import { answerSongs } from "./publish.ts";

/** One answer song's clip start, as vocals/find_vocal_start.py found it. */
export interface VocalStart {
  /** Where the singing starts in the preview; null when the preview has none. */
  vocalStartMs: number | null;
  /** Where its clips start: a little before the singing, never in silence. */
  clipStartMs: number;
  durationMs: number;
}

const LIST = "songs.json";
const RESULTS = "vocals.json";

/**
 * Writes the answer songs and their previews for the vocal finder to work through:
 *
 *     npm run vocals
 *     .venv/Scripts/python vocals/find_vocal_start.py data/vocals/songs.json data/vocals/vocals.json
 *
 * `publish` then gives each song its clip start.
 */
export async function writeVocalsList(): Promise<void> {
  const songs = (await answerSongs()).map(({ tiered, track }) => ({ songId: tiered.songId, previewUrl: track.previewUrl }));
  await mkdir(paths.vocals, { recursive: true });
  await writeFile(path.join(paths.vocals, LIST), JSON.stringify(songs));
  const done = await readVocalStarts();
  const left = songs.filter((s) => done.get(s.songId) === undefined).length;
  console.log(`${songs.length} answer songs listed; ${left} still to check. Run:`);
  console.log(`  .venv/Scripts/python vocals/find_vocal_start.py ${path.relative(process.cwd(), path.join(paths.vocals, LIST))} ${path.relative(process.cwd(), path.join(paths.vocals, RESULTS))}`);
}

/** The finder's results so far, by songId; empty before its first run. */
export async function readVocalStarts(): Promise<Map<string, VocalStart>> {
  const text = await readFile(path.join(paths.vocals, RESULTS), "utf8").catch(() => "{}");
  return new Map(Object.entries(JSON.parse(text) as Record<string, VocalStart>));
}
