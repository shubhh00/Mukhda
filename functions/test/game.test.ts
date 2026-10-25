import assert from "node:assert/strict";
import { beforeEach, test } from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { closeDay } from "../src/close.ts";
import type { RoundDoc, ScheduleDoc, SongStatsDoc } from "../src/model.ts";
import { PREVIEW_TTL_MS } from "../src/previews.ts";
import { getShow } from "../src/show.ts";
import { GameErrorException, submitTry } from "../src/tries.ts";
import { DATE, NOON, answerOf, clearDb, db, noLookup, requestId, seedShow } from "./helpers.ts";

const UID = "player_1";

beforeEach(async () => {
  await clearDb();
  await seedShow(db());
});

const skip = (now = NOON, id = requestId()) =>
  submitTry(db(), UID, { date: DATE, tier: "HARD", requestId: id, type: "SKIP" }, now, noLookup);
const guess = (songId: string, id = requestId()) =>
  submitTry(db(), UID, { date: DATE, tier: "HARD", requestId: id, type: "GUESS", songId }, NOON, noLookup);

async function rejects(promise: Promise<unknown>, reason: string): Promise<void> {
  await assert.rejects(promise, (error: unknown) => error instanceof GameErrorException && error.reason === reason);
}

test("a fresh show has every tier open, with no answers in it", async () => {
  const show = await getShow(db(), UID, NOON, noLookup);
  assert.equal(show.date, DATE);
  assert.equal(show.showNo, 12);
  assert.equal(show.endsAt, "2026-10-12T18:30:00.000Z");
  assert.deepEqual(Object.keys(show.tiers).sort(), ["EASY", "EXPERT", "HARD", "LEGEND", "MEDIUM"]);
  assert.deepEqual(show.tiers.HARD, {
    previewUrl: "https://audio.example/s_31.m4a",
    startOffsetMs: 0,
    status: "OPEN",
    triesUsed: 0,
    currentStep: 0,
    tries: [],
    reveal: null,
  });
});

test("the answer is never in an open round's show", async () => {
  await guess("s_99");
  await guess("s_32");
  // The clip's URL has to be there for the app to play it (real ones are opaque iTunes paths;
  // the test ones name the song), so it's left out of the check.
  const show = JSON.stringify(await getShow(db(), UID, NOON, noLookup), (key, value) => (key === "previewUrl" ? undefined : value));
  for (const secret of [answerOf("HARD"), "Title of s_31", "Film f_3"]) assert.ok(!show.includes(secret), `leaked ${secret}`);
});

test("wrong, right film, then correct: won at step 2, with the reveal", async () => {
  assert.equal((await guess("s_99")).result, "WRONG");
  const rightFilm = await guess("s_32");
  assert.deepEqual([rightFilm.result, rightFilm.triesUsed, rightFilm.currentStep, rightFilm.reveal], ["RIGHT_FILM", 2, 2, null]);
  const won = await guess(answerOf("HARD"));
  assert.equal(won.result, "CORRECT");
  assert.equal(won.status, "WON");
  assert.equal(won.reveal?.title, "Title of s_31");
  assert.equal(won.reveal?.topPercent, 100);

  const round = (await db().doc(`rounds/${UID}_${DATE}_HARD`).get()).data() as RoundDoc;
  assert.equal(round.solvedAtStep, 2);
  const stats = (await db().doc(`songStats/${DATE}_HARD`).get()).data() as SongStatsDoc;
  assert.deepEqual(stats, { played: 1, won: 1, lost: 0, byStep: [0, 0, 1, 0, 0] });

  const show = await getShow(db(), UID, NOON, noLookup);
  assert.equal(show.tiers.HARD?.status, "WON");
  assert.equal(show.tiers.HARD?.reveal?.songId, answerOf("HARD"));
  // Other tiers stay closed books.
  assert.equal(show.tiers.EASY?.reveal, null);
});

