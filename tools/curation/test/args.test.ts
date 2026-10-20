import assert from "node:assert/strict";
import { test } from "node:test";
import { date, wholeNumber } from "../src/args.ts";

test("publish flags are checked before anything runs", () => {
  assert.equal(wholeNumber("--days", "60", 1, 366), 60);
  assert.throws(() => wholeNumber("--days", "0", 1, 366), /--days must be a whole number/);
  assert.throws(() => wholeNumber("--days", "sixty", 1, 366), /--days/);
  assert.throws(() => wholeNumber("--seed", "1.5", 0, 100), /--seed/);
  assert.equal(date("--launch", "2026-11-01"), "2026-11-01");
  assert.equal(date("--launch", undefined), undefined);
  assert.throws(() => date("--from", "1-11-2026"), /--from must be a date/);
});
