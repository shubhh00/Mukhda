import { type Firestore, Timestamp } from "firebase-admin/firestore";
import {
  type CatalogDoc,
  type GameError,
  MAX_TRIES,
  type RoundDoc,
  type SongDoc,
  type SongStatsDoc,
  type SubmitTryRequest,
  type SubmitTryResponse,
  type TryRecord,
} from "./model.ts";
import type { PreviewLookup } from "./previews.ts";
import { currentStep, judge, statsAfterRound, statusAfter } from "./rules.ts";
import { enforceRateLimit } from "./rate-limit.ts";
import { activeSong, revealOf, roundId, statsId } from "./show.ts";
import { istDate } from "./time.ts";

/** A try the rules turn down; the app acts on [reason] (AGENTS.md §8). */
export class GameErrorException extends Error {
  readonly reason: GameError;
  constructor(reason: GameError) {
    super(reason);
    this.reason = reason;
  }
}

/** The guessed song's film, from the answer pool or the wider search catalogue; null if it's neither. */
async function filmOf(db: Firestore, songId: string): Promise<string | null> {
  const [song, catalog] = await db.getAll(db.doc(`songs/${songId}`), db.doc(`catalog/${songId}`));
  if (song?.exists) return (song.data() as SongDoc).filmId;
  if (catalog?.exists) return (catalog.data() as CatalogDoc).filmId;
  return null;
}

/** How long a song read is reused: songs change only when curation republishes them. */
const SONG_CACHE_MS = 10 * 60 * 1000;
const songCache = new Map<string, { song: SongDoc; at: number }>();

/**
 * A round's answer song. A warm instance reads each song once and keeps it a while, which saves
 * a database round trip on nearly every try.
 */
async function answerSong(db: Firestore, songId: string, now: number): Promise<SongDoc> {
  const hit = songCache.get(songId);
  if (hit && now - hit.at < SONG_CACHE_MS) return hit.song;
  const song = (await db.doc(`songs/${songId}`).get()).data() as SongDoc;
  songCache.set(songId, { song, at: now });
  return song;
}

/**
 * Records one guess or skip, in a transaction (AGENTS.md §7): yesterday's show is closed; a
 * request seen before gets its stored result and uses no second try; a finished or full round
 * takes no more; a guess must be a known song. A round's first try fixes the song it's about.
 */
export async function submitTry(db: Firestore, uid: string, request: SubmitTryRequest, now: Date, lookup: PreviewLookup): Promise<SubmitTryResponse> {
  // Counted before anything else is read, and kept even if the try is then refused.
  await enforceRateLimit(db, uid, "try", now);
  if (request.date !== istDate(now)) throw new GameErrorException("ROUND_CLOSED");
  const { date, tier, requestId, type } = request;
  const roundRef = db.doc(`rounds/${roundId(uid, date, tier)}`);
  const statsRef = db.doc(`songStats/${statsId(date, tier)}`);

  // Reads that can't change under the transaction, and the network check for a tier's first
  // song, happen before it, side by side where they can.
  const [existingSnap, guessFilm] = await Promise.all([
    roundRef.get(),
    type === "GUESS" && request.songId ? filmOf(db, request.songId) : Promise.resolve(null),
  ]);
  const existing = existingSnap.data() as RoundDoc | undefined;
  const answer = existing ? await answerSong(db, existing.songId, now.getTime()) : await activeSong(db, date, tier, now, lookup);

  return db.runTransaction(async (tx) => {
    const [roundSnap, statsSnap] = await tx.getAll(roundRef, statsRef);
    const round = roundSnap?.data() as RoundDoc | undefined;
    const stats = statsSnap?.data() as SongStatsDoc | undefined;
    const tries = round?.tries ?? [];

    const seen = tries.find((t) => t.requestId === requestId);
    if (seen && round) {
      const finished = round.status === "WON" || round.status === "LOST";
      return {
        result: seen.result,
        triesUsed: tries.length,
        currentStep: currentStep(tries.length),
        status: round.status,
        reveal: finished ? revealOf(answer, stats, round.solvedAtStep) : null,
      };
    }
    if (round && round.status !== "OPEN") throw new GameErrorException("ALREADY_FINISHED");
    if (tries.length >= MAX_TRIES) throw new GameErrorException("TRIES_EXHAUSTED");
    if (type === "GUESS" && guessFilm === null) throw new GameErrorException("INVALID_SONG");

    const result = judge(type, answer, type === "GUESS" ? { songId: request.songId!, filmId: guessFilm! } : null);
    const record: TryRecord = { requestId, type, songId: type === "GUESS" ? request.songId! : null, result, at: Timestamp.fromDate(now) };
    const nextTries = [...tries, record];
    const { status, solvedAtStep } = statusAfter(nextTries.map((t) => t.result));
    const ended = status !== "OPEN";

    const next: RoundDoc = {
      uid,
      date,
      tier,
      songId: round?.songId ?? answer.songId,
      tries: nextTries,
      status,
      solvedAtStep,
      startedAt: round?.startedAt ?? Timestamp.fromDate(now),
      endedAt: ended ? Timestamp.fromDate(now) : null,
    };
    tx.set(roundRef, next);
    let nextStats = stats;
    if (ended) {
      nextStats = statsAfterRound(stats, solvedAtStep);
      tx.set(statsRef, nextStats);
    }
    return {
      result,
      triesUsed: nextTries.length,
      currentStep: currentStep(nextTries.length),
      status,
      reveal: ended ? revealOf(answer, nextStats, solvedAtStep) : null,
    };
  });
}
