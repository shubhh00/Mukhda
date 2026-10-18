import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { z } from "zod";
import { paths } from "./config.ts";
import type { Candidate } from "./candidates.ts";
import type { MatchResult } from "./match.ts";

/** One answer from a review prompt, matched back to its verified row by id. */
export const ReviewAnswerSchema = z.object({
  id: z.string(),
  real: z.boolean().nullable().optional(),
  correct_film: z.string().nullable().optional(),
  correct_year: z.coerce.number().nullable().optional(),
  apple_track_is_original: z.boolean().nullable().optional(),
  verdict: z.enum(["keep", "fix", "drop"]),
  note: z.string().optional(),
});

export type ReviewAnswer = z.infer<typeof ReviewAnswerSchema>;

/**
 * Every saved review answer, by row id (`chat/2004-all.json#12`). The answers folder is the record
 * of decisions: verify applies it on every run, and review never asks about these rows again.
 */
export async function loadReviewAnswers(dir: string = path.join(paths.review, "answers")): Promise<Map<string, ReviewAnswer>> {
  const answers = new Map<string, ReviewAnswer>();
  for (const file of await readdir(dir).catch(() => [] as string[])) {
    const text = await readFile(path.join(dir, file), "utf8");
    const start = text.indexOf("[");
    const end = text.lastIndexOf("]");
    if (start === -1 || end <= start) continue;
    for (const item of JSON.parse(text.slice(start, end + 1)) as unknown[]) {
      const parsed = ReviewAnswerSchema.safeParse(item);
      if (parsed.success) answers.set(parsed.data.id, parsed.data);
    }
  }
  return answers;
}

/**
 * A reviewed "keep" turns a flagged row into a usable song, but only when the reviewer confirmed
 * the Apple track is the original film version and Apple has a preview clip to play.
 * "fix" and "drop" leave the row as it is: it stays out of the pool.
 */
export function applyReview(result: MatchResult, answer: ReviewAnswer | undefined): MatchResult {
  if (answer?.verdict !== "keep" || answer.apple_track_is_original !== true) return result;
  if (result.status === "found" || !result.track?.previewUrl) return result;
  return { status: "found", reasons: [], track: result.track, reviewed: true };
}

/** A kept song with the film and year the reviewer corrected, so it's filed under the right film. */
export function correctedCandidate(candidate: Candidate, answer: ReviewAnswer): Candidate {
  return {
    ...candidate,
    film: answer.correct_film?.trim() || candidate.film,
    year: answer.correct_year ?? candidate.year,
  };
}
