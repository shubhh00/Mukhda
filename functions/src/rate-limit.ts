import type { Firestore } from "firebase-admin/firestore";

/** Calls a player may make per minute, per kind; a person playing never comes close. */
export const LIMITS = { try: 30, show: 60 } as const;
export type CallKind = keyof typeof LIMITS;

/** rateLimits/{uid}_{kind}: the player's calls in the current minute. */
export interface RateCount {
  minute: number;
  count: number;
}

/** The player made more calls in a minute than [LIMITS] allows. */
export class RateLimitedError extends Error {
  constructor() {
    super("Too many requests; wait a minute");
  }
}

/** The count after one more call at [now], or null when that call is over [limit]. */
export function countCall(current: RateCount | undefined, now: Date, limit: number): RateCount | null {
  const minute = Math.floor(now.getTime() / 60_000);
  const count = current?.minute === minute ? current.count + 1 : 1;
  return count <= limit ? { minute, count } : null;
}

/**
 * Counts this call and throws [RateLimitedError] when it's over the limit. Runs first, before
 * any other read, and commits on its own, so a call the game then refuses (a round already
 * over, an unknown song) still counts: refused calls can't be repeated without limit.
 */
export async function enforceRateLimit(db: Firestore, uid: string, kind: CallKind, now: Date): Promise<void> {
  const ref = db.doc(`rateLimits/${uid}_${kind}`);
  const allowed = await db.runTransaction(async (tx) => {
    const counted = countCall((await tx.get(ref)).data() as RateCount | undefined, now, LIMITS[kind]);
    if (counted) tx.set(ref, counted);
    return counted !== null;
  });
  if (!allowed) throw new RateLimitedError();
}
