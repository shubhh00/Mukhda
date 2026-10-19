/**
 * The show day is the date in India (IST, UTC+5:30, no daylight saving), always from the server's
 * clock. Never the device's.
 */
const IST_OFFSET_MS = (5 * 60 + 30) * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;

/** The IST date at [now], as yyyy-mm-dd. */
export function istDate(now: Date): string {
  return new Date(now.getTime() + IST_OFFSET_MS).toISOString().slice(0, 10);
}

/** The instant [date]'s show ends: the following midnight IST (18:30 UTC on [date]). */
export function showEndsAt(date: string): Date {
  return new Date(Date.parse(`${date}T00:00:00Z`) + DAY_MS - IST_OFFSET_MS);
}

/** The IST date [days] after [date] (negative for before). */
export function addDays(date: string, days: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + days * DAY_MS).toISOString().slice(0, 10);
}

export const isDate = (value: unknown): value is string =>
  typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) && !Number.isNaN(Date.parse(`${value}T00:00:00Z`));
