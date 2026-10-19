import type { Firestore } from "firebase-admin/firestore";

/** Tries a player may send per minute; a person playing never comes close. */
export const TRIES_PER_MINUTE = 30;

/**
 * Counts the player's calls in the current minute (rateLimits/{uid}) and says whether this one is
 * within the limit.
 */
export async function withinRateLimit(db: Firestore, uid: string, now: Date): Promise<boolean> {
  const ref = db.doc(`rateLimits/${uid}`);
  const minute = Math.floor(now.getTime() / 60_000);
  return db.runTransaction(async (tx) => {
    const current = (await tx.get(ref)).data() as { minute: number; count: number } | undefined;
    const count = current?.minute === minute ? current.count + 1 : 1;
    tx.set(ref, { minute, count });
    return count <= TRIES_PER_MINUTE;
  });
}
