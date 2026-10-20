import type { LastfmTrack } from "./lastfm.ts";
import { isAlternateVersion, titleMatches } from "./match.ts";
import { joined, normalize } from "./normalize.ts";

export const TIERS = ["EASY", "MEDIUM", "HARD", "EXPERT", "LEGEND"] as const;
export type Tier = (typeof TIERS)[number];

/** The popularity sources, each fetched by its own command into data/signals/<source>.json. */
export const SOURCES = ["youtube", "lastfm"] as const;
export type Source = (typeof SOURCES)[number];

/**
 * How much each source counts. YouTube is where India hears film songs; Last.fm's audience is
 * small and mostly abroad, so it counts less and mainly fills YouTube's gaps. (Spotify no longer
 * gives new apps its popularity scores.)
 */
export const WEIGHTS: Record<Source, number> = { youtube: 0.75, lastfm: 0.25 };

/** Why a song's tier needs a human look. */
export type Flag = "no_data" | "one_source" | "sources_disagree" | "chat_disagrees";

/** Splits "Vishal-Shekhar, Shreya Ghoshal & KK" into one key per name. */
const NAME_SEPARATORS = /\s*(?:,|&|\/|\+|\bfeat\.?|\bft\.?|\bfeaturing\b|\band\b|\bx\b|\bwith\b)\s*/i;

/** Shortest name part allowed to match inside a longer one ("shankar" in "Shankar-Ehsaan-Loy"). */
const MIN_PARTIAL = 5;

export function nameKeys(names: string[]): string[] {
  return [
    ...new Set(
      names
        .flatMap((n) => n.split(NAME_SEPARATORS))
        .map((n) => joined(normalize(n)))
        .filter((k) => k.length >= 3),
    ),
  ];
}

/** True when any of a Last.fm artist's names is one of the song's people. */
export function artistMatches(lastfmArtist: string, people: string[]): boolean {
  return nameKeys([lastfmArtist]).some((a) =>
    people.some((p) => a === p || (Math.min(a.length, p.length) >= MIN_PARTIAL && (a.includes(p) || p.includes(a)))),
  );
}

/**
 * The song's Last.fm listeners: its plays are split across spellings of the singer and composer
 * ("Shankar-Ehsaan-Loy", "Shankar Ehsaan Loy", "Alisha Chinai"), so every entry with the title and
 * one of the song's people counts. Remixes and other versions don't.
 */
export function listenersFor(title: string, people: string[], results: LastfmTrack[]): number {
  return results
    .filter((t) => titleMatches(title, t.name) && !isAlternateVersion(t.name) && artistMatches(t.artist, people))
    .reduce((sum, t) => sum + t.listeners, 0);
}

/**
 * Each value's percentile among [values], 0 for the lowest and 1 for the highest; ties share the
 * average of their ranks. A single value sits at 0.5.
 */
export function percentiles(values: number[]): number[] {
  if (values.length === 1) return [0.5];
  const order = values.map((v, i) => [v, i] as const).sort((a, b) => a[0] - b[0]);
  const result = new Array<number>(values.length);
  for (let start = 0; start < order.length; ) {
    let end = start;
    while (end + 1 < order.length && order[end + 1]![0] === order[start]![0]) end++;
    const rank = (start + end) / 2 / (values.length - 1);
    for (let k = start; k <= end; k++) result[order[k]![1]] = rank;
    start = end + 1;
  }
  return result;
}

export interface TierInput {
  year: number;
  /** Raw value per source that has the song: views or listeners. */
  values: Partial<Record<Source, number>>;
  /** The chat's 1–5 guess at how well known the song is. */
  recognisability: number;
}

export interface TierResult {
  /** Percentile within the song's year, per source that has it. */
  percentiles: Partial<Record<Source, number>>;
  recognisabilityPct: number;
  /** Final popularity within the year, 1 = best known. */
  score: number;
  tier: Tier;
  flags: Flag[];
}

