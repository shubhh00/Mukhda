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
  lastfmCache: path.join(DATA_DIR, "cache", "lastfm"),
  youtubeCache: path.join(DATA_DIR, "cache", "youtube"),
  /** Each source's raw popularity per song: data/signals/<source>.json. */
  signals: path.join(DATA_DIR, "signals"),
  /** Each pool song's seeded tier, and the sheet of tiers to review. */
  tiers: path.join(DATA_DIR, "tiers"),
  reports: path.join(DATA_DIR, "reports"),
  /** Where the singing starts in each answer song's preview, from vocals/find_vocal_start.py. */
  vocals: path.join(DATA_DIR, "vocals"),
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

/**
 * Label channels whose uploads count as a song's official plays on YouTube: Hindi film music is
 * almost all released through these. Uploads playlists are the channel id with UC swapped for UU.
 * YouTube lists only a channel's newest 20,000 uploads, so for the biggest (`playlists: true`)
 * their playlists are read too, which reach the older ones.
 */
export const LABEL_CHANNELS = [
  { name: "T-Series", uploads: "UUq-Fj5jknLsUf-MWSy4_brA", playlists: true },
  { name: "Zee Music Company", uploads: "UUFFbwnve3yF62-tVXkTyHqg" },
  { name: "Sony Music India", uploads: "UU56gTxNs4f9xZ7Pa2i5xNzg" },
  { name: "SonyMusicIndiaVEVO", uploads: "UU3MLnJtqc_phABBriLRhtgQ" },
  { name: "Tips Official", uploads: "UUJrDMFOdv1I2k8n9oK_V21w" },
  { name: "Tips Films", uploads: "UUqXO3ktBw0D0sw1z5hZFeDg" },
  { name: "YRF", uploads: "UUbTLwN10NoCU4WDzLf1JMOA" },
  { name: "Saregama Music", uploads: "UU_A7K2dXFsTMAciGmnNxy-Q" },
  { name: "Eros Universe", uploads: "UUX52tYZiEh_mHoFja3Veciw", playlists: true },
  { name: "Speed Records", uploads: "UUOsyDsO5tIt-VZ1iwjdQmew" },
  { name: "Universal Music India", uploads: "UUOQZiBbxmbPo3euu6ECwFUA" },
  { name: "Times Music", uploads: "UUo07fumrTn1w4AxcU4j_uDw" },
  { name: "Venus Movies", uploads: "UUpcsv3Ow-8IhoUGDEfPetsQ" },
  { name: "Shemaroo", uploads: "UUF1JIbMUs6uqoZEY1Haw0GQ" },
  { name: "Ultra Bollywood", uploads: "UUmL1WlDI8UkXDXCXcBQN9CA" },
  { name: "Rajshri", uploads: "UUEKWXRsfUHkan-D_ljU8Asw" },
  { name: "Pen Movies", uploads: "UU3ar28GS6o1p0m_wabfk2zw" },
  { name: "Junglee Music", uploads: "UUa5G5-Pc3rsYqq2A5ivxhUg" },
  { name: "White Hill Music", uploads: "UUxQRUQmsAHA5GMAI4nddx_w" },
] as const;
