import assert from "node:assert/strict";
import { test } from "node:test";
import { currentStep, judge, statsAfterRound, statusAfter, topPercent } from "../src/rules.ts";
import { addDays, istDate, showEndsAt } from "../src/time.ts";
import { parseSubmitTry } from "../src/validate.ts";

const answer = { songId: "s_1", filmId: "f_1" };

test("same song is correct, same film is right film, a skip is skipped", () => {
  assert.equal(judge("GUESS", answer, { songId: "s_1", filmId: "f_1" }), "CORRECT");
  assert.equal(judge("GUESS", answer, { songId: "s_2", filmId: "f_1" }), "RIGHT_FILM");
  assert.equal(judge("GUESS", answer, { songId: "s_3", filmId: "f_9" }), "WRONG");
  assert.equal(judge("SKIP", answer, null), "SKIPPED");
});

test("a round is won on a correct try, lost after the fifth miss", () => {
  assert.deepEqual(statusAfter(["WRONG", "SKIPPED", "CORRECT"]), { status: "WON", solvedAtStep: 2 });
  assert.deepEqual(statusAfter(["WRONG", "RIGHT_FILM", "SKIPPED", "WRONG"]), { status: "OPEN", solvedAtStep: null });
  assert.deepEqual(statusAfter(["WRONG", "RIGHT_FILM", "SKIPPED", "WRONG", "SKIPPED"]), { status: "LOST", solvedAtStep: null });
  assert.equal(currentStep(0), 0);
  assert.equal(currentStep(3), 3);
  assert.equal(currentStep(5), 4);
});

test("the show day is the IST date and ends at the next midnight IST", () => {
  assert.equal(istDate(new Date("2026-10-12T18:29:59.999Z")), "2026-10-12");
  assert.equal(istDate(new Date("2026-10-12T18:30:00.000Z")), "2026-10-13");
  assert.equal(istDate(new Date("2026-10-11T18:30:00.000Z")), "2026-10-12");
  assert.equal(showEndsAt("2026-10-12").toISOString(), "2026-10-12T18:30:00.000Z");
  assert.equal(addDays("2026-03-01", -1), "2026-02-28");
  assert.equal(addDays("2026-12-31", 1), "2027-01-01");
});

test("Top X%: the share who did at least as well, never 0", () => {
  let stats = statsAfterRound(undefined, 2);
  stats = statsAfterRound(stats, 0);
  stats = statsAfterRound(stats, null);
  stats = statsAfterRound(stats, 4);
  assert.deepEqual(stats, { played: 4, won: 3, lost: 1, byStep: [1, 0, 1, 0, 1] });
  assert.equal(topPercent(stats, 0), 25);
  assert.equal(topPercent(stats, 2), 50);
  assert.equal(topPercent(stats, null), 100);
  assert.equal(topPercent(undefined, 0), null);
  assert.equal(topPercent({ played: 1000, won: 1, lost: 999, byStep: [1, 0, 0, 0, 0] }, 0), 1);
});

test("submitTry payloads are checked before anything runs", () => {
  const ok = { date: "2026-10-12", tier: "HARD", requestId: "abcdef12", type: "GUESS", songId: "s_1" };
  assert.deepEqual(parseSubmitTry(ok), ok);
  assert.deepEqual(parseSubmitTry({ ...ok, type: "SKIP" }), { date: "2026-10-12", tier: "HARD", requestId: "abcdef12", type: "SKIP" });
  for (const bad of [
    { ...ok, date: "12-10-2026" },
    { ...ok, tier: "IMPOSSIBLE" },
    { ...ok, requestId: "short" },
    { ...ok, type: "PEEK" },
    { ...ok, songId: undefined },
    { ...ok, songId: "../songs/x" },
    null,
  ]) {
    assert.throws(() => parseSubmitTry(bad), /Bad/);
  }
});