/** Percentile spread between sources that gets a song flagged for review. */
export const SOURCES_DISAGREE = 0.5;
/** Gap between the data and the chat's guess that gets a song flagged. */
export const CHAT_DISAGREES = 0.6;

/**
 * Share of a year's songs a source must have before a song it lacks counts as unpopular there
 * rather than unknown. In years the label channels have nearly every song, one that isn't there
 * is probably obscure. Elsewhere a gap means little: YouTube lists only a channel's newest 20,000
 * uploads, which cuts T-Series off around 2012, so hits like Dabangg 2's are missing. Last.fm's
 * gaps never count: its audience is too small.
 */
export const ABSENCE_COUNTS_FROM: Partial<Record<Source, number>> = { youtube: 0.95 };

/**
 * Tiers each song by how well known it is, leaning to recent songs. Each source is ranked within
 * the song's release year on its own, then the ranks are blended by [WEIGHTS]. A source without the
 * song gives its weight to the others, unless it has nearly all of that year's songs
 * ([ABSENCE_COUNTS_FROM]): then the song ranks at the bottom for it. Only a song no source has
 * falls back to the chat's guess. The year's ranks are then lowered with age ([agePenalty]) and
 * all songs split into equal fifths, best known to Easy.
 */
export function assignTiers(songs: TierInput[], currentYear = new Date().getUTCFullYear()): TierResult[] {
  const results = new Array<TierResult>(songs.length);
  /** Each song's rank within its own year, before the age adjustment. */
  const yearRanks = new Array<number>(songs.length);
  const byYear = Map.groupBy(songs.map((s, i) => ({ ...s, i })), (s) => s.year);
  for (const group of byYear.values()) {
    const recognised = percentiles(group.map((s) => s.recognisability));
    // Percentiles don't care about scale, so views and listeners rank alike.
    const ranked = new Map<number, Partial<Record<Source, number>>>(group.map((s) => [s.i, {}]));
    for (const source of SOURCES) {
      const coverage = group.filter((s) => (s.values[source] ?? 0) > 0).length / group.length;
      const absenceCounts = coverage >= (ABSENCE_COUNTS_FROM[source] ?? Infinity);
      // Songs the source lacks join the ranking at zero (tied at the bottom) when absence counts.
      const has = group.filter((s) => absenceCounts || (s.values[source] ?? 0) > 0);
      percentiles(has.map((s) => s.values[source] ?? 0)).forEach((pct, k) => {
        ranked.get(has[k]!.i)![source] = pct;
      });
    }
    const scores = group.map((s, k) => {
      const pcts = Object.entries(ranked.get(s.i)!) as [Source, number][];
      if (pcts.length === 0) return recognised[k]!;
      const weight = pcts.reduce((sum, [source]) => sum + WEIGHTS[source], 0);
      return pcts.reduce((sum, [source, pct]) => sum + WEIGHTS[source] * pct, 0) / weight;
    });
    const ranks = percentiles(scores);
    group.forEach((s, k) => {
      const pcts = ranked.get(s.i)!;
      const values = Object.values(pcts);
      // Flags follow the data actually found, not a ranking a missing song was given.
      const found = SOURCES.filter((source) => (s.values[source] ?? 0) > 0).length;
      const flags: Flag[] = [];
      if (found === 0) flags.push("no_data");
      if (values.length > 0) {
        if (found === 1) flags.push("one_source");
        if (Math.max(...values) - Math.min(...values) > SOURCES_DISAGREE) flags.push("sources_disagree");
        if (Math.abs(scores[k]! - recognised[k]!) > CHAT_DISAGREES) flags.push("chat_disagrees");
      }
      yearRanks[s.i] = ranks[k]!;
      results[s.i] = { percentiles: pcts, recognisabilityPct: recognised[k]!, score: 0, tier: "LEGEND", flags };
    });
  }
  // Then across all years: a song's rank in its year, less a little for each year of age, so the
  // easier tiers lean to recent songs. An older song can still be Easy if it's near the top of its year.
  const adjusted = songs.map((s, i) => yearRanks[i]! - agePenalty(s.year, currentYear));
  percentiles(adjusted).forEach((score, i) => {
    results[i] = { ...results[i]!, score, tier: tierOf(score) };
  });
  return results;
}

