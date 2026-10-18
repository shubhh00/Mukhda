import assert from "node:assert/strict";
import { test } from "node:test";
import { parseCandidates, yearOfFile } from "../src/candidates.ts";

const song = {
  title: "Tum Hi Ho",
  film: "Aashiqui 2",
  year: 2013,
  composer: "Mithoon",
  singers: ["Arijit Singh"],
  is_recreation: false,
  why_known: "Chart-topping romantic ballad",
  recognisability: 5,
  confidence: 0.98,
};

test("reads a chat answer wrapped in a code fence and prose", () => {
  const answer = "Here you go:\n```json\n" + JSON.stringify([song]) + "\n```\nEnjoy!";
  const { candidates, invalid } = parseCandidates(answer);
  assert.equal(candidates.length, 1);
  assert.equal(candidates[0]?.title, "Tum Hi Ho");
  assert.equal(invalid, 0);
});

test("bad rows are counted, good rows kept", () => {
  const answer = JSON.stringify([song, { title: "No film" }, { ...song, recognisability: 9 }]);
  const { candidates, invalid } = parseCandidates(answer);
  assert.equal(candidates.length, 1);
  assert.equal(invalid, 2);
});

test("numbers given as strings are accepted", () => {
  const { candidates } = parseCandidates(JSON.stringify([{ ...song, year: "2013", confidence: "0.9" }]));
  assert.equal(candidates[0]?.year, 2013);
  assert.equal(candidates[0]?.confidence, 0.9);
});

test("an answer without an array is rejected", () => {
  assert.throws(() => parseCandidates("Sorry, I can't help with that."), /no JSON array/);
});

test("compact answers without year or confidence take the year from the file", () => {
  const answer = '[{"title":"Kal Ho Naa Ho","film":"Kal Ho Naa Ho","composer":"Shankar-Ehsaan-Loy","singers":["Sonu Nigam"],"recognisability":5,"group":1}]';
  const { candidates, invalid } = parseCandidates(answer, yearOfFile("2003-all-2.json"));
  assert.equal(invalid, 0);
  assert.equal(candidates[0]?.year, 2003);
  assert.equal(candidates[0]?.group, 1);
  assert.equal(candidates[0]?.confidence, undefined);
});

test("a year the model did give is kept", () => {
  const { candidates } = parseCandidates(JSON.stringify([{ ...song, year: 2002 }]), 2003);
  assert.equal(candidates[0]?.year, 2002);
});
