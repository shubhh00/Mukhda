import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { editDistance, joined, normalize } from "../src/normalize.ts";

// Mirrors SearchNormalizerTest in :domain, so the app and the tool agree on spellings.
test("lowercases and strips punctuation", () => {
  assert.equal(normalize("Tujh Mein Rab Dikhta Hai!"), "tujh mein rab dikhta hai");
  assert.equal(normalize("Dil-E-Nadaan"), "dil e nadan");
  assert.equal(normalize("It's The Time To Disco"), "its the time to disco");
});

test("strips diacritics", () => {
  assert.equal(normalize("Café"), "cafe");
  assert.equal(normalize("Sájna"), "sajna");
});

test("romanisation variants meet", () => {
  assert.equal(normalize("Kabhee"), normalize("Kabhi"));
  assert.equal(normalize("Lootera"), normalize("Lutera"));
  assert.equal(normalize("Phir"), normalize("Fir"));
  assert.equal(normalize("Zara"), normalize("Jara"));
  assert.equal(normalize("Wada"), normalize("Vada"));
  assert.equal(normalize("Baazigar"), normalize("Bajigar"));
});

test("collapses repeated letters and joins", () => {
  assert.equal(normalize("Kal Ho Naa Ho"), "kal ho na ho");
  assert.equal(joined(normalize("Kal Ho Naa Ho")), "kalhonaho");
});

test("edit distance", () => {
  assert.equal(editDistance("abc", "abc"), 0);
  assert.equal(editDistance("abc", "abd"), 1);
  assert.equal(editDistance("abc", "ab"), 1);
  assert.equal(editDistance("", "abc"), 3);
});

// The same cases SearchNormalizerTest checks in the app: this tool builds the app's search
// database, so the two must normalise alike.
test("agrees with the app's SearchNormalizer on the shared cases", () => {
  const file = new URL("../../../domain/src/test/resources/search-normalizer-cases.tsv", import.meta.url);
  const cases = readFileSync(file, "utf8")
    .split(/\r?\n/)
    .filter((l) => l.trim() && !l.startsWith("#"))
    .map((l) => l.split("\t"));
  assert.equal(cases.length, 22);
  for (const [input, expected] of cases) assert.equal(normalize(input!), expected);
});
