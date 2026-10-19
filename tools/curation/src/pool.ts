import { mkdir, readdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import type { VerifiedFile } from "./commands/verify.ts";
import { paths } from "./config.ts";
import type { ItunesTrack } from "./itunes.ts";
import { baseTitle } from "./match.ts";
import { SOURCES, type Source, nameKeys } from "./popularity.ts";
import { songIdOf } from "./search-index.ts";

/** A verified answer-pool song, as the popularity lookups see it. */
export interface PoolSong {
  songId: string;
  /** Without iTunes-style suffixes. */
  title: string;
  film: string;
  year: number;
  composer: string;
  singers: string[];
  /** Name keys of everyone credited: singers, composer and the iTunes artist line. */
  people: string[];
  /** The chat's 1–5 guess at how well known the song is. */
  recognisability: number;
  track: ItunesTrack;
}

/** One entry per verified iTunes track; a track suggested twice keeps the higher recognisability. */
export async function loadPool(): Promise<PoolSong[]> {
  const byTrack = new Map<number, PoolSong>();
  for (const source of await readdir(paths.verified).catch(() => [] as string[])) {
    for (const file of (await readdir(path.join(paths.verified, source))).filter((f) => f.endsWith(".json"))) {
      const verified = JSON.parse(await readFile(path.join(paths.verified, source, file), "utf8")) as VerifiedFile;
      for (const { candidate, result } of verified.rows) {
        if (result.status !== "found" || !result.track) continue;
        const seen = byTrack.get(result.track.trackId);
        if (seen && candidate.recognisability <= seen.recognisability) continue;
        byTrack.set(result.track.trackId, {
          songId: songIdOf(result.track.trackId),
          title: baseTitle(candidate.title),
          film: candidate.film,
          year: candidate.year,
          composer: candidate.composer,
          singers: candidate.singers,
          people: nameKeys([...candidate.singers, candidate.composer, result.track.artistName]),
          recognisability: candidate.recognisability,
          track: result.track,
        });
      }
    }
  }
  return [...byTrack.values()];
}

/** Each song's raw value from one source: views or listeners. */
export type Signal = Record<string, number>;

export async function writeSignal(source: Source, values: Signal): Promise<void> {
  await mkdir(paths.signals, { recursive: true });
  await writeAtomically(path.join(paths.signals, `${source}.json`), JSON.stringify(values, null, 1));
}

/** Every source fetched so far; a source not fetched yet is missing from the map. */
export async function readSignals(): Promise<Map<Source, Signal>> {
  const signals = new Map<Source, Signal>();
  for (const source of SOURCES) {
    const body = await readFile(path.join(paths.signals, `${source}.json`), "utf8").catch(() => null);
    if (body !== null) signals.set(source, JSON.parse(body) as Signal);
  }
  return signals;
}

/**
 * Writes beside [target] and renames over it, so a stopped run never leaves half a file. Windows
 * briefly locks a file just written (antivirus, the search indexer), so the rename is retried.
 */
export async function writeAtomically(target: string, body: string): Promise<void> {
  const temp = `${target}.tmp`;
  await writeFile(temp, body);
  for (let attempt = 1; ; attempt++) {
    try {
      await rename(temp, target);
      return;
    } catch (error) {
      const code = (error as NodeJS.ErrnoException).code;
      if ((code !== "EPERM" && code !== "EBUSY" && code !== "EACCES") || attempt >= 10) throw error;
      await sleep(50 * attempt);
    }
  }
}

/**
 * Runs [task] over [items] with [concurrency] in flight, printing progress as "label n/total".
 * Returns how many failed; a failure is logged and leaves that item out.
 */
export async function forEachConcurrently<T>(
  label: string,
  items: T[],
  concurrency: number,
  task: (item: T) => Promise<void>,
  describe: (item: T) => string,
): Promise<number> {
  let next = 0;
  let done = 0;
  let failed = 0;
  const worker = async () => {
    for (let i = next++; i < items.length; i = next++) {
      try {
        await task(items[i]!);
      } catch (error) {
        failed++;
        console.log(`\n  skipped ${describe(items[i]!)}: ${(error as Error).message}`);
      }
      process.stdout.write(`\r${label} ${++done}/${items.length}   `);
    }
  };
  await Promise.all(Array.from({ length: concurrency }, worker));
  console.log();
  return failed;
}
