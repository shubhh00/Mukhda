/** Checks for command-line values, so a typo fails with the flag's name before anything runs. */

/** [value] as a whole number from [min] to [max], or an error naming [flag]. */
export function wholeNumber(flag: string, value: string, min: number, max: number): number {
  const n = Number(value);
  if (!Number.isInteger(n) || n < min || n > max) throw new Error(`${flag} must be a whole number from ${min} to ${max}, not "${value}"`);
  return n;
}

/** [value] as a yyyy-mm-dd date, or an error naming [flag]; absent stays absent. */
export function date(flag: string, value: string | undefined): string | undefined {
  if (value === undefined) return undefined;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(Date.parse(`${value}T00:00:00Z`))) {
    throw new Error(`${flag} must be a date like 2026-11-01, not "${value}"`);
  }
  return value;
}