test("the same requestId twice uses one try and gets the same result", async () => {
  const id = requestId();
  const first = await guess("s_99", id);
  const again = await guess("s_99", id);
  assert.deepEqual(again, first);
  const round = (await db().doc(`rounds/${UID}_${DATE}_HARD`).get()).data() as RoundDoc;
  assert.equal(round.tries.length, 1);
});

test("a retried request that ended the round gets its reveal again", async () => {
  const id = requestId();
  const first = await guess(answerOf("HARD"), id);
  const again = await guess(answerOf("HARD"), id);
  assert.equal(again.status, "WON");
  assert.deepEqual(again.reveal, first.reveal);
});

test("five misses lose the round; a sixth try is turned down", async () => {
  for (let i = 0; i < 4; i++) assert.equal((await skip()).status, "OPEN");
  const last = await skip();
  assert.equal(last.status, "LOST");
  assert.equal(last.currentStep, 4);
  assert.equal(last.reveal?.songId, answerOf("HARD"));
  await rejects(skip(), "ALREADY_FINISHED");
  const stats = (await db().doc(`songStats/${DATE}_HARD`).get()).data() as SongStatsDoc;
  assert.deepEqual(stats, { played: 1, won: 0, lost: 1, byStep: [0, 0, 0, 0, 0] });
});

test("a guess must be a known song", async () => {
  await rejects(guess("s_nonexistent"), "INVALID_SONG");
  // Turning it down used no try.
  assert.equal((await db().doc(`rounds/${UID}_${DATE}_HARD`).get()).exists, false);
});

test("at 00:00:00 IST the old day's show is closed", async () => {
  const lastMoment = new Date("2026-10-12T18:29:59.999Z");
  assert.equal((await skip(lastMoment)).result, "SKIPPED");
  await rejects(skip(new Date("2026-10-12T18:30:00.000Z")), "ROUND_CLOSED");
});

test("a scheduled song whose preview is gone is swapped for its backup, for everyone", async () => {
  // HARD's main song was last confirmed long ago, and iTunes has dropped it.
  await db().doc("songs/s_31").update({ previewUrlCachedAt: Timestamp.fromMillis(NOON.getTime() - PREVIEW_TTL_MS - 1) });
  const lookups: number[] = [];
  const lookup = async (trackId: number) => {
    lookups.push(trackId);
    return null;
  };
  const show = await getShow(db(), UID, NOON, lookup);
  assert.equal(show.tiers.HARD?.previewUrl, "https://audio.example/s_39.m4a");
  const schedule = (await db().doc(`schedule/${DATE}`).get()).data() as ScheduleDoc;
  assert.equal(schedule.tiers.HARD.activeSongId, "s_39");

  // Another player gets the backup too, without another check; and it's the answer now.
  const other = await getShow(db(), "player_2", NOON, noLookup);
  assert.equal(other.tiers.HARD?.previewUrl, "https://audio.example/s_39.m4a");
  assert.deepEqual(lookups, [31]);
  assert.equal((await guess("s_39")).result, "CORRECT");
});

test("a stale preview that iTunes still has is kept and re-dated", async () => {
  await db().doc("songs/s_31").update({ previewUrlCachedAt: Timestamp.fromMillis(NOON.getTime() - PREVIEW_TTL_MS - 1) });
  const show = await getShow(db(), UID, NOON, async () => "https://audio.example/s_31-new.m4a");
  assert.equal(show.tiers.HARD?.previewUrl, "https://audio.example/s_31-new.m4a");
  const song = (await db().doc("songs/s_31").get()).data();
  assert.equal(song?.previewUrlCachedAt.toMillis(), NOON.getTime());
});

test("a failed preview check keeps the scheduled song", async () => {
  await db().doc("songs/s_31").update({ previewUrlCachedAt: Timestamp.fromMillis(0) });
  const show = await getShow(db(), UID, NOON, async () => {
    throw new Error("iTunes is down");
  });
  assert.equal(show.tiers.HARD?.previewUrl, "https://audio.example/s_31.m4a");
});

