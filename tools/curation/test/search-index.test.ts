import assert from "node:assert/strict";
import { test } from "node:test";
import type { ItunesTrack } from "../src/itunes.ts";
import { assembleIndex, filmIdOf, isFullSoundtrack, mostCommon, songIdOf } from "../src/search-index.ts";

const track = (trackId: number, trackName: string, collectionName = "Lootera (Original Motion Picture Soundtrack)") =>
  ({ trackId, trackName, collectionName, artistName: "Amit Trivedi", releaseDate: "2013-05-29T00:00:00Z", trackViewUrl: "" }) as ItunesTrack;

test("ids come from Apple's track ids; film ids carry the year", () => {
  assert.equal(songIdOf(1440832321), "s_1440832321");
  assert.equal(filmIdOf("Mr. & Mrs. Mahi", 2024), "f_mrandmrsmahi_2024");
  assert.notEqual(filmIdOf("Don", 1978), filmIdOf("Don", 2006));
});

test("the index adds the rest of each soundtrack to the pool songs", () => {
  const pool = [{ track: track(2, "Shikayatein"), film: "Lootera", year: 2013 }];
  const album = { film: "Lootera", year: 2013, tracks: [track(1, "Sawaar Loon"), track(2, "Shikayatein"), track(3, "Zinda")] };
  const index = assembleIndex(pool, [album]);
  assert.deepEqual(index.map((s) => s.title), ["Sawaar Loon", "Shikayatein", "Zinda"]);
  assert.deepEqual(index.map((s) => s.filmId), Array(3).fill("f_lutera_2013"));
});

test("remixes, reprises and instrumentals are left out", () => {
  const album = { film: "Lootera", year: 2013, tracks: [track(1, "Zinda"), track(4, "Zinda (Reprise)"), track(5, "Shikayatein (Instrumental)")] };
  assert.deepEqual(assembleIndex([], [album]).map((s) => s.title), ["Zinda"]);
});

test("a song on both a single and the album keeps the pool's id", () => {
  const single = track(900, 'Kesariya (From "Brahmastra")', 'Kesariya (From "Brahmastra") - Single');
  const pool = [{ track: single, film: "Brahmastra Part One: Shiva", year: 2022 }];
  const album = { film: "Brahmastra Part One: Shiva", year: 2022, tracks: [track(100, "Kesariya", "Brahmastra (OST)"), track(101, "Deva Deva", "Brahmastra (OST)")] };
  const index = assembleIndex(pool, [album]);
  assert.equal(index.find((s) => s.title === "Kesariya")?.songId, "s_900");
  assert.equal(index.length, 2);
});

test("helpers", () => {
  assert.equal(mostCommon(["Lootera", "Lootera", "Lutera"]), "Lootera");
  assert.ok(isFullSoundtrack({ collectionName: "Lootera (Original Motion Picture Soundtrack)", trackCount: 6 }));
  assert.ok(!isFullSoundtrack({ collectionName: "Kesariya - Single", trackCount: 1 }));
  assert.ok(!isFullSoundtrack({ collectionName: "Purani Dilli - EP", trackCount: 4 }));
});
