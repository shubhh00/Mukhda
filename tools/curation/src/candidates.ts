import { z } from "zod";

export const CandidateSchema = z.object({
  title: z.string().trim().min(1),
  film: z.string().trim().min(1),
  year: z.coerce.number().int(),
  composer: z.string().trim().default(""),
  singers: z.array(z.string()).default([]),
  is_recreation: z.boolean().default(false),
  /** Which angle a song came from, in one-prompt-per-year answers (1-4). */
  group: z.coerce.number().int().min(1).max(4).optional(),
  /** Only in the pilot answers; the current prompt doesn't ask for it. */
  why_known: z.string().optional(),
  recognisability: z.coerce.number().min(1).max(5),
  /** Only in the pilot answers. It didn't separate real songs from wrong ones, so it's no longer asked for. */
  confidence: z.coerce.number().min(0).max(1).optional(),
});

export type Candidate = z.infer<typeof CandidateSchema>;

export interface ParsedCandidates {
  candidates: Candidate[];
  /** Items that weren't valid candidates (missing fields, wrong types). */
  invalid: number;
}

/**
 * Parses a model's answer. Tolerates code fences and text around the array, because chat
 * answers often have them; validates each item on its own so one bad row doesn't sink the file.
 * Songs without a `year` get [defaultYear], the year the prompt asked about.
 */
export function parseCandidates(text: string, defaultYear?: number): ParsedCandidates {
  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start === -1 || end <= start) throw new Error("no JSON array found in the answer");

  const items: unknown = JSON.parse(text.slice(start, end + 1));
  if (!Array.isArray(items)) throw new Error("answer is not a JSON array");

  const candidates: Candidate[] = [];
  let invalid = 0;
  for (const item of items) {
    const withYear =
      defaultYear !== undefined && typeof item === "object" && item !== null && !("year" in item)
        ? { ...item, year: defaultYear }
        : item;
    const result = CandidateSchema.safeParse(withYear);
    if (result.success) candidates.push(result.data);
    else invalid++;
  }
  return { candidates, invalid };
}

/** `2004-all.json`, `2003-chart_hits-2.json` -> 2004, 2003. */
export function yearOfFile(fileName: string): number | undefined {
  const match = /^(\d{4})-/.exec(fileName);
  return match ? Number(match[1]) : undefined;
}
