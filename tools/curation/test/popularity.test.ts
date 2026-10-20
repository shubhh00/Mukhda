import assert from "node:assert/strict";
import { test } from "node:test";
import { reviewSheet, type TieredSong } from "../src/commands/tier.ts";
import { matchUploads } from "../src/commands/youtube.ts";
import type { PoolSong } from "../src/pool.ts";
import {
  agePenalty,
  artistMatches,
  filmNames,
  assignTiers,
  listenersFor,
  nameKeys,
  percentiles,
  tierOf,
  uploadMatches,
} from "../src/popularity.ts";

test("splits credits into one key per name", () => {
  assert.deepEqual(nameKeys(["Vishal-Shekhar, Shreya Ghoshal & KK", "A.R. Rahman feat. Sukhwinder Singh"]), [
    "vishalshekhar",
    "shreyaghoshal",
    "arrahman",
    "sukhvindersingh",
  ]);
});

test("artist spellings match: joined names, en dashes, one member of a trio", () => {
  const people = nameKeys(["Roop Kumar Rathod", "Salim-Sulaiman", "Shankar-Ehsaan-Loy"]);
  assert.ok(artistMatches("Roopkumar Rathod", people));
  assert.ok(artistMatches("Salim–Sulaiman", people));
  assert.ok(artistMatches("Shankar Ehsaan Loy", people));
  assert.ok(artistMatches("Shankar", people));
  assert.ok(!artistMatches("Taal", people));
  // Too short to match inside a longer name.
  assert.ok(!artistMatches("Loy", people));
});

test("listeners add up across the song's artist spellings, not other songs or remixes", () => {
  const people = nameKeys(["Shankar-Ehsaan-Loy", "Alisha Chinai"]);
  const results = [
    { name: "Kajra Re", artist: "Shankar-Ehsaan-Loy", listeners: 47_000 },
    { name: "Kajra re", artist: "Shankar", listeners: 10_000 },
    { name: "Kajra Re", artist: "Alisha Chinai", listeners: 3_000 },
    { name: "Kajra Re (Remix)", artist: "Alisha Chinai", listeners: 900 },
    { name: "Kajra Re", artist: "Some Cover Band", listeners: 500 },
    { name: "Kajra Mohabbat Wala", artist: "Asha Bhosle", listeners: 8_000 },
  ];
  assert.equal(listenersFor("Kajra Re", people, results), 60_000);
});

test("percentiles run 0 to 1 and ties share a rank", () => {
  assert.deepEqual(percentiles([10, 30, 20]), [0, 1, 0.5]);
  assert.deepEqual(percentiles([5, 5, 1]), [0.75, 0.75, 0]);
  assert.deepEqual(percentiles([7]), [0.5]);
});

test("fifths: top is Easy, bottom is Legend", () => {
  assert.equal(tierOf(1), "EASY");
  assert.equal(tierOf(0.85), "EASY");
  assert.equal(tierOf(0.5), "HARD");
  assert.equal(tierOf(0.1), "LEGEND");
  assert.equal(tierOf(0), "LEGEND");
});

test("songs rank within their year, then lean to recent ones", () => {
  const results = assignTiers(
    [
      { year: 2003, values: { youtube: 50_000_000 }, recognisability: 5 },
      { year: 2003, values: { youtube: 100_000 }, recognisability: 1 },
      { year: 2023, values: { youtube: 5_000_000 }, recognisability: 5 },
      { year: 2023, values: { youtube: 10_000 }, recognisability: 1 },
    ],
    2026,
  );
  // 2003's biggest hit has 10x 2023's views but ranks below it; it still beats 2023's least known song.
  assert.deepEqual(
    results.map((r) => r.tier),
    ["MEDIUM", "LEGEND", "EASY", "EXPERT"],
  );
});

test("the age adjustment grows with age, up to a cap", () => {
  assert.equal(agePenalty(2026, 2026), 0);
  assert.equal(agePenalty(2021, 2026), 0.1);
  assert.equal(agePenalty(2000, 2026), 0.4);
  // A year ahead (a release dated next year) is never a bonus.
  assert.equal(agePenalty(2027, 2026), 0);
});

test("sources blend by weight: YouTube outweighs Last.fm", () => {
  // Song 0 leads on YouTube, song 1 on Last.fm; YouTube's weight decides.
  const results = assignTiers([
    { year: 2010, values: { youtube: 90_000_000, lastfm: 100 }, recognisability: 3 },
    { year: 2010, values: { youtube: 1_000_000, lastfm: 90_000 }, recognisability: 3 },
  ]);
  assert.ok(results[0]!.score > results[1]!.score);
});

