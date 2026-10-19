import { LABEL_CHANNELS, paths } from "../config.ts";
import { joined, normalize } from "../normalize.ts";
import { type PoolSong, type Signal, loadPool, writeSignal } from "../pool.ts";
import { uploadMatches } from "../popularity.ts";
import { QuotaExceededError, type Upload, YoutubeClient } from "../youtube.ts";

/** Playlists of one channel read side by side. */
const PLAYLIST_CONCURRENCY = 8;

/**
 * Each pool song's label uploads: every upload whose title has the song and its film. Songs are
 * looked up by the first two letters of their title (spaces aside, as matching ignores them), so
 * each upload is only checked against songs whose title could start at one of its words.
 */
export function matchUploads(songs: PoolSong[], uploads: Upload[]): Map<string, Upload[]> {
  const byStart = Map.groupBy(songs, (s) => joined(normalize(s.title)).slice(0, 2));
  const matches = new Map<string, Upload[]>();
  for (const upload of uploads) {
    const words = normalize(upload.title).split(" ");
    // A one-letter word starts a title together with the next one.
    const candidates = new Set(words.flatMap((w, i) => byStart.get((w + (words[i + 1] ?? "")).slice(0, 2)) ?? []));
    for (const song of candidates) {
      if (!uploadMatches(upload.title, song.title, song.film)) continue;
      const list = matches.get(song.songId) ?? [];
      list.push(upload);
      matches.set(song.songId, list);
    }
  }
  return matches;
}

/**
 * Sums each pool song's views across the label channels' uploads of it (full video, lyric video,
 * audio) and writes data/signals/youtube.json. Listing about 170,000 uploads and reading the
 * matched ones' views costs about 4,000 units, within a day's quota; everything is cached, so a
 * run stopped by the quota carries on the next day.
 */
export async function fetchYoutube(): Promise<void> {
  const apiKey = process.env.YOUTUBE_API_KEY?.trim();
  if (!apiKey) {
    console.log("Set YOUTUBE_API_KEY in tools/curation/.env first (see .env.example).");
    process.exitCode = 1;
    return;
  }
  const pool = await loadPool();
  const youtube = new YoutubeClient(apiKey, paths.youtubeCache);
  try {
    // Each channel's pages come one after another, but channels can be listed side by side.
    let listed = 0;
    process.stdout.write(`Listing uploads of ${LABEL_CHANNELS.length} label channels…`);
    const lists = await Promise.all(
      LABEL_CHANNELS.map(async (channel) => {
        const list = await youtube.uploads(channel.uploads);
        if ("playlists" in channel && channel.playlists) {
          // UU… is the channel's uploads playlist; UC… is the channel itself.
          const playlists = await youtube.playlists(`UC${channel.uploads.slice(2)}`);
          let next = 0;
          const reader = async () => {
            for (let i = next++; i < playlists.length; i = next++) list.push(...(await youtube.uploads(playlists[i]!)));
          };
          await Promise.all(Array.from({ length: PLAYLIST_CONCURRENCY }, reader));
        }
        process.stdout.write(`\rListed ${++listed}/${LABEL_CHANNELS.length} channels (${channel.name}: ${list.length})          `);
        return list;
      }),
    );
    // A video sits in its channel's uploads and in playlists, maybe several; keep one of each.
    const uploads = [...new Map(lists.flat().map((u) => [u.id, u])).values()];
    console.log(`\n${uploads.length} uploads from ${LABEL_CHANNELS.length} label channels.`);

    const matches = matchUploads(pool, uploads);
    const ids = [...matches.values()].flat().map((u) => u.id);
    process.stdout.write(`Reading views for ${new Set(ids).size} matched uploads…`);
    const views = await youtube.viewCounts(ids);
    console.log();

    const values: Signal = {};
    for (const [songId, list] of matches) {
      // The same upload can sit on two channels' lists (a label and its VEVO); count it once.
      const total = [...new Set(list.map((u) => u.id))].reduce((sum, id) => sum + (views.get(id) ?? 0), 0);
      if (total > 0) values[songId] = total;
    }
    await writeSignal("youtube", values);
    console.log(`YouTube has views for ${Object.keys(values).length} of ${pool.length} songs. Wrote data/signals/youtube.json.`);
    printCoverage(pool, values);
  } catch (error) {
    if (!(error instanceof QuotaExceededError)) throw error;
    console.log("\nToday's YouTube quota is used up. Everything fetched so far is cached; run youtube again tomorrow.");
    process.exitCode = 1;
  }
}

/** Share of each year's songs a source has, to spot years it covers badly. */
export function printCoverage(pool: PoolSong[], values: Signal): void {
  const byYear = Map.groupBy(pool, (s) => s.year);
  const line = [...byYear.entries()]
    .sort((a, b) => a[0] - b[0])
    .map(([year, songs]) => `${year} ${Math.round((100 * songs.filter((s) => values[s.songId]).length) / songs.length)}%`);
  console.log(`Coverage by year: ${line.join(", ")}`);
}