/**
 * How much a song's within-year rank drops per year of age, up to [MAX_AGE_PENALTY]. At 0.02 a
 * year, a 2005 hit at the top of its year ranks with a song a little above the middle of this
 * year's: the top of 2005 lands in Easy or Medium, its middle in Hard or below.
 */
export const AGE_PENALTY_PER_YEAR = 0.02;
export const MAX_AGE_PENALTY = 0.4;

export function agePenalty(year: number, currentYear: number): number {
  return Math.min(MAX_AGE_PENALTY, Math.max(0, currentYear - year) * AGE_PENALTY_PER_YEAR);
}

/** Top fifth Easy, bottom fifth Legend. */
export function tierOf(score: number): Tier {
  const index = Math.min(TIERS.length - 1, Math.floor((1 - score) * TIERS.length));
  return TIERS[index]!;
}

/** Covers, remixes and edits aren't the song's own plays. */
const NOT_THE_SONG = /\b(cover|remix|reprise|unplugged|lo ?fi|slowed|reverb|8d|karaoke|instrumental|mashup|jhankar|piano|guitar|flute|reaction|tutorial|teaser|promo|making)\b/i;
/** Uploads of a whole soundtrack or a string of songs count for none of them. */
const NOT_ONE_SONG = /\b(jukebox|full album|all songs|back to back|non ?stop|medley|audio songs|video songs|top \d+|best of|hits of)\b/i;

const words = (text: string) => normalize(text).split(" ").filter(Boolean);

/**
 * Where [needle] first appears in [hay] as a run of whole words, spaces aside: "Tumhi Ho Bandhu"
 * has "Tum Hi Ho Bandhu". Returns the run's first word and the word after it, or null.
 */
function findRun(hay: string[], needle: string[]): { start: number; end: number } | null {
  const target = needle.join("");
  if (target.length === 0) return null;
  for (let start = 0; start < hay.length; start++) {
    let joinedRun = "";
    for (let end = start; end < hay.length && joinedRun.length < target.length; end++) {
      joinedRun += hay[end];
      if (joinedRun === target) return { start, end: end + 1 };
      if (!target.startsWith(joinedRun)) break;
    }
  }
  return null;
}

/**
 * True when a label's upload is this song: its title has the song's title and, elsewhere, the
 * film's ("Kajra Re | Bunty Aur Babli"), as whole words. Without the film, every song from "Kal
 * Ho Naa Ho" would count as its title track.
 */
export function uploadMatches(videoTitle: string, title: string, film: string): boolean {
  if (NOT_THE_SONG.test(videoTitle) || NOT_ONE_SONG.test(videoTitle)) return false;
  const video = words(videoTitle);
  const song = words(title);
  const at = findRun(video, song);
  if (!at) return false;
  const rest = [...video.slice(0, at.start), ...video.slice(at.end)];
  if (filmNames(film).some((name) => findRun(rest, name))) return true;
  // A title track names the film once, so "Title Track"/"Title Song" stands in for the second
  // mention. Leading with the title isn't enough: films are often named after older songs ("Tum
  // Se Achcha Kaun Hai", 1969 and 2002), and the older song would be counted.
  return words(film).join("") === song.join("") && rest.includes("title");
}

/**
 * The film's full name and its short one, as words: uploads often drop a subtitle or part
 * number ("Brahmastra Part One: Shiva" is "Brahmāstra"). A short name of one short word is too
 * loose to count.
 */
export function filmNames(film: string): string[][] {
  const short = film
    .split(/\s*[:(]|\s+[-–—]\s+/)[0]!
    .replace(/\s+(part|chapter|vol\.?|volume)\s+(\d+|one|two|three|i{1,3})\s*$/i, "");
  const names = [words(film), words(short)];
  return names.filter((name, i) => name.length > 0 && (i === 0 || name.join("").length >= 6));
}
