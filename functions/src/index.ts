import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import { setGlobalOptions } from "firebase-functions/v2";
import { type CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { BILLING_TOPIC, type BudgetNotification, overBudget, stopBilling } from "./billing.ts";
import { closeDay as closeDayImpl } from "./close.ts";
import { itunesPreview } from "./previews.ts";
import { RateLimitedError } from "./rate-limit.ts";
import { NoShowError, getShow as getShowImpl } from "./show.ts";
import { GameErrorException, submitTry as submitTryImpl } from "./tries.ts";
import { parseSubmitTry } from "./validate.ts";

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 10 });

/**
 * App Check: when on, only the genuine app may call the game (Play Integrity on release builds, a
 * registered debug token on development ones). Off until the app is on Play, where Play Integrity
 * can first vouch for it; switch it on with the console steps at publish. The local emulator
 * can't check tokens, so it's never enforced there.
 */
const APP_CHECK_ENFORCED = false;
const gameCall = { enforceAppCheck: APP_CHECK_ENFORCED && process.env.FUNCTIONS_EMULATOR !== "true" };

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
  if (error instanceof RateLimitedError) return new HttpsError("resource-exhausted", error.message);
  if (error instanceof GameErrorException) return new HttpsError("failed-precondition", error.reason, { reason: error.reason });
  if (error instanceof NoShowError) {
    logger.error(error.message);
    return new HttpsError("unavailable", "No show today", { reason: "NO_SHOW" });
  }
  logger.error("Unexpected error", { error: String(error), stack: (error as Error)?.stack });
  return new HttpsError("internal", "Something went wrong");
}

/** Today's show for the caller (AGENTS.md §7). */
export const getShow = onCall(gameCall, async (request) => {
  const uid = uidOf(request);
  try {
    return await getShowImpl(getFirestore(), uid, new Date(), itunesPreview);
  } catch (error) {
    throw toHttpsError(error);
  }
});

/** One guess or skip (AGENTS.md §7). */
export const submitTry = onCall(gameCall, async (request) => {
  const uid = uidOf(request);
  const payload = parseSubmitTry(request.data);
  try {
    return await submitTryImpl(getFirestore(), uid, payload, new Date(), itunesPreview);
  } catch (error) {
    throw toHttpsError(error);
  }
});

/** Five past midnight IST: yesterday's open rounds become UNFINISHED. */
export const closeDay = onSchedule({ schedule: "5 0 * * *", timeZone: "Asia/Kolkata" }, async () => {
  await closeDayImpl(getFirestore(), new Date());
});

/**
 * The cost cap: the project's budget posts to [BILLING_TOPIC], and once the month's spending
 * reaches the budget, billing is switched off, which stops every paid service.
 */
export const billingCap = onMessagePublished(BILLING_TOPIC, async (event) => {
  const notice = event.data.message.json as BudgetNotification;
  if (!overBudget(notice)) return;
  const projectId = process.env.GCLOUD_PROJECT ?? process.env.GOOGLE_CLOUD_PROJECT;
  if (!projectId) throw new Error("No project id in the environment");
  await stopBilling(projectId);
});
