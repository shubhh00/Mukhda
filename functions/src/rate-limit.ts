/** Tries a player may send per minute; a person playing never comes close. */
export const TRIES_PER_MINUTE = 30;

/** rateLimits/{uid}: the player's calls in the current minute. */
export interface RateCount {
  minute: number;
  count: number;
}

/** The player sent more than [TRIES_PER_MINUTE] tries in a minute. */
export class RateLimitedError extends Error {
  constructor() {
    super("Too many tries; wait a minute");
  }
}

/**
 * The count after one more call at [now], or null when that call is over the limit. Checked and
 * written inside the try's own transaction, so it costs no extra round trip.
 */
export function countCall(current: RateCount | undefined, now: Date): RateCount | null {
  const minute = Math.floor(now.getTime() / 60_000);
  const count = current?.minute === minute ? current.count + 1 : 1;
  return count <= TRIES_PER_MINUTE ? { minute, count } : null;
}
