import assert from "node:assert/strict";
import { test } from "node:test";
import { CandidateSchema } from "../src/candidates.ts";
import { renderMarkdown, songsPerYear, summarize } from "../src/commands/report.ts";
import type { VerifiedFile, VerifiedRow } from "../src/commands/verify.ts";
import type { ItunesTrack } from "../src/itunes.ts";

function row(title: string, status: "found" | "check" | "missing", trackId?: number, confidence = 0.9): VerifiedRow {
  const candidate = CandidateSchema.parse({ title, film: "Film", year: 2013, recognisability: 4, confidence });
  const track = trackId === undefined ? undefined : ({ trackId } as ItunesTrack);
  const reasons = status === "found" ? [] : status === "check" ? ["film_mismatch" as const] : ["no_title_match" as const];
  return { candidate, result: { status, reasons, track } };
}

const files: VerifiedFile[] = [
  { source: "chat", file: "2013-chart_hits.json", invalid: 1, rows: [row("A", "found", 1), row("B", "found", 2), row("C", "missing", undefined, 0.4)] },
  { source: "chat", file: "2013-lesser_known_films.json", invalid: 0, rows: [row("A", "found", 1), row("D", "check")] },
  { source: "pilot", file: "2013-chart_hits.json", invalid: 0, rows: [row("B", "found", 2), row("E", "found", 3)] },
];

test("summarises each source", () => {
  const [chat, pilot] = summarize(files);
  assert.equal(chat?.source, "chat");
  assert.equal(chat?.candidates, 5);
  assert.equal(chat?.invalid, 1);
  assert.equal(chat?.found, 3);
  assert.equal(chat?.check, 1);
  assert.equal(chat?.missing, 1);
  assert.equal(chat?.duplicates, 1);
  assert.equal(chat?.uniqueFound, 2);
  assert.deepEqual(chat?.checkReasons, { film_mismatch: 1 });
  assert.equal(chat?.meanConfidenceMissing, 0.4);
  assert.deepEqual(chat?.foundByRecognisability, [0, 0, 0, 3, 0]);
  assert.equal(pilot?.uniqueFound, 2);
});

test("report shows overlap and union", () => {
  const markdown = renderMarkdown(summarize(files));
  assert.match(markdown, /chat ∩ pilot: 1/);
  assert.match(markdown, /Union across all sources: 3 unique songs/);
});

test("songs per year are merged across sources and thin years flagged", () => {
  const found = (title: string, year: number, trackId: number): VerifiedRow => {
    const r = row(title, "found", trackId);
    return { ...r, candidate: { ...r.candidate, year } };
  };
  const yearFiles: VerifiedFile[] = [
    { source: "chat", file: "2004-all.json", invalid: 0, rows: [1, 2, 3, 4].map((id) => found(`a${id}`, 2004, id)) },
    { source: "pilot", file: "2004-all.json", invalid: 0, rows: [4, 5].map((id) => found(`b${id}`, 2004, id)) },
    { source: "chat", file: "2005-all.json", invalid: 0, rows: [6, 7, 8, 9, 10].map((id) => found(`c${id}`, 2005, id)) },
    { source: "chat", file: "2006-all.json", invalid: 0, rows: [11, 12].map((id) => found(`d${id}`, 2006, id)) },
    { source: "chat", file: "2020-all.json", invalid: 0, rows: [13].map((id) => found(`e${id}`, 2020, id)) },
  ];
  const years = songsPerYear(yearFiles);
  assert.deepEqual(years.map((y) => [y.year, y.usable, y.thin]), [
    [2004, 5, false],
    [2005, 5, false],
    [2006, 2, true],
    [2020, 1, true],
  ]);
  const markdown = renderMarkdown(summarize(yearFiles), years);
  assert.match(markdown, /\| 2006 \| 2 \| \*\*thin: consider a re-run\*\* \|/);
  assert.match(markdown, /\| 2020 \| 1 \| thin \(COVID year, fewer releases\) \|/);
});
