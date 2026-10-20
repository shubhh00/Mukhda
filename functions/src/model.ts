/** Firestore documents and callable payloads, as AGENTS.md §6.1 and §7 define them. */

import type { Timestamp } from "firebase-admin/firestore";

export const TIERS = ["EASY", "MEDIUM", "HARD", "EXPERT", "LEGEND"] as const;
export type Tier = (typeof TIERS)[number];

export type TryType = "GUESS" | "SKIP";
export type TryResult = "CORRECT" | "RIGHT_FILM" | "WRONG" | "SKIPPED";
export type RoundStatus = "OPEN" | "WON" | "LOST" | "UNFINISHED";

/** Tries per tier per day; the fifth non-correct try loses the round. */
export const MAX_TRIES = 5;
/** Clip steps 0..4: 0.5 s, 1 s, 2 s, 4 s, full preview. */
export const LAST_STEP = 4;

/** songs/{songId}: an answer-pool song. */
export interface SongDoc {
  songId: string;
  trackId: number;
  title: string;
  filmId: string;
  filmTitle: string;
  year: number;
  composer: string;
  previewUrl: string;
  /** When [previewUrl] was last confirmed with iTunes. */
  previewUrlCachedAt: Timestamp;
  startOffsetMs: number;
  artworkUrl: string | null;
  appleMusicUrl: string | null;
  tier: Tier;
  tierSource: "SEED" | "DATA" | "MANUAL";
  status: "ACTIVE" | "RETIRED";
}

/** catalog/{songId}: every song in the app's search index, answer or not, for judging guesses. */
export interface CatalogDoc {
  filmId: string;
}

export interface ScheduledTier {
  songId: string;
  backupSongId: string;
  /** Set the first time the tier is served: the backup when the main song's preview was gone. */
  activeSongId?: string;
}

/** schedule/{yyyy-mm-dd}, the IST date. */
export interface ScheduleDoc {
  showNo: number;
  tiers: Record<Tier, ScheduledTier>;
}

export interface TryRecord {
  requestId: string;
  type: TryType;
  songId: string | null;
  result: TryResult;
  at: Timestamp;
}

/** rounds/{uid}_{date}_{tier}. */
export interface RoundDoc {
  uid: string;
  date: string;
  tier: Tier;
  songId: string;
  tries: TryRecord[];
  status: RoundStatus;
  solvedAtStep: number | null;
  startedAt: Timestamp;
  endedAt: Timestamp | null;
}

/** songStats/{date}_{tier}: how the day's players did, for "Top X%" and re-tiering. */
export interface SongStatsDoc {
  /** Rounds that ended, won or lost. */
  played: number;
  won: number;
  lost: number;
  /** Wins by the clip step they came at, 0..4. */
  byStep: number[];
  /** Rounds still open at midnight; set by closeDay. */
  unfinished?: number;
}

/** What a finished round reveals. Never sent for an open round. */
export interface Reveal {
  songId: string;
  title: string;
  filmTitle: string;
  year: number;
  composer: string;
  artworkUrl: string | null;
  appleMusicUrl: string | null;
  /** "Top X% today": the share of the day's finished players who did at least as well. */
  topPercent: number | null;
}

export interface TierState {
  previewUrl: string;
  startOffsetMs: number;
  status: RoundStatus;
  triesUsed: number;
  currentStep: number;
  tries: { requestId: string; type: TryType; songId: string | null; result: TryResult }[];
  reveal: Reveal | null;
}

export interface ShowResponse {
  date: string;
  showNo: number;
  /** The show's last moment: the next midnight IST, as an ISO instant. */
  endsAt: string;
  tiers: Partial<Record<Tier, TierState>>;
}

export interface SubmitTryRequest {
  date: string;
  tier: Tier;
  requestId: string;
  type: TryType;
  songId?: string;
}

export interface SubmitTryResponse {
  result: TryResult;
  triesUsed: number;
  currentStep: number;
  status: RoundStatus;
  reveal: Reveal | null;
}

/** Game errors the app acts on (AGENTS.md §8); sent as the HttpsError's details.reason. */
export type GameError = "ROUND_CLOSED" | "TRIES_EXHAUSTED" | "ALREADY_FINISHED" | "INVALID_SONG";
