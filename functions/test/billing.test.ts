import assert from "node:assert/strict";
import { test } from "node:test";
import { overBudget } from "../src/billing.ts";

test("billing stops once the month's spending reaches the budget, not before", () => {
  assert.equal(overBudget({ costAmount: 40, budgetAmount: 100 }), false);
  assert.equal(overBudget({ costAmount: 99.99, budgetAmount: 100 }), false);
  assert.equal(overBudget({ costAmount: 100, budgetAmount: 100 }), true);
  assert.equal(overBudget({ costAmount: 130, budgetAmount: 100 }), true);
  // A budget of nothing never stops anything by mistake.
  assert.equal(overBudget({ costAmount: 0, budgetAmount: 0 }), false);
});
