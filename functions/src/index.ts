import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { setGlobalOptions } from "firebase-functions/v2";
import { type CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { closeDay as closeDayImpl } from "./close.ts";
import { itunesPreview } from "./previews.ts";
import { withinRateLimit } from "./rate-limit.ts";
import { NoShowError, getShow as getShowImpl } from "./show.ts";
import { GameErrorException, submitTry as submitTryImpl } from "./tries.ts";
import { parseSubmitTry } from "./validate.ts";

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

/** The caller's Firebase UID; every call needs the app's anonymous sign-in. */
function uidOf(request: CallableRequest): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign-in required");
  return request.auth.uid;
}

/**
 * Game errors go to the app as `failed-precondition` with the reason in `details.reason`, which
 * it maps to its own error type. Anything else is logged and sent as a plain internal error.
 */
function toHttpsError(error: unknown): HttpsError {
  if (error instanceof HttpsError) return error;
  if (error instanceof GameErrorException) return new HttpsError("failed-precondition", error.reason, { reason: error.reason });
  if (error instanceof NoShowError) {
    logger.error(error.message);
    return new HttpsError("unavailable", "No show today", { reason: "NO_SHOW" });
  }
  logger.error("Unexpected error", { error: String(error), stack: (error as Error)?.stack });
  return new HttpsError("internal", "Something went wrong");
}

/** Today's show for the caller (AGENTS.md §7). */
export const getShow = onCall(async (request) => {
  const uid = uidOf(request);
  try {
    return await getShowImpl(getFirestore(), uid, new Date(), itunesPreview);
  } catch (error) {
    throw toHttpsError(error);
  }
});

/** One guess or skip (AGENTS.md §7). */
export const submitTry = onCall(async (request) => {
  const uid = uidOf(request);
  const payload = parseSubmitTry(request.data);
  try {
    const now = new Date();
    if (!(await withinRateLimit(getFirestore(), uid, now))) {
      throw new HttpsError("resource-exhausted", "Too many tries; wait a minute");
    }
    return await submitTryImpl(getFirestore(), uid, payload, now, itunesPreview);
  } catch (error) {
    throw toHttpsError(error);
  }
});

/** Five past midnight IST: yesterday's open rounds become UNFINISHED. */
export const closeDay = onSchedule({ schedule: "5 0 * * *", timeZone: "Asia/Kolkata" }, async () => {
  await closeDayImpl(getFirestore(), new Date());
});
