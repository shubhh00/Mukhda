import { normalize } from "./normalize.ts";
import { TIERS, type Tier } from "./popularity.ts";

export interface SchedulableSong {
  songId: string;
  filmId: string;
  composer: string;
  tier: Tier;
  /** Release year: Easy, Medium and Hard draw recent songs more often. */
  year: number;
}

export interface ScheduledDay {
  date: string;
  showNo: number;
  tiers: Record<Tier, { songId: string; backupSongId: string }>;
}

/** A song isn't the answer again within a year. */
export const SONG_GAP_DAYS = 365;
/** A film is in the show at most once a week, across all tiers. */
export const FILM_GAP_DAYS = 7;

const DAY_MS = 24 * 60 * 60 * 1000;
const dayNumber = (date: string) => Date.parse(`${date}T00:00:00Z`) / DAY_MS;
export const addDays = (date: string, days: number) => new Date((dayNumber(date) + days) * DAY_MS).toISOString().slice(0, 10);

/** Show No. 1 is the launch date (AGENTS.md §3.7). */
export const showNoOf = (date: string, launchDate: string) => dayNumber(date) - dayNumber(launchDate) + 1;

/** Tiers whose daily song leans to recent releases; Expert and Legend draw from every year alike. */
export const RECENT_LEANING_TIERS: readonly Tier[] = ["EASY", "MEDIUM", "HARD"];
/** In those tiers, a song this many years older is drawn half as often... */
export const HALF_LIFE_YEARS = 5;
/** ...but never less than this often, so the classics still come round. */
export const MIN_WEIGHT = 0.08;

/** How often [song] is drawn, relative to this year's songs, in a show in [year]. */
export function drawWeight(song: SchedulableSong, year: number): number {
  if (!RECENT_LEANING_TIERS.includes(song.tier)) return 1;
  return Math.max(MIN_WEIGHT, 0.5 ** (Math.max(0, year - song.year) / HALF_LIFE_YEARS));
}

/**
 * [items] in a random order where heavier items tend to come first: weighted sampling without
 * replacement (each item's key is u^(1/weight)). Deterministic for a seed (mulberry32).
 */
function weightedOrder<T>(items: T[], weight: (item: T) => number, seed: number): T[] {
  let state = seed >>> 0;
  const random = () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return items
    .map((item) => ({ item, key: Math.log(random() || Number.MIN_VALUE) / weight(item) }))
    .sort((a, b) => b.key - a.key)
    .map((entry) => entry.item);
}

const composerKey = (composer: string) => normalize(composer).replaceAll(" ", "");

/**
 * Schedules [days] shows from [from], continuing after [history] (already scheduled days, which
 * are never changed). Each tier gets a song from its own tier and a backup, drawn at random with
 * Easy, Medium and Hard leaning to recent songs ([drawWeight]). Hard rules: no song
 * within [SONG_GAP_DAYS] of its last day, no film twice within [FILM_GAP_DAYS] (nor twice in a
 * day, backups included). Composers are spread: a day avoids repeating one when it can.
 * Throws when a tier runs out of songs that fit.
 */
export function schedule(
  songs: SchedulableSong[],
  history: ScheduledDay[],
  from: string,
  days: number,
  launchDate: string,
  seed: number,
): ScheduledDay[] {
  const lastSong = new Map<string, number>();
  const lastFilm = new Map<string, number>();
  const filmOf = new Map(songs.map((s) => [s.songId, s.filmId]));
  const remember = (day: ScheduledDay) => {
    const n = dayNumber(day.date);
    for (const tier of TIERS) {
      const { songId } = day.tiers[tier];
      lastSong.set(songId, Math.max(lastSong.get(songId) ?? -Infinity, n));
      const film = filmOf.get(songId);
      if (film) lastFilm.set(film, Math.max(lastFilm.get(film) ?? -Infinity, n));
    }
  };
  history.forEach(remember);

  const byTier = Map.groupBy(songs, (s) => s.tier);
  const out: ScheduledDay[] = [];
  for (let d = 0; d < days; d++) {
    const date = addDays(from, d);
    const n = dayNumber(date);
    const filmsToday = new Set<string>();
    const composersToday = new Set<string>();
    const tiers = {} as ScheduledDay["tiers"];
    for (const [t, tier] of TIERS.entries()) {
      const fits = (s: SchedulableSong) =>
        n - (lastSong.get(s.songId) ?? -Infinity) > SONG_GAP_DAYS &&
        n - (lastFilm.get(s.filmId) ?? -Infinity) >= FILM_GAP_DAYS &&
        !filmsToday.has(s.filmId);
      const year = Number(date.slice(0, 4));
      const candidates = weightedOrder(byTier.get(tier) ?? [], (s) => drawWeight(s, year), seed * 7919 + n * 31 + t).filter(fits);
      // Prefer a composer the day doesn't have yet; fall back to any that fits.
      const fresh = (s: SchedulableSong) => !composersToday.has(composerKey(s.composer));
      const main = candidates.find(fresh) ?? candidates[0];
      const backup = candidates.find((s) => s !== main && s.filmId !== main?.filmId && fresh(s)) ??
        candidates.find((s) => s !== main && s.filmId !== main?.filmId);
      if (!main || !backup) throw new Error(`Ran out of ${tier} songs that fit on ${date}`);
      tiers[tier] = { songId: main.songId, backupSongId: backup.songId };
      for (const s of [main, backup]) filmsToday.add(s.filmId);
      composersToday.add(composerKey(main.composer));
    }
    const day = { date, showNo: showNoOf(date, launchDate), tiers };
    remember(day);
    out.push(day);
  }
  return out;
}
