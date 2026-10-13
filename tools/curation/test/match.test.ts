import assert from "node:assert/strict";
import { test } from "node:test";
import { CandidateSchema, type Candidate } from "../src/candidates.ts";
import type { ItunesTrack } from "../src/itunes.ts";
import { better, evaluate, filmOf, splitTrackName, titleMatches } from "../src/match.ts";

function candidate(overrides: Partial<Candidate> = {}): Candidate {
  return CandidateSchema.parse({
    title: "Kesariya",
    film: "Brahmastra Part One: Shiva",
    year: 2022,
    singers: ["Arijit Singh"],
    recognisability: 5,
    confidence: 0.9,
    ...overrides,
  });
}

function track(overrides: Partial<ItunesTrack> = {}): ItunesTrack {
  return {
    trackId: 1,
    trackName: "Kesariya",
    collectionName: "Brahmastra (Original Motion Picture Soundtrack)",
    artistName: "Pritam & Arijit Singh",
    releaseDate: "2022-07-17T07:00:00Z",
    previewUrl: "https://audio.example/preview.m4a",
    trackViewUrl: "https://music.apple.com/in/album/1",
    ...overrides,
  };
}

test("splits a track name into title and suffix", () => {
  assert.deepEqual(splitTrackName('Kesariya (From "Brahmastra")'), { base: "Kesariya", suffix: ' (From "Brahmastra")' });
  assert.deepEqual(splitTrackName("Tum Hi Ho - Female Version"), { base: "Tum Hi Ho", suffix: " - Female Version" });
  assert.deepEqual(splitTrackName("Zinda"), { base: "Zinda", suffix: "" });
});

test("film comes from From \"X\" or the album name without soundtrack wording", () => {
  assert.equal(filmOf(track({ trackName: 'Kesariya (From "Brahmastra")', collectionName: "Kesariya - Single" })), "Brahmastra");
  assert.equal(filmOf(track({ collectionName: "Lootera (Original Motion Picture Soundtrack) - EP" })), "Lootera");
  assert.equal(filmOf(track({ collectionName: "Kal Ho Naa Ho" })), "Kal Ho Naa Ho");
});

test("iTunes-style suffixes in a model's title are ignored", () => {
  assert.ok(titleMatches('Jadu Hai Nasha Hai - From "Jism"', "Jadu Hai Nasha Hai"));
  assert.ok(titleMatches("O Ajnabi (Happy Version)", "O Ajnabi"));
});

test("title match tolerates spelling variants but not different songs", () => {
  assert.ok(titleMatches("Kal Ho Naa Ho", "Kal Ho Na Ho"));
  assert.ok(titleMatches("Ankahee", "Ankahi"));
  assert.ok(titleMatches("Kesariya", 'Kesariya (From "Brahmastra")'));
  assert.ok(!titleMatches("Kabira", "Ilahi"));
});

test("clean match is found", () => {
  const result = evaluate(candidate(), [track()]);
  assert.equal(result.status, "found");
  assert.deepEqual(result.reasons, []);
});

test("the original is preferred over a remix listed first", () => {
  const remix = track({ trackId: 2, trackName: "Kesariya (Remix)" });
  const result = evaluate(candidate(), [remix, track()]);
  assert.equal(result.status, "found");
  assert.equal(result.track?.trackId, 1);
});

test("only a remix available needs a check", () => {
  const result = evaluate(candidate(), [track({ trackName: "Kesariya (Lofi Version)" })]);
  assert.equal(result.status, "check");
  assert.deepEqual(result.reasons, ["not_original"]);
});

test("a recreation is allowed a version marker", () => {
  const result = evaluate(candidate({ is_recreation: true }), [track({ trackName: "Kesariya (Recreated)" })]);
  assert.equal(result.status, "found");
});

test("same title from another film needs a check", () => {
  const result = evaluate(candidate(), [track({ collectionName: "Some Other Film" })]);
  assert.deepEqual(result.reasons, ["film_mismatch"]);
});

