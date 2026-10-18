import assert from "node:assert/strict";
import { mkdtemp, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import type { ItunesTrack } from "../src/itunes.ts";
import type { MatchResult } from "../src/match.ts";
import { CandidateSchema } from "../src/candidates.ts";
import { applyReview, correctedCandidate, loadReviewAnswers } from "../src/review-answers.ts";

const track = { trackId: 1, trackName: "Na Na Karte Pyar", collectionName: "Dhadkan", previewUrl: "https://x", releaseDate: "1992-01-01" } as ItunesTrack;
const flagged: MatchResult = { status: "check", reasons: ["year_mismatch"], track };
const keep = { id: "chat/2000-all.json#3", verdict: "keep" as const, apple_track_is_original: true };

test("a confirmed keep makes a flagged row usable", () => {
  const result = applyReview(flagged, keep);
  assert.equal(result.status, "found");
  assert.deepEqual(result.reasons, []);
  assert.equal(result.reviewed, true);
});

test("keep without a confirmed original, or without a preview, changes nothing", () => {
  assert.equal(applyReview(flagged, { ...keep, apple_track_is_original: null }), flagged);
  const noPreview: MatchResult = { ...flagged, track: { ...track, previewUrl: undefined } };
  assert.equal(applyReview(noPreview, keep), noPreview);
});

test("fix and drop leave the row out of the pool", () => {
  assert.equal(applyReview(flagged, { ...keep, verdict: "fix" }), flagged);
  assert.equal(applyReview(flagged, { ...keep, verdict: "drop" }), flagged);
  assert.equal(applyReview(flagged, undefined), flagged);
});

test("answers load from chat files, code fences and all", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "answers-"));
  await writeFile(path.join(dir, "mukhda-review-001.json"), "```json\n" + JSON.stringify([keep, { id: "x", verdict: "maybe" }]) + "\n```");
  const answers = await loadReviewAnswers(dir);
  assert.equal(answers.size, 1);
  assert.equal(answers.get(keep.id)?.verdict, "keep");
});

test("a kept song takes the film and year the reviewer corrected", () => {
  const candidate = CandidateSchema.parse({ title: "Kay Sera Sera", film: "Dhadkan", year: 2000, recognisability: 3 });
  const fixed = correctedCandidate(candidate, { ...keep, correct_film: "Pukar", correct_year: 2000 });
  assert.equal(fixed.film, "Pukar");
  assert.equal(correctedCandidate(candidate, { ...keep, correct_film: null }).film, "Dhadkan");
});
