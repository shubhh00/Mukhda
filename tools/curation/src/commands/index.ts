import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { ROOT, paths } from "../config.ts";
import { ItunesClient } from "../itunes.ts";
import { filmMatches } from "../match.ts";
import {
  type AlbumSource,
  type PoolSong,
  assembleIndex,
  filmIdOf,
  isFullSoundtrack,
  mostCommon,
  songIdOf,
} from "../search-index.ts";
import type { VerifiedFile } from "./verify.ts";

/** Where the app reads bundled indexes from: assets/search_index/v<N>.json. */
const APP_INDEX_DIR = path.resolve(ROOT, "..", "..", "data", "src", "main", "assets", "search_index");

async function loadPool(): Promise<PoolSong[]> {
  const pool: PoolSong[] = [];
  for (const source of await readdir(paths.verified).catch(() => [] as string[])) {
    for (const file of await readdir(path.join(paths.verified, source))) {
      const verified = JSON.parse(await readFile(path.join(paths.verified, source, file), "utf8")) as VerifiedFile;
      for (const { candidate, result } of verified.rows) {
        if (result.status === "found" && result.track) pool.push({ track: result.track, film: candidate.film, year: candidate.year });
      }
    }
  }
  return pool;
}

/**
 * Builds the app's search index from the verified pool: each pool film's full soundtrack from
 * iTunes. Writes data/index/search_index.json and the next assets/search_index/v<N>.json.
 */
export async function writeSearchIndex(): Promise<void> {
  const itunes = new ItunesClient(paths.itunesCache);
  const pool = await loadPool();
  if (pool.length === 0) {
    console.log("Nothing verified yet. Run `npm run verify` first.");
    return;
  }

  // One film name and year per album, from the pool songs that came from it.
  const byAlbum = Map.groupBy(pool.filter((p) => p.track.collectionId), (p) => p.track.collectionId!);
  const albums = new Map<number, { film: string; year: number }>();
  for (const [id, songs] of byAlbum) {
    if (!isFullSoundtrack(songs[0]!.track)) continue;
    albums.set(id, { film: mostCommon(songs.map((s) => s.film))!, year: mostCommon(songs.map((s) => s.year))! });
  }

  // Films whose pool songs are all singles: look for their soundtrack album.
  const coveredFilms = new Set([...albums.values()].map((a) => filmIdOf(a.film, a.year)));
  const singleOnly = new Map<string, { film: string; year: number }>();
  for (const p of pool) {
    const id = filmIdOf(p.film, p.year);
    if (!coveredFilms.has(id)) singleOnly.set(id, { film: p.film, year: p.year });
  }
  console.log(`${pool.length} pool songs, ${albums.size} soundtrack albums, ${singleOnly.size} films found only as singles.`);

  // A failed lookup leaves songs out. Publishing that would make phones swap a full index for a
  // short one, so it's written for inspection but not published; responses are cached, so a
  // re-run only repeats the lookups that failed.
  let failedLookups = 0;
  let i = 0;
  for (const { film, year } of singleOnly.values()) {
    process.stdout.write(`\rlooking for soundtracks ${++i}/${singleOnly.size}   `);
    try {
      const found = (await itunes.searchAlbums(film)).find(
        (a) => isFullSoundtrack(a) && filmMatches(film, { ...a, trackName: "", artistName: "", trackId: 0, trackViewUrl: "" }),
      );
      if (found && !albums.has(found.collectionId)) albums.set(found.collectionId, { film, year });
    } catch (error) {
      failedLookups++;
      console.log(`\n  skipped "${film}": ${(error as Error).message}`);
    }
  }

  const sources: AlbumSource[] = [];
  i = 0;
  for (const [id, { film, year }] of albums) {
    process.stdout.write(`\rreading soundtracks ${++i}/${albums.size}   `);
    try {
      sources.push({ film, year, tracks: await itunes.albumTracks(id) });
    } catch (error) {
      failedLookups++;
      console.log(`\n  skipped album ${id} (${film}): ${(error as Error).message}`);
    }
  }

  const songs = assembleIndex(pool, sources);
  const indexed = new Set(songs.map((s) => s.songId));
  const poolIds = new Set(pool.map((p) => songIdOf(p.track.trackId)));
  const poolInIndex = [...poolIds].filter((id) => indexed.has(id)).length;
  const films = new Set(songs.map((s) => s.filmId)).size;
  console.log(`\nIndex: ${songs.length} songs from ${films} films; ${poolInIndex} of ${poolIds.size} pool tracks keep their own id.`);

  const body = JSON.stringify({ songs }, null, 0);
  await mkdir(path.join(paths.index), { recursive: true });
  await writeFile(path.join(paths.index, "search_index.json"), body);

  if (failedLookups > 0) {
    console.log(`${failedLookups} iTunes lookups failed, so the index wasn't published to the app. Run index again.`);
    return;
  }
  await publishToApp(body);
}

/**
 * Bundles the index as assets/search_index/v<N>.json. Installed apps only re-import a higher N,
 * so N goes up only when the songs change, and only the newest file is kept in the app.
 */
async function publishToApp(body: string): Promise<void> {
  await mkdir(APP_INDEX_DIR, { recursive: true });
  const versions = (await readdir(APP_INDEX_DIR))
    .map((f) => Number(/^v(\d+)\.json$/.exec(f)?.[1]))
    .filter((v) => Number.isInteger(v))
    .sort((a, b) => a - b);
  const latest = versions.at(-1);
  if (latest !== undefined && (await readFile(path.join(APP_INDEX_DIR, `v${latest}.json`), "utf8")) === body) {
    console.log(`Songs unchanged; the app keeps assets/search_index/v${latest}.json.`);
    return;
  }
  const next = (latest ?? 0) + 1;
  await writeFile(path.join(APP_INDEX_DIR, `v${next}.json`), body);
  for (const old of versions) await rm(path.join(APP_INDEX_DIR, `v${old}.json`));
  console.log(`Wrote data/index/search_index.json and the app's assets/search_index/v${next}.json.`);
}