test("missing preview and a release well before the film are flagged", () => {
  const result = evaluate(candidate(), [track({ previewUrl: undefined, releaseDate: "2015-01-01T00:00:00Z" })]);
  assert.equal(result.status, "check");
  assert.deepEqual(result.reasons, ["no_preview", "year_mismatch"]);
});

test("a later digital reissue of the soundtrack is fine", () => {
  assert.equal(evaluate(candidate(), [track({ releaseDate: "2023-02-01T00:00:00Z" })]).status, "found");
  const pinjar = candidate({ title: "Darda Marya", film: "Pinjar", year: 2003 });
  const reissue = track({ trackName: "Darda Marya", collectionName: "Pinjar (Original Motion Picture Soundtrack)", releaseDate: "2013-03-01T00:00:00Z" });
  assert.equal(evaluate(pinjar, [reissue]).status, "found");
});

test("a label's placeholder date a few years early is forgiven when the album is exactly the film", () => {
  const song = candidate({ title: "Ajab Si", film: "Om Shanti Om", year: 2007 });
  const album = track({ trackName: "Ajab Si", collectionName: "Om Shanti Om (Original Motion Picture Soundtrack)", releaseDate: "2005-03-02T00:00:00Z" });
  assert.equal(evaluate(song, [album]).status, "found");
});

test("an early date is still flagged for remakes and loose film matches", () => {
  // Don (2006) vs the 1978 original: same name, decades apart.
  const remake = candidate({ title: "Khaike Paan Banaraswala", film: "Don", year: 2006 });
  const original = track({ trackName: "Khaike Paan Banaraswala", collectionName: "Don (Original Motion Picture Soundtrack)", releaseDate: "1978-01-01T00:00:00Z" });
  assert.deepEqual(evaluate(remake, [original]).reasons, ["year_mismatch"]);
  // "Qayamat" only loosely matches "Qayamat: City Under Threat", so its 1991 date stays flagged.
  const qayamat = candidate({ title: "Aitbaar Nahi Karna", film: "Qayamat: City Under Threat", year: 2003 });
  const older = track({ trackName: "Aitbaar Nahi Karna", collectionName: "Qayamat (Original Motion Picture Soundtrack)", releaseDate: "2001-01-01T00:00:00Z" });
  assert.deepEqual(evaluate(qayamat, [older]).reasons, ["year_mismatch"]);
});

test("'&' and 'And' in film names are the same", () => {
  const song = candidate({ title: "Tu Hain Toh", film: "Mr. & Mrs. Mahi", year: 2024 });
  const album = track({
    trackName: 'Tu Hain Toh (From "Mr. And Mrs. Mahi")',
    collectionName: "Mr. And Mrs. Mahi (Original Motion Picture Soundtrack)",
    releaseDate: "2024-05-24T00:00:00Z",
  });
  assert.equal(evaluate(song, [album]).status, "found");
});

test("titles with different numbers never match", () => {
  assert.ok(!titleMatches("Bhool Bhulaiyaa 3 Title Track", 'Bhool Bhulaiyaa 2 Title Track (From "Bhool Bhulaiyaa 2")'));
  assert.ok(titleMatches("Bhool Bhulaiyaa 3 Title Track", "Bhool Bhulaiya 3 Title Track"));
});

test("very short film names match exactly", () => {
  const song = candidate({ title: "Spaney", film: "Sssshhh...", year: 2003 });
  const album = track({ trackName: "Spaney", collectionName: "Sssshhh... (Original Motion Picture Soundtrack)", releaseDate: "2003-09-05T00:00:00Z" });
  assert.equal(evaluate(song, [album]).status, "found");
  assert.equal(evaluate(song, [track({ trackName: "Spaney", collectionName: "Sholay", releaseDate: "2003-09-05T00:00:00Z" })]).status, "check");
});

test("no title match means missing", () => {
  const result = evaluate(candidate(), [track({ trackName: "Deva Deva" })]);
  assert.equal(result.status, "missing");
  assert.deepEqual(result.reasons, ["no_title_match"]);
});

test("better prefers found over check over missing", () => {
  const found = evaluate(candidate(), [track()]);
  const missing = evaluate(candidate(), []);
  assert.equal(better(missing, found), found);
  assert.equal(better(found, missing), found);
});
