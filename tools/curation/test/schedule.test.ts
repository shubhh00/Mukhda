import assert from "node:assert/strict";
import { test } from "node:test";
import { TIERS } from "../src/popularity.ts";
import { FILM_GAP_DAYS, MIN_WEIGHT, type SchedulableSong, SONG_GAP_DAYS, addDays, drawWeight, schedule, showNoOf } from "../src/schedule.ts";

/** [perTier] songs per tier, each from its own film, with [composers] composers taking turns. */
function pool(perTier: number, composers = 50): SchedulableSong[] {
  return TIERS.flatMap((tier) =>
    Array.from({ length: perTier }, (_, i) => ({
      songId: `s_${tier}_${i}`,
      filmId: `f_${tier}_${i}`,
      composer: `Composer ${i % composers}`,
      tier,
      year: 2000 + (i % 27),
    })),
  );
}

test("show numbers count from the launch date", () => {
  assert.equal(showNoOf("2026-10-12", "2026-10-01"), 12);
  assert.equal(showNoOf("2026-10-01", "2026-10-01"), 1);
  assert.equal(addDays("2026-12-30", 3), "2027-01-02");
});

test("each day has every tier, from that tier's songs, with a different backup", () => {
  const days = schedule(pool(30), [], "2026-10-01", 10, "2026-10-01", 1);
  assert.equal(days.length, 10);
  assert.deepEqual(days.map((d) => d.showNo), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  for (const day of days) {
    for (const tier of TIERS) {
      const { songId, backupSongId } = day.tiers[tier];
      assert.ok(songId.startsWith(`s_${tier}_`) && backupSongId.startsWith(`s_${tier}_`));
      assert.notEqual(songId, backupSongId);
    }
  }
});

test("the same seed schedules the same way", () => {
  assert.deepEqual(schedule(pool(30), [], "2026-10-01", 5, "2026-10-01", 7), schedule(pool(30), [], "2026-10-01", 5, "2026-10-01", 7));
  assert.notDeepEqual(schedule(pool(30), [], "2026-10-01", 5, "2026-10-01", 7), schedule(pool(30), [], "2026-10-01", 5, "2026-10-01", 8));
});

test("no song twice within a year, counting days already scheduled", () => {
  const songs = pool(60);
  const first = schedule(songs, [], "2026-10-01", 20, "2026-10-01", 3);
  const rest = schedule(songs, first, "2026-10-21", 20, "2026-10-01", 3);
  const answers = [...first, ...rest].flatMap((d) => TIERS.map((t) => d.tiers[t].songId));
  assert.equal(new Set(answers).size, answers.length);
  // The gap is exactly a year: after it, songs may come back.
  assert.equal(SONG_GAP_DAYS, 365);
});

test("a film is in the show at most once a week", () => {
  // Every song from one of only 20 films, shared by all tiers: films must still be a week apart.
  const songs = TIERS.flatMap((tier) =>
    Array.from({ length: 200 }, (_, i) => ({ songId: `s_${tier}_${i}`, filmId: `f_${i % 20}`, composer: `C${i}`, tier, year: 2020 })),
  );
  const days = schedule(songs, [], "2026-10-01", 2, "2026-10-01", 5);
  const films = days.flatMap((d) => TIERS.map((t) => songs.find((s) => s.songId === d.tiers[t].songId)!.filmId));
  assert.equal(new Set(films).size, films.length, `a film repeated within ${FILM_GAP_DAYS} days`);
});

test("a day spreads its composers when it can", () => {
  const days = schedule(pool(40, 8), [], "2026-10-01", 15, "2026-10-01", 9);
  for (const day of days) {
    const composers = TIERS.map((t) => `Composer ${Number(day.tiers[t].songId.split("_").at(-1)) % 8}`);
    assert.equal(new Set(composers).size, 5, `${day.date} repeats a composer`);
  }
});

test("running out of songs is an error, not a repeat", () => {
  // Each day's answer is used up for a year; with 3 songs a tier, day 3 has no answer and backup left.
  assert.throws(() => schedule(pool(3), [], "2026-10-01", 5, "2026-10-01", 1), /Ran out of/);
});

test("Easy, Medium and Hard draw recent songs more often; Expert and Legend don't lean", () => {
  const song = (tier: (typeof TIERS)[number], year: number) => ({ songId: "s", filmId: "f", composer: "c", tier, year });
  assert.equal(drawWeight(song("EASY", 2026), 2026), 1);
  assert.equal(drawWeight(song("MEDIUM", 2021), 2026), 0.5);
  assert.equal(drawWeight(song("HARD", 2001), 2026), MIN_WEIGHT);
  assert.equal(drawWeight(song("LEGEND", 2001), 2026), 1);

  // A season of shows from a pool with every year alike (10 of 27 years are 2016 on, so 37% with
  // no lean): Easy leans well past that, Legend doesn't.
  const songs = pool(400, 50);
  const year = (songId: string) => songs.find((s) => s.songId === songId)!.year;
  const days = schedule(songs, [], "2026-01-01", 200, "2026-01-01", 11);
  const recentShare = (tier: (typeof TIERS)[number]) =>
    days.filter((d) => year(d.tiers[tier].songId) >= 2016).length / days.length;
  assert.ok(recentShare("EASY") > 0.55, `Easy recent share ${recentShare("EASY")}`);
  assert.ok(recentShare("LEGEND") < 0.5, `Legend recent share ${recentShare("LEGEND")}`);
});