test("a source without the song gives its weight to the others", () => {
  const results = assignTiers([
    { year: 2012, values: { lastfm: 70_000 }, recognisability: 3 },
    { year: 2012, values: { youtube: 1_000, lastfm: 20_000 }, recognisability: 3 },
    { year: 2012, values: { youtube: 9_000, lastfm: 40_000 }, recognisability: 3 },
  ]);
  // Song 0 is top on Last.fm, the only source that has it.
  assert.deepEqual(results[0]!.percentiles, { lastfm: 1 });
  assert.equal(results[0]!.score, 1);
  assert.deepEqual(results[0]!.flags, ["one_source"]);
});

test("no data falls back to the chat's guess and is flagged", () => {
  const results = assignTiers([
    { year: 2010, values: {}, recognisability: 5 },
    { year: 2010, values: { youtube: 900 }, recognisability: 2 },
    { year: 2010, values: { youtube: 90 }, recognisability: 1 },
  ]);
  assert.deepEqual(results[0]!.flags, ["no_data"]);
  assert.deepEqual(results[0]!.percentiles, {});
});

test("sources far apart, or data far from the chat, are flagged", () => {
  const results = assignTiers([
    { year: 2015, values: { youtube: 90_000_000, lastfm: 10 }, recognisability: 3 },
    { year: 2015, values: { youtube: 50, lastfm: 70_000 }, recognisability: 3 },
    { year: 2015, values: { youtube: 5_000, lastfm: 4_000 }, recognisability: 3 },
    { year: 2015, values: { youtube: 80_000_000, lastfm: 80_000 }, recognisability: 1 },
    { year: 2015, values: { youtube: 10, lastfm: 5 }, recognisability: 5 },
  ]);
  assert.ok(results[0]!.flags.includes("sources_disagree"));
  assert.ok(results[3]!.flags.includes("chat_disagrees"));
  assert.ok(results[4]!.flags.includes("chat_disagrees"));
  assert.ok(!results[2]!.flags.includes("sources_disagree"));
});

test("a label upload counts when it names the song and the film", () => {
  assert.ok(uploadMatches("Kajra Re Full Song | Bunty Aur Babli | Aishwarya, Abhishek", "Kajra Re", "Bunty Aur Babli"));
  assert.ok(uploadMatches("Lyrical: Kajra Re | Bunty Aur Babli", "Kajra Re", "Bunty Aur Babli"));
  // No film, a cover, a jukebox, or the title only inside another word.
  assert.ok(!uploadMatches("Kajra Re | Aishwarya Rai", "Kajra Re", "Bunty Aur Babli"));
  assert.ok(!uploadMatches("Kajra Re cover | Bunty Aur Babli", "Kajra Re", "Bunty Aur Babli"));
  assert.ok(!uploadMatches("Bunty Aur Babli Audio Jukebox | Kajra Re, Dhadak Dhadak", "Kajra Re", "Bunty Aur Babli"));
  assert.ok(!uploadMatches("Donna | London Thumakda", "Don", "Don"));
});

test("a title track needs the film named twice or 'title track'", () => {
  assert.ok(uploadMatches("Kal Ho Naa Ho - Full Song | Kal Ho Naa Ho | Shah Rukh Khan", "Kal Ho Naa Ho", "Kal Ho Naa Ho"));
  assert.ok(uploadMatches("Kal Ho Naa Ho Title Track (Full Video) | Shah Rukh Khan", "Kal Ho Naa Ho", "Kal Ho Naa Ho"));
  // Another song from the film.
  assert.ok(!uploadMatches("Pretty Woman | Kal Ho Naa Ho | Shah Rukh Khan", "Kal Ho Naa Ho", "Kal Ho Naa Ho"));
  // The 1969 song the 2002 film is named after.
  assert.ok(!uploadMatches("Tumse Achha Kaun Hai | Janwar | Mohammed Rafi | Shammi Kapoor", "Tum Se Achcha Kaun Hai", "Tum Se Achcha Kaun Hai"));
});

test("uploads are matched to every song they name, spellings normalised", () => {
  const song = (songId: string, title: string, film: string) => ({ songId, title, film }) as PoolSong;
  const matches = matchUploads(
    [song("a", "Kajra Re", "Bunty Aur Babli"), song("b", "Tum Hi Ho", "Aashiqui 2")],
    [
      { id: "v1", title: "Kajraa Re | Bunty Aur Babli" },
      { id: "v2", title: "Tum Hi Ho Full Video Song | Aashiqui 2" },
      { id: "v3", title: "Tum Hi Ho | Some Other Film" },
    ],
  );
  assert.deepEqual(matches.get("a")?.map((u) => u.id), ["v1"]);
  assert.deepEqual(matches.get("b")?.map((u) => u.id), ["v2"]);
});

