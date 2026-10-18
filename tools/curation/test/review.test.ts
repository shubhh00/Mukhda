import assert from "node:assert/strict";
import { test } from "node:test";
import { CandidateSchema } from "../src/candidates.ts";
import { buildReviewPrompt } from "../src/commands/review.ts";
import type { ItunesTrack } from "../src/itunes.ts";

test("review prompt lists each song with what Apple Music has and asks for a named file", () => {
  const candidate = CandidateSchema.parse({ title: "Aitbaar Nahi Karna", film: "Qayamat: City Under Threat", year: 2003, recognisability: 3 });
  const track = {
    trackName: "Aitbaar Nahi Karna",
    collectionName: "Qayamat (Original Motion Picture Soundtrack)",
    releaseDate: "1991-01-01T00:00:00Z",
  } as ItunesTrack;
  const prompt = buildReviewPrompt(
    [{ id: "chat/2003-all.json#7", row: { candidate, result: { status: "check", reasons: ["year_mismatch"], track } } }],
    "mukhda-review-001.json",
  );
  assert.match(prompt, /chat\/2003-all\.json#7 \| "Aitbaar Nahi Karna" from the film "Qayamat: City Under Threat" \(2003\)/);
  assert.match(prompt, /album "Qayamat \(Original Motion Picture Soundtrack\)" \(released 1991-01-01\) \| flagged: year_mismatch/);
  assert.match(prompt, /named exactly "mukhda-review-001\.json"/);
});
