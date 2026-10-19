import { mkdir } from "node:fs/promises";
import path from "node:path";
import { paths } from "../config.ts";
import { loadPool, readSignals, writeAtomically } from "../pool.ts";
import { type Flag, type Source, type Tier, SOURCES, TIERS, WEIGHTS, assignTiers } from "../popularity.ts";

export interface TieredSong {
  songId: string;
  title: string;
  film: string;
  year: number;
  composer: string;
  /** Raw value per source that has the song. */
  values: Partial<Record<Source, number>>;
  recognisability: number;
  score: number;
  tier: Tier;
  flags: Flag[];
}

/**
 * Seeds every pool song's tier from the sources fetched so far (`npm run youtube`, `lastfm`),
 * blended by weight within each release year. Makes no API calls. Writes
 * data/tiers/tiers.json and data/tiers/review.csv.
 */
export async function writeTiers(): Promise<void> {
  const pool = await loadPool();
  if (pool.length === 0) {
    console.log("Nothing verified yet. Run `npm run verify` first.");
    return;
  }
  const signals = await readSignals();
  if (signals.size === 0) {
    console.log("No popularity data yet. Run `npm run youtube` and `npm run lastfm` first.");
    return;
  }
  const missing = SOURCES.filter((s) => !signals.has(s));
  if (missing.length > 0) console.log(`Not fetched yet: ${missing.join(", ")}. Tiering from the rest.`);

  const values = pool.map((song) =>
    Object.fromEntries([...signals].flatMap(([source, signal]) => (signal[song.songId] ? [[source, signal[song.songId]]] : []))),
  ) as Partial<Record<Source, number>>[];
  const results = assignTiers(pool.map((song, i) => ({ year: song.year, values: values[i]!, recognisability: song.recognisability })));
  const songs: TieredSong[] = pool
    .map((song, i) => ({
      songId: song.songId,
      title: song.title,
      film: song.film,
      year: song.year,
      composer: song.composer,
      values: values[i]!,
      recognisability: song.recognisability,
      score: results[i]!.score,
      tier: results[i]!.tier,
      flags: results[i]!.flags,
    }))
    .sort((a, b) => a.year - b.year || b.score - a.score);

  await mkdir(paths.tiers, { recursive: true });
  await writeAtomically(path.join(paths.tiers, "tiers.json"), JSON.stringify(songs, null, 2));
  await writeAtomically(path.join(paths.tiers, "review.csv"), reviewSheet(songs));

  const weights = [...signals.keys()].map((s) => `${s} ${Math.round(WEIGHTS[s] * 100)}%`).join(", ");
  console.log(`Tiered ${songs.length} songs from ${weights}.`);
  for (const source of signals.keys()) {
    const has = songs.filter((s) => s.values[source]).length;
    console.log(`  ${source}: ${has} songs (${Math.round((100 * has) / songs.length)}%)`);
  }
  const flagged = (flag: Flag) => songs.filter((s) => s.flags.includes(flag)).length;
  console.log(
    `To review: ${flagged("no_data")} with no data, ${flagged("one_source")} from one source only, ` +
      `${flagged("sources_disagree")} where sources disagree, ${flagged("chat_disagrees")} where the data and the chat disagree.`,
  );
  console.log(`Per tier: ${TIERS.map((t) => `${t} ${songs.filter((s) => s.tier === t).length}`).join(", ")}.`);
  console.log("Wrote data/tiers/tiers.json and data/tiers/review.csv (import the CSV into the curation sheet).");
}

/** Every song, flagged ones first, as CSV for the curation sheet; `new_tier` is for the reviewer. */
export function reviewSheet(songs: TieredSong[]): string {
  const header = ["song_id", "title", "film", "year", "composer", "tier", "score", "youtube_views", "lastfm_listeners", "recognisability", "flags", "new_tier"];
  const ordered = [...songs].sort((a, b) => Number(b.flags.length > 0) - Number(a.flags.length > 0));
  const rows = ordered.map((s) => [
    s.songId,
    s.title,
    s.film,
    s.year,
    s.composer,
    s.tier,
    s.score.toFixed(2),
    s.values.youtube ?? "",
    s.values.lastfm ?? "",
    s.recognisability,
    s.flags.join(" "),
    "",
  ]);
  return [header, ...rows].map((r) => r.map(csvCell).join(",")).join("\n") + "\n";
}

function csvCell(value: string | number): string {
  const s = String(value);
  return /[",\n]/.test(s) ? `"${s.replaceAll('"', '""')}"` : s;
}
