import assert from "node:assert/strict";
import { mkdtemp, readdir, readFile, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { test } from "node:test";
import { importDownloads, parseDownloadName } from "../src/commands/import.ts";
import { parseYears } from "../src/config.ts";
import { buildPrompt } from "../src/prompt.ts";

const answer = (title: string) =>
  JSON.stringify([{ title, film: "Aashiqui 2", year: 2013, recognisability: 5, confidence: 0.9 }]);

test("year lists and ranges", () => {
  assert.deepEqual(parseYears("2003,2013"), [2003, 2013]);
  assert.deepEqual(parseYears("2000-2003"), [2000, 2001, 2002, 2003]);
  assert.deepEqual(parseYears("2000-2001, 2010"), [2000, 2001, 2010]);
  assert.throws(() => parseYears("2005-2001"), /bad year range/);
});

test("reads download names, including browser duplicates and .txt", () => {
  assert.deepEqual(parseDownloadName("mukhda-2004-all.json"), { year: 2004, kind: "all" });
  assert.deepEqual(parseDownloadName("mukhda-2003-chart_hits.json"), { year: 2003, kind: "chart_hits" });
  assert.deepEqual(parseDownloadName("mukhda-2013-composer_deep_cuts (1).json"), { year: 2013, kind: "composer_deep_cuts" });
  assert.equal(parseDownloadName("mukhda-2023-smaller_star_films.txt")?.kind, "smaller_star_films");
});

test("ignores unrelated files", () => {
  assert.equal(parseDownloadName("holiday-photos.json"), null);
  assert.equal(parseDownloadName("other-2003-chart_hits.json"), null);
  assert.equal(parseDownloadName("mukhda-2003-best_songs.json"), null);
  assert.equal(parseDownloadName("mukhda-review-001.json"), null);
});

test("per-year prompt covers all four groups and asks for the exact download name", () => {
  const prompt = buildPrompt(2004, "all", "mukhda-2004-all.json");
  assert.match(prompt, /List up to 160 songs/);
  assert.match(prompt, /1\. the year's biggest hits/);
  assert.match(prompt, /4\. songs from films without top-tier stars/);
  assert.match(prompt, /"group": 1-4/);
  assert.match(prompt, /Don't show the list in the chat/);
  assert.match(prompt, /Don't search the web/);
  assert.doesNotMatch(prompt, /at most 4 songs/);
  assert.doesNotMatch(prompt, /why_known|confidence/);
  assert.match(prompt, /named exactly "mukhda-2004-all\.json"/);
});

test("search prompt works from web search, not memory", () => {
  const prompt = buildPrompt(2026, "all", "mukhda-2026-all.json", { search: true });
  assert.match(prompt, /films released in India in 2026 so far/);
  assert.match(prompt, /search the web instead of relying on memory/);
  assert.match(prompt, /List of Hindi films of 2026/);
  assert.doesNotMatch(prompt, /Answer from memory/);
  assert.match(prompt, /named exactly "mukhda-2026-all\.json"/);
});

test("memory prompt is the default", () => {
  const prompt = buildPrompt(2004, "all", "mukhda-2004-all.json");
  assert.match(prompt, /Answer from memory/);
  assert.doesNotMatch(prompt, /so far/);
});

test("per-angle prompt keeps the pilot wording", () => {
  const prompt = buildPrompt(2013, "chart_hits", "mukhda-2013-chart_hits.json");
  assert.match(prompt, /List up to 40 songs/);
  assert.match(prompt, /Focus: the year's biggest hits/);
  assert.doesNotMatch(prompt, /"group"/);
});

test("re-runs are kept alongside earlier answers, exact repeats are dropped, bad files stay put", async () => {
  const downloads = await mkdtemp(path.join(os.tmpdir(), "dl-"));
  const candidates = await mkdtemp(path.join(os.tmpdir(), "cand-"));

  await writeFile(path.join(downloads, "mukhda-2013-chart_hits.json"), answer("First run"));
  await writeFile(path.join(downloads, "mukhda-2013-chart_hits (1).json"), answer("Second run"));
  await writeFile(path.join(downloads, "mukhda-2013-chart_hits (2).json"), answer("Second run"));
  await writeFile(path.join(downloads, "mukhda-2013-all.json"), "Sorry, I can't do that.");
  await writeFile(path.join(downloads, "notes.txt"), "unrelated");
  await importDownloads(downloads, candidates);

  // A later refresh of the same prompt adds a third run.
  await writeFile(path.join(downloads, "mukhda-2013-chart_hits.json"), answer("Refresh"));
  await importDownloads(downloads, candidates);

  const runs = (await readdir(path.join(candidates, "chat"))).sort();
  assert.deepEqual(runs, ["2013-chart_hits-2.json", "2013-chart_hits-3.json", "2013-chart_hits.json"]);
  const contents = await Promise.all(runs.map((f) => readFile(path.join(candidates, "chat", f), "utf8")));
  assert.equal(contents.filter((c) => c.includes("Second run")).length, 1);
  assert.ok(contents.some((c) => c.includes("Refresh")));
  assert.deepEqual((await readdir(downloads)).sort(), ["mukhda-2013-all.json", "notes.txt"]);
});
