import { getApps, initializeApp } from "firebase-admin/app";
import { type Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { type ScheduleDoc, type SongDoc, TIERS, type Tier } from "../src/model.ts";
import type { PreviewLookup } from "../src/previews.ts";

export const PROJECT = "demo-mukhda";

/** The Firestore emulator; tests only run under `firebase emulators:exec` (npm test). */
export function db(): Firestore {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error("Run the tests with `npm test`, which starts the Firestore emulator.");
  if (getApps().length === 0) initializeApp({ projectId: PROJECT });
  return getFirestore();
}

/** Empties the emulator's database between tests. */
export async function clearDb(): Promise<void> {
  const url = `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`;
  const response = await fetch(url, { method: "DELETE" });
  if (!response.ok) throw new Error(`Couldn't clear the emulator (${response.status})`);
}

/** 12 Oct 2026, mid-afternoon IST. */
export const DATE = "2026-10-12";
export const NOON = new Date("2026-10-12T09:00:00Z");

export function song(songId: string, filmId: string, overrides: Partial<SongDoc> = {}): SongDoc {
  return {
    songId,
    trackId: Number(songId.replace(/\D/g, "")) || 1,
    title: `Title of ${songId}`,
    filmId,
    filmTitle: `Film ${filmId}`,
    year: 2005,
    composer: "Shankar-Ehsaan-Loy",
    previewUrl: `https://audio.example/${songId}.m4a`,
    previewUrlCachedAt: Timestamp.fromDate(NOON),
    startOffsetMs: 0,
    artworkUrl: `https://art.example/${songId}.jpg`,
    appleMusicUrl: `https://music.apple.com/in/song/${songId}`,
    tier: "EASY",
    tierSource: "DATA",
    status: "ACTIVE",
    ...overrides,
  };
}

/**
 * Seeds a day's show: tier T's answer is s_<n>1 from film f_<n> (n = 1..5), with a backup s_<n>9,
 * and another song from the same film, s_<n>2, in the search catalogue. Also an unrelated
 * catalogue song s_99 from film f_99.
 */
export async function seedShow(firestore: Firestore, date = DATE, showNo = 12): Promise<void> {
  const batch = firestore.batch();
  const tiers = {} as ScheduleDoc["tiers"];
  TIERS.forEach((tier, i) => {
    const n = i + 1;
    batch.set(firestore.doc(`songs/s_${n}1`), song(`s_${n}1`, `f_${n}`, { tier }));
    batch.set(firestore.doc(`songs/s_${n}9`), song(`s_${n}9`, `f_${n}9`, { tier }));
    batch.set(firestore.doc(`catalog/s_${n}2`), { filmId: `f_${n}` });
    tiers[tier] = { songId: `s_${n}1`, backupSongId: `s_${n}9` };
  });
  batch.set(firestore.doc("catalog/s_99"), { filmId: "f_99" });
  batch.set(firestore.doc(`schedule/${date}`), { showNo, tiers });
  await batch.commit();
}

/** The answer song for [tier] in a seeded show. */
export const answerOf = (tier: Tier) => `s_${TIERS.indexOf(tier) + 1}1`;

/** A preview lookup that never runs: seeded previews are fresh, so none should be needed. */
export const noLookup: PreviewLookup = async () => {
  throw new Error("unexpected preview lookup");
};

let requests = 0;
export const requestId = () => `req_${Date.now()}_${requests++}`;
