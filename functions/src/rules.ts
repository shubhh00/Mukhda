import { LAST_STEP, MAX_TRIES, type RoundStatus, type SongStatsDoc, type TryResult, type TryType } from "./model.ts";

/** Same song wins; another song from the same film is "right film", still a miss; a skip is a skip. */
export function judge(type: TryType, answer: { songId: string; filmId: string }, guess: { songId: string; filmId: string } | null): TryResult {
  if (type === "SKIP" || !guess) return "SKIPPED";
  if (guess.songId === answer.songId) return "CORRECT";
  return guess.filmId === answer.filmId ? "RIGHT_FILM" : "WRONG";
}

/** The round after a try: won on a correct one, lost after the fifth miss, otherwise still open. */
export function statusAfter(results: TryResult[]): { status: RoundStatus; solvedAtStep: number | null } {
  const correctAt = results.indexOf("CORRECT");
  if (correctAt !== -1) return { status: "WON", solvedAtStep: correctAt };
  return { status: results.length >= MAX_TRIES ? "LOST" : "OPEN", solvedAtStep: null };
}

/** The clip step the player is on: one longer clip per try used, up to the full preview. */
export const currentStep = (triesUsed: number): number => Math.min(triesUsed, LAST_STEP);

/**
 * "Top X% today": the share of the day's finished players who did at least as well as one who won
 * at [solvedAtStep] (null for a loss), out of everyone finished so far, this player included.
 * Rounded up, so the best player is "Top 1%" rather than "Top 0%". Null before anyone's finished.
 */
export function topPercent(stats: SongStatsDoc | undefined, solvedAtStep: number | null): number | null {
  if (!stats || stats.played <= 0) return null;
  const atLeastAsGood =
    solvedAtStep === null
      ? stats.played
      : stats.byStep.slice(0, solvedAtStep + 1).reduce((sum, n) => sum + n, 0);
  return Math.max(1, Math.min(100, Math.ceil((100 * atLeastAsGood) / stats.played)));
}

/** songStats after a round ends, as a won round at [solvedAtStep] or a lost one (null). */
export function statsAfterRound(stats: SongStatsDoc | undefined, solvedAtStep: number | null): SongStatsDoc {
  const base: SongStatsDoc = stats ?? { played: 0, won: 0, lost: 0, byStep: new Array(LAST_STEP + 1).fill(0) };
  const byStep = [...base.byStep];
  if (solvedAtStep !== null) byStep[solvedAtStep] = (byStep[solvedAtStep] ?? 0) + 1;
  return {
    ...base,
    played: base.played + 1,
    won: base.won + (solvedAtStep !== null ? 1 : 0),
    lost: base.lost + (solvedAtStep === null ? 1 : 0),
    byStep,
  };
}