test("Top X% compares with the day's other players", async () => {
  // Two players before: one won at step 0, one lost.
  await submitTry(db(), "early_bird", { date: DATE, tier: "HARD", requestId: requestId(), type: "GUESS", songId: answerOf("HARD") }, NOON, noLookup);
  for (let i = 0; i < 5; i++) {
    await submitTry(db(), "unlucky", { date: DATE, tier: "HARD", requestId: requestId(), type: "SKIP" }, NOON, noLookup);
  }
  await skip();
  const won = await guess(answerOf("HARD"));
  // Won at step 1: the early bird and this player did at least as well, of three.
  assert.equal(won.reveal?.topPercent, 67);
});

test("closeDay marks yesterday's open rounds unfinished and leaves the rest", async () => {
  await skip();
  await submitTry(db(), "winner", { date: DATE, tier: "HARD", requestId: requestId(), type: "GUESS", songId: answerOf("HARD") }, NOON, noLookup);
  const nextDay = new Date("2026-10-12T18:35:00Z"); // 00:05 IST on the 13th
  const { date, closed } = await closeDay(db(), nextDay);
  assert.equal(date, DATE);
  assert.equal(closed, 1);
  const round = (await db().doc(`rounds/${UID}_${DATE}_HARD`).get()).data() as RoundDoc;
  assert.equal(round.status, "UNFINISHED");
  const winner = (await db().doc(`rounds/winner_${DATE}_HARD`).get()).data() as RoundDoc;
  assert.equal(winner.status, "WON");
  const stats = (await db().doc(`songStats/${DATE}_HARD`).get()).data() as SongStatsDoc;
  assert.equal(stats.unfinished, 1);
  // A second run finds nothing to do.
  assert.equal((await closeDay(db(), nextDay)).closed, 0);
});

test("no show scheduled is an error, not an empty show", async () => {
  await assert.rejects(getShow(db(), UID, new Date("2026-11-01T09:00:00Z"), noLookup), /No show scheduled/);
});

test("a 31st try in a minute is turned away, refused ones included", async () => {
  // Refused tries (an unknown song) count too, so they can't be repeated without limit.
  for (let i = 0; i < 30; i++) await rejects(guess("s_nonexistent"), "INVALID_SONG");
  await assert.rejects(skip(), /Too many requests/);
});

test("a 61st show in a minute is turned away", async () => {
  for (let i = 0; i < 60; i++) await getShow(db(), UID, NOON, noLookup);
  await assert.rejects(getShow(db(), UID, NOON, noLookup), /Too many requests/);
});

test("the show carries the server's clock", async () => {
  const show = await getShow(db(), UID, NOON, noLookup);
  assert.equal(show.serverTime, NOON.toISOString());
});

test("tomorrow's clips come only in the show's last five minutes, as audio links alone", async () => {
  await seedShow(db(), "2026-10-13", 13);
  assert.equal((await getShow(db(), UID, NOON, noLookup)).next, null);
  const late = new Date("2026-10-12T18:26:00Z"); // 23:56 IST
  const next = (await getShow(db(), UID, late, noLookup)).next;
  assert.equal(next?.date, "2026-10-13");
  for (const preview of Object.values(next!.previews)) assert.deepEqual(Object.keys(preview!).sort(), ["previewUrl", "startOffsetMs"]);
  assert.equal(Object.keys(next!.previews).length, 5);
});

test("no next show scheduled is no next clips, not an error", async () => {
  const late = new Date("2026-10-12T18:26:00Z");
  assert.equal((await getShow(db(), UID, late, noLookup)).next, null);
});

test("after midnight, a show says nothing about yesterday's songs", async () => {
  await skip(); // HARD started yesterday, never finished
  await seedShow(db(), "2026-10-13", 13);
  const show = await getShow(db(), UID, new Date("2026-10-13T03:00:00Z"), noLookup);
  assert.equal(show.date, "2026-10-13");
  assert.equal("ended" in show, false);
  for (const tier of Object.values(show.tiers)) assert.equal(tier!.reveal, null);
});
