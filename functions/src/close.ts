import { FieldValue, type Firestore, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import type { RoundDoc, Tier } from "./model.ts";
import { statsId } from "./show.ts";
import { addDays, istDate } from "./time.ts";

/** Firestore takes at most 500 writes in one batch. */
const BATCH_SIZE = 400;

/**
 * Runs just after midnight IST: rounds of yesterday's show still open become UNFINISHED (not a
 * loss; they still count for the streak), and each tier's stats get its unfinished count.
 * Running it twice is harmless: the second run finds nothing open.
 */
export async function closeDay(db: Firestore, now: Date): Promise<{ date: string; closed: number }> {
  const date = addDays(istDate(now), -1);
  const open = await db.collection("rounds").where("date", "==", date).where("status", "==", "OPEN").get();
  const unfinishedByTier = new Map<Tier, number>();
  for (let i = 0; i < open.docs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    for (const doc of open.docs.slice(i, i + BATCH_SIZE)) {
      const round = doc.data() as RoundDoc;
      batch.update(doc.ref, { status: "UNFINISHED", endedAt: Timestamp.fromDate(now) });
      unfinishedByTier.set(round.tier, (unfinishedByTier.get(round.tier) ?? 0) + 1);
    }
    await batch.commit();
  }
  await Promise.all(
    [...unfinishedByTier].map(([tier, count]) =>
      db.doc(`songStats/${statsId(date, tier)}`).set({ unfinished: FieldValue.increment(count) }, { merge: true }),
    ),
  );
  logger.info("Closed the day", { date, closed: open.size });
  return { date, closed: open.size };
}