test("the review sheet lists flagged songs first and quotes commas", () => {
  const song = (overrides: Partial<TieredSong>): TieredSong => ({
    songId: "s_1",
    title: "Kajra Re",
    film: "Bunty Aur Babli",
    year: 2005,
    composer: "Shankar-Ehsaan-Loy",
    values: { youtube: 300_000_000, lastfm: 60_000 },
    recognisability: 5,
    score: 1,
    tier: "EASY",
    flags: [],
    ...overrides,
  });
  const csv = reviewSheet([song({}), song({ songId: "s_2", film: "Hum, Tum", values: {}, flags: ["no_data"] })]).split("\n");
  assert.equal(
    csv[0],
    "song_id,title,film,year,composer,tier,score,youtube_views,lastfm_listeners,recognisability,flags,new_tier",
  );
  assert.ok(csv[1]!.startsWith('s_2,Kajra Re,"Hum, Tum",2005,Shankar-Ehsaan-Loy,EASY,1.00,,,5,no_data,'));
  assert.ok(csv[2]!.startsWith("s_1,Kajra Re,Bunty Aur Babli,2005,Shankar-Ehsaan-Loy,EASY,1.00,300000000,60000,"));
});

test("a film also matches by its short name, without subtitle or part", () => {
  assert.deepEqual(filmNames("Brahmastra Part One: Shiva").map((n) => n.join(" ")), ["brahmastra part one shiva", "brahmastra"]);
  assert.ok(uploadMatches("Kesariya - Brahmāstra | Ranbir Kapoor | Alia Bhatt | Pritam | Arijit Singh", "Kesariya", "Brahmastra Part One: Shiva"));
  assert.ok(uploadMatches("Chugliyaan | Once Upon Ay Time In Mumbai Dobaara | Akshay Kumar", "Chugliyaan", "Once Upon Ay Time in Mumbai Dobaara!"));
  // One short word is too loose to stand for the film.
  assert.deepEqual(filmNames("Don: The Chase Begins Again").length, 1);
});

test("in a year YouTube covers well, a song it lacks ranks at the bottom for it", () => {
  // 19 of 20 songs on YouTube: the missing one is obscure, not unknown, despite Last.fm.
  const covered = Array.from({ length: 19 }, (_, i) => ({ year: 2018, values: { youtube: (i + 1) * 1_000_000, lastfm: 100 }, recognisability: 3 }));
  const results = assignTiers([...covered, { year: 2018, values: { lastfm: 90_000 }, recognisability: 3 }]);
  assert.equal(results[19]!.percentiles.youtube, 0);
  assert.equal(results[19]!.tier, "LEGEND");
  assert.ok(results[19]!.flags.includes("one_source"));
});

test("in a year YouTube covers badly, a song it lacks is ranked on the rest", () => {
  // 2 of 4 songs on YouTube: a gap there means nothing.
  const results = assignTiers([
    { year: 2004, values: { youtube: 9_000_000, lastfm: 100 }, recognisability: 3 },
    { year: 2004, values: { youtube: 1_000_000, lastfm: 50 }, recognisability: 3 },
    { year: 2004, values: { lastfm: 90_000 }, recognisability: 3 },
    { year: 2004, values: { lastfm: 10 }, recognisability: 3 },
  ]);
  assert.equal(results[2]!.percentiles.youtube, undefined);
  assert.equal(results[2]!.tier, "EASY");
});

test("titles match with words split or joined differently", () => {
  assert.ok(uploadMatches("Tumhi Ho Bandhu - Full Video Song | Cocktail | Saif Ali Khan", "Tum Hi Ho Bandhu", "Cocktail"));
  assert.ok(uploadMatches("Kal Ho Na Ho | Kal Ho Naa Ho", "Kal Ho Naa Ho", "KalHo Naa Ho"));
  // Joining never reaches into part of a word.
  assert.ok(!uploadMatches("Tumhiho Bandhuon | Cocktail", "Tum Hi Ho Bandhu", "Cocktail"));
});

test("an upload spelling the title as one word still reaches the song", () => {
  const matches = matchUploads(
    [{ songId: "a", title: "Tum Hi Ho Bandhu", film: "Cocktail" } as PoolSong],
    [{ id: "v1", title: "Tumhi Ho Bandhu - Full Video Song | Cocktail" }],
  );
  assert.deepEqual(matches.get("a")?.map((u) => u.id), ["v1"]);
});
