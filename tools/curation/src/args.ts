/** Checks for command-line values, so a typo fails with the flag's name before anything runs. */

/** [value] as a whole number from [min] to [max], or an error naming [flag]. */
export function wholeNumber(flag: string, value: string, min: number, max: number): number {
  // Number("") is 0, so an empty value must be refused before it's read as a number.
  const n = value.trim() === "" ? NaN : Number(value);
  if (!Number.isInteger(n) || n < min || n > max) throw new Error(`${flag} must be a whole number from ${min} to ${max}, not "${value}"`);
  return n;
}

/** [value] as a yyyy-mm-dd date, or an error naming [flag]; absent stays absent. */
export function date(flag: string, value: string | undefined): string | undefined {
  if (value === undefined) return undefined;
  // A date that doesn't exist (2026-02-30) parses as a later one; it must read back the same.
  const parsed = new Date(`${value}T00:00:00Z`);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) {
    throw new Error(`${flag} must be a date like 2026-11-01, not "${value}"`);
  }
  return value;
}
