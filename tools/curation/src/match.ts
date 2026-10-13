import type { Candidate } from "./candidates.ts";
import type { ItunesTrack } from "./itunes.ts";
import { editDistance, joined, normalize } from "./normalize.ts";

export type Status = "found" | "check" | "missing";

export type Reason = "no_title_match" | "film_mismatch" | "not_original" | "no_preview" | "year_mismatch";

export interface MatchResult {
  status: Status;
  reasons: Reason[];
  track?: ItunesTrack;
  /** Accepted by a human or chat review after the automatic check flagged it. */
  reviewed?: boolean;
}

/** Markers that a track isn't the original film version. Checked only in the suffix, never the title itself. */
const VERSION_MARKERS =
  /\b(remix|reprise|unplugged|lo ?fi|mashup|club|acoustic|instrumental|karaoke|recreated|revisited|version|slowed|reverb|cover|live|redux)\b/i;

const FROM_FILM = /from\s+["“'‘]([^"”'’]+)["”'’]/i;

const SOUNDTRACK_SUFFIX =
  /\s*[([]?(original motion picture soundtrack|original soundtrack|motion picture soundtrack|ost)[)\]]?\s*$|\s+-\s+(ep|single)\s*$/i;

/** True for remixes, reprises, instrumentals and other non-original versions. */
export function isAlternateVersion(trackName: string): boolean {
  return VERSION_MARKERS.test(splitTrackName(trackName).suffix);
}

/** "Kesariya (From \"Brahmastra\")" -> base "Kesariya", suffix "(From \"Brahmastra\")". */
export function splitTrackName(name: string): { base: string; suffix: string } {
  const cut = name.search(/\s[([]|\s[-–—]\s/);
  return cut === -1 ? { base: name, suffix: "" } : { base: name.slice(0, cut), suffix: name.slice(cut) };
}

/** Film a track belongs to: an explicit `From "X"` wins, otherwise the album name minus soundtrack wording. */
export function filmOf(track: ItunesTrack): string {
  const from = FROM_FILM.exec(track.trackName) ?? FROM_FILM.exec(track.collectionName);
  if (from?.[1]) return from[1];
  let album = track.collectionName;
  // Strip repeatedly: "X (Original Motion Picture Soundtrack) - EP".
  for (let previous = ""; previous !== album; ) {
    previous = album;
    album = album.replace(SOUNDTRACK_SUFFIX, "");
  }
  return album;
}

/**
 * The song title without iTunes-style suffixes. Models sometimes copy them ("Jadu Hai Nasha Hai -
 * From \"Jism\""), so candidate titles get the same treatment as track names.
 */
export function baseTitle(title: string): string {
  return splitTrackName(title).base.trim();
}

const digits = (s: string) => s.replace(/\D/g, "");

export function titleMatches(candidateTitle: string, trackName: string): boolean {
  const a = joined(normalize(baseTitle(candidateTitle)));
  const b = joined(normalize(splitTrackName(trackName).base));
  if (a.length === 0 || b.length === 0) return false;
  if (a === b) return true;
  // "Bhool Bhulaiyaa 3 Title Track" is never the "2" one, however close the spelling.
  if (digits(a) !== digits(b)) return false;
  const allowed = Math.max(1, Math.floor(Math.min(a.length, b.length) * 0.15));
  return editDistance(a, b) <= allowed;
}

/** "Mr. & Mrs. Mahi" and "Mr. And Mrs. Mahi" are the same film. */
export const filmKey = (film: string) => joined(normalize(film.replaceAll("&", " and ")));

export function filmMatches(candidateFilm: string, track: ItunesTrack): boolean {
  const a = filmKey(candidateFilm);
  const b = filmKey(filmOf(track));
  if (a.length === 0 || b.length === 0) return false;
  // Very short names ("Sssshhh..." normalises to "sh") must match exactly; containment would be too loose.
  if (a === b) return true;
  if (a.length < 3 || b.length < 3) return false;
  if (a.includes(b) || b.includes(a)) return true;
  return Math.min(a.length, b.length) >= 8 && editDistance(a, b) <= 2;
}

function releaseYear(track: ItunesTrack): number {
  return new Date(track.releaseDate).getUTCFullYear();
}

/** Problems with a track that matches the candidate's title. Empty means a clean match. */
function problems(candidate: Candidate, track: ItunesTrack): Reason[] {
  const reasons: Reason[] = [];
  if (!filmMatches(candidate.film, track)) reasons.push("film_mismatch");
  if (!candidate.is_recreation && isAlternateVersion(track.trackName)) {
    reasons.push("not_original");
  }
  if (!track.previewUrl) reasons.push("no_preview");
  if (yearIsWrong(candidate, track)) reasons.push("year_mismatch");
  return reasons;
}

/** Furthest a placeholder date can sit before the film; no film is remade within this gap. */
const MAX_PLACEHOLDER_GAP = 3;

/**
 * Old soundtracks are often reissued digitally years later, so a later date is fine. A date well
 * before the film usually means a different, older film or song (a remake like Don 1978 vs 2006),
 * except that some labels stamp their whole catalogue with one placeholder date (2007 films
 * listed as 2005-03-02). That's forgiven only when the album is exactly this film and the date
 * is at most a few years early.
 */
function yearIsWrong(candidate: Candidate, track: ItunesTrack): boolean {
  const gap = candidate.year - releaseYear(track);
  if (gap <= 1) return false;
  const exactFilm = filmKey(candidate.film) === filmKey(filmOf(track));
  return !(exactFilm && gap <= MAX_PLACEHOLDER_GAP);
}

/**
 * Picks the iTunes track that best fits the candidate. `found` needs title, film, original
 * version, a preview and a release no earlier than the film; a title match with other problems is `check`.
 */
export function evaluate(candidate: Candidate, tracks: ItunesTrack[]): MatchResult {
  let best: MatchResult | undefined;
  for (const track of tracks) {
    if (!titleMatches(candidate.title, track.trackName)) continue;
    const reasons = problems(candidate, track);
    if (!best || reasons.length < best.reasons.length) {
      best = { status: reasons.length === 0 ? "found" : "check", reasons, track };
      if (reasons.length === 0) break;
    }
  }
  return best ?? { status: "missing", reasons: ["no_title_match"] };
}

const RANK: Record<Status, number> = { found: 0, check: 1, missing: 2 };

export function better(a: MatchResult, b: MatchResult): MatchResult {
  if (RANK[a.status] !== RANK[b.status]) return RANK[a.status] < RANK[b.status] ? a : b;
  return a.reasons.length <= b.reasons.length ? a : b;
}
