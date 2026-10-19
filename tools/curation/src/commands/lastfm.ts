import { paths } from "../config.ts";
import { LastfmClient } from "../lastfm.ts";
import { type Signal, forEachConcurrently, loadPool, writeSignal } from "../pool.ts";
import { listenersFor } from "../popularity.ts";

/** Each lookup takes about a second; the client keeps call starts within Last.fm's rate. */
const CONCURRENCY = 4;

/**
 * Looks up every pool song's Last.fm listeners and writes data/signals/lastfm.json. Songs Last.fm
 * has nothing on are left out. Cached, so a re-run only repeats lookups that failed.
 */
export async function fetchLastfm(): Promise<void> {
  const apiKey = process.env.LASTFM_API_KEY?.trim();
  if (!apiKey) {
    console.log("Set LASTFM_API_KEY in tools/curation/.env first (see .env.example).");
    process.exitCode = 1;
    return;
  }
  const pool = await loadPool();
  const lastfm = new LastfmClient(apiKey, paths.lastfmCache);
  const values: Signal = {};
  const failed = await forEachConcurrently(
    "Last.fm",
    pool,
    CONCURRENCY,
    async (song) => {
      const listeners = listenersFor(song.title, song.people, await lastfm.searchTrack(song.title));
      if (listeners > 0) values[song.songId] = listeners;
    },
    (song) => `"${song.title}"`,
  );
  if (failed > 0) {
    // A failed lookup would look like a song Last.fm doesn't know.
    console.log(`${failed} lookups failed, so data/signals/lastfm.json wasn't updated. Run lastfm again.`);
    process.exitCode = 1;
    return;
  }
  await writeSignal("lastfm", values);
  console.log(`Last.fm has listeners for ${Object.keys(values).length} of ${pool.length} songs. Wrote data/signals/lastfm.json.`);
}
