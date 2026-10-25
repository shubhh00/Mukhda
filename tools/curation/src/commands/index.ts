import { mkdir, readdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { ROOT, paths } from "../config.ts";
import { ItunesClient } from "../itunes.ts";
import { filmMatches } from "../match.ts";
import {
  type AlbumSource,
  type IndexSong,
  type PoolSong,
  assembleIndex,
  filmIdOf,
  isFullSoundtrack,
  mostCommon,
  songIdOf,
} from "../search-index.ts";
import { writeSearchDb } from "../search-db.ts";
import type { VerifiedFile } from "./verify.ts";

/** Where the app reads its bundled index from: assets/search_index/v<N>.db. */
const APP_INDEX_DIR = path.resolve(ROOT, "..", "..", "data", "src", "main", "assets", "search_index");

async function loadPool(): Promise<PoolSong[]> {
  const pool: PoolSong[] = [];
  for (const source of await readdir(paths.verified).catch(() => [] as string[])) {
    for (const file of (await readdir(path.join(paths.verified, source))).filter((f) => f.endsWith(".json"))) {
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

/** The songs last bundled into the app, to tell whether a new index changed anything. */
const BUNDLED = path.join(paths.index, "bundled.json");

/**
 * Bundles the index as assets/search_index/v<N>.db, a ready-built database the app copies on
 * first launch. Installed apps only replace their index for a higher N, so N goes up only when the
 * songs change, and only the newest file is kept in the app.
 */
async function publishToApp(body: string): Promise<void> {
  const latest = await bundledVersion();
  if (latest !== undefined && (await readFile(BUNDLED, "utf8").catch(() => "")) === body) {
    console.log(`Songs unchanged; the app keeps assets/search_index/v${latest}.db.`);
    return;
  }
  await bundle(body, (latest ?? 0) + 1);
}

/** The highest version in the app's assets, .db or the older .json. */
async function bundledVersion(): Promise<number | undefined> {
  await mkdir(APP_INDEX_DIR, { recursive: true });
  const versions = (await readdir(APP_INDEX_DIR)).map((f) => Number(/^v(\d+)\.(db|json)$/.exec(f)?.[1])).filter(Number.isInteger);
  return versions.length ? Math.max(...versions) : undefined;
}

async function bundle(body: string, version: number): Promise<void> {
  const { songs } = JSON.parse(body) as { songs: IndexSong[] };
  // Built beside the working data, then moved in, so a failed build leaves the app's index alone.
  const built = path.join(paths.index, `v${version}.db`);
  await writeSearchDb(built, songs, version);
  // Moved in first (replacing a same-version file), then the others removed: the app's assets
  // always hold an index, even if a step fails.
  const name = `v${version}.db`;
  await rename(built, path.join(APP_INDEX_DIR, name));
  for (const old of await readdir(APP_INDEX_DIR)) if (old !== name) await rm(path.join(APP_INDEX_DIR, old));
  await writeFile(BUNDLED, body);
  console.log(`Wrote the app's assets/search_index/v${version}.db (${songs.length} songs).`);
}

/**
 * Rebuilds the bundled database at the same version, from the songs last bundled: after the
 * app's search schema changes, or to turn an older bundled v<N>.json into v<N>.db.
 */
export async function rebuildSearchDb(): Promise<void> {
  const version = await bundledVersion();
  if (version === undefined) throw new Error("No index bundled in the app yet. Run `npm run index` first.");
  const legacy = path.join(APP_INDEX_DIR, `v${version}.json`);
  const body = await readFile(legacy, "utf8").catch(() => readFile(BUNDLED, "utf8"));
  await bundle(body, version);
}
