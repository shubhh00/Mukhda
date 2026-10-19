import { HttpsError } from "firebase-functions/v2/https";
import { type SubmitTryRequest, TIERS, type Tier } from "./model.ts";
import { isDate } from "./time.ts";

/** Checks the app's submitTry payload; anything malformed is the app's bug, not a game rule. */
export function parseSubmitTry(data: unknown): SubmitTryRequest {
  const d = (data ?? {}) as Record<string, unknown>;
  const bad = (what: string) => new HttpsError("invalid-argument", `Bad ${what}`);
  if (!isDate(d.date)) throw bad("date");
  if (!TIERS.includes(d.tier as Tier)) throw bad("tier");
  if (typeof d.requestId !== "string" || !/^[A-Za-z0-9_-]{8,64}$/.test(d.requestId)) throw bad("requestId");
  if (d.type !== "GUESS" && d.type !== "SKIP") throw bad("type");
  if (d.type === "GUESS" && (typeof d.songId !== "string" || !/^[A-Za-z0-9_-]{1,64}$/.test(d.songId))) throw bad("songId");
  return {
    date: d.date,
    tier: d.tier as Tier,
    requestId: d.requestId,
    type: d.type,
    ...(d.type === "GUESS" ? { songId: d.songId as string } : {}),
  };
}
