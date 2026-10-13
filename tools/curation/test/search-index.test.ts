import assert from "node:assert/strict";
import { test } from "node:test";
import type { ItunesTrack } from "../src/itunes.ts";
import { assembleIndex, filmIdOf, indexTitle, isFullSoundtrack, isSong, mostCommon, songIdOf } from "../src/search-index.ts";

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
  assert.ok(isFullSoundtrack({ collectionName: "Pyaar Impossible (Original Soundtrack) - EP", trackCount: 7 }));
  assert.ok(!isFullSoundtrack({ collectionName: "Kal Ho Naa Ho - Bollywood Hits", trackCount: 100 }));
  assert.ok(!isFullSoundtrack({ collectionName: "Dekhha Tenu (From \"Mr. And Mrs. Mahi\") - Single", trackCount: 2 }));
});

test("themes, dialogues, medleys and in-title versions aren't songs", () => {
  for (const name of ["Theme Music", "Akash's Love Theme", "Dialogues", "Ranvijay's Entry Medley", "Hua Hua Remix",
    "Tum Dil Ki Dhadkan Instrumental", "Kabhi Kabhi Mere Dil Mein Yeh Sawaal Aata Hai House Track", "Zinda (Reprise)",
    "Chaleya Arabic Version", "Raja Ram – Female Version"]) {
    assert.ok(!isSong(name), name);
  }
  for (const name of ["Abrar's Entry Jamal Kudu", "Main Sada Hoon", "Tum Hi Ho", 'Kesariya (From "Brahmastra")']) {
    assert.ok(isSong(name), name);
  }
});

test("featured artists are cut from titles", () => {
  assert.equal(indexTitle("Main Hoon Saath Tere Feat. Shivangi Bhayana"), "Main Hoon Saath Tere");
  assert.equal(indexTitle("Tu Banja Gali Benaras Ki ft. Asees Kaur"), "Tu Banja Gali Benaras Ki");
  assert.equal(indexTitle("Kesariya"), "Kesariya");
});

test("a verified recreation stays searchable under its clean title, even as a remix", () => {
  const remix = track(700, "Oh Ho Ho Ho (Remix)", "Hindi Medium (Original Motion Picture Soundtrack)");
  const index = assembleIndex([{ track: remix, film: "Hindi Medium", year: 2017 }], []);
  assert.deepEqual(index.map((s) => [s.songId, s.title]), [["s_700", "Oh Ho Ho Ho"]]);
});

test("a track suggested for two films is filed once, under the film its album matches", () => {
  const kaySera = track(1281577228, "Kay Sera Sera", "Pukar (Original Motion Picture Soundtrack)");
  const pool = [
    { track: kaySera, film: "Dhadkan", year: 2000 },
    { track: kaySera, film: "Pukar", year: 2000 },
  ];
  const index = assembleIndex(pool, []);
  assert.equal(index.length, 1);
  assert.equal(index[0]?.filmTitle, "Pukar");
});

test("between a film and its sequel, the year closest to the track's release wins", () => {
  const chot = { ...track(1749323778, "Chot Dil Pe Lagi", "Ishq Vishk (Original Motion Picture Soundtrack)"), releaseDate: "2003-04-01T00:00:00Z" };
  const index = assembleIndex(
    [
      { track: chot, film: "Ishq Vishk Rebound", year: 2024 },
      { track: chot, film: "Ishq Vishk", year: 2003 },
    ],
    [],
  );
  assert.deepEqual(index.map((s) => [s.filmTitle, s.year]), [["Ishq Vishk", 2003]]);
});
