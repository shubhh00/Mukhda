import os from "node:os";
import path from "node:path";

export const ROOT = path.resolve(import.meta.dirname, "..");

/** Where the browser saves chat downloads. Override with CURATION_DOWNLOADS_DIR in .env. */
export const DOWNLOADS_DIR = process.env.CURATION_DOWNLOADS_DIR?.trim() || path.join(os.homedir(), "Downloads");
export const DATA_DIR = path.join(ROOT, "data");

export const paths = {
  prompts: path.join(DATA_DIR, "prompts"),
  /** data/candidates/<folder>/<year>-<kind>.json — one JSON array of candidates per file. */
  candidates: path.join(DATA_DIR, "candidates"),
  verified: path.join(DATA_DIR, "verified"),
  itunesCache: path.join(DATA_DIR, "cache", "itunes"),
  reports: path.join(DATA_DIR, "reports"),
  /** The built search index (also copied into the app's assets). */
  index: path.join(DATA_DIR, "index"),
  /** Prompts for, and chat answers about, songs the iTunes check couldn't confirm. */
  review: path.join(DATA_DIR, "review"),
};

/** Pilot years: early 2000s, early 2010s, recent. */
export const PILOT_YEARS = [2003, 2013, 2023] as const;

/** "2003,2013" or "2000-2026" or a mix like "2000-2002,2010". Each year gets its own prompts. */
export function parseYears(spec: string): number[] {
  const years = spec.split(",").flatMap((part) => {
    const [from, to] = part.trim().split("-").map((y) => Number.parseInt(y, 10));
    if (from === undefined || Number.isNaN(from)) throw new Error(`bad year "${part}"`);
    if (to === undefined) return [from];
    if (Number.isNaN(to) || to < from) throw new Error(`bad year range "${part}"`);
    return Array.from({ length: to - from + 1 }, (_, i) => from + i);
  });
  return [...new Set(years)];
}

export const ANGLES = {
  chart_hits:
    "the year's biggest hits, songs that ruled charts, radio and wedding playlists",
  lesser_known_films:
    "good songs from films that didn't do well at the box office",
  composer_deep_cuts:
    "album tracks from well-known composers' soundtracks that weren't the lead single",
  smaller_star_films: "songs from films without top-tier stars",
} as const;

export type Angle = keyof typeof ANGLES;
export const ANGLE_KEYS = Object.keys(ANGLES) as Angle[];

/** One prompt per year covering every angle, or one prompt per angle (as in the pilot). */
export const ALL = "all";
export type PromptKind = Angle | typeof ALL;
export const PROMPT_KINDS: readonly PromptKind[] = [ALL, ...ANGLE_KEYS];

/** Folder that downloaded chat answers are filed into: data/candidates/chat/. */
export const CHAT_FOLDER = "chat";

/** Prefix of every file the chat is asked to produce, so they're easy to spot in Downloads. */
export const DOWNLOAD_PREFIX = "mukhda";

/** File name the chat is asked to give its answer, e.g. `mukhda-2004-all.json`. */
export function downloadFileName(year: number, kind: PromptKind): string {
  return `${DOWNLOAD_PREFIX}-${candidateFileName(year, kind)}`;
}

export function candidateFileName(year: number, kind: PromptKind): string {
  return `${year}-${kind}.json`;
}
