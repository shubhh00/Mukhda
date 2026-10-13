import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { type Candidate, parseCandidates, yearOfFile } from "../candidates.ts";
import { paths } from "../config.ts";
import { importDownloads } from "./import.ts";
import { ItunesClient } from "../itunes.ts";
import { type MatchResult, baseTitle, better, evaluate } from "../match.ts";
import { type ReviewAnswer, applyReview, correctedCandidate, loadReviewAnswers } from "../review-answers.ts";

export interface VerifiedRow {
  candidate: Candidate;
  result: MatchResult;
}

export interface VerifiedFile {
  source: string;
  file: string;
  /** Set when the answer couldn't be read as a JSON array at all. */
  parseError?: string;
  invalid: number;
  rows: VerifiedRow[];
}

/** Checks every candidate file of every source against iTunes and writes data/verified/. */
export async function verifyAll(): Promise<void> {
  const itunes = new ItunesClient(paths.itunesCache);
  const answers = await loadReviewAnswers();
  // Chat downloads saved in data/candidates/ or one of its folders under their download name
  // (mukhda-2004-all.json) are filed into data/candidates/chat/ first.
  await importDownloads(paths.candidates, paths.candidates, { quietWhenEmpty: true });
  const sourceDirs = async () =>
    (await readdir(paths.candidates, { withFileTypes: true }).catch(() => []))
      .filter((entry) => entry.isDirectory())
      .map((entry) => entry.name);
  for (const dir of await sourceDirs()) {
    await importDownloads(path.join(paths.candidates, dir), paths.candidates, { quietWhenEmpty: true });
  }
  const sources = await sourceDirs();
  if (sources.length === 0) {
    console.log("No candidates yet. Download chat answers into data/candidates/ (see `npm run prompts`).");
    return;
  }

  for (const source of sources) {
    const files = (await readdir(path.join(paths.candidates, source))).filter((f) => f.endsWith(".json"));
    for (const file of files) {
      const verified = await verifyFile(itunes, source, file, answers);
      const outDir = path.join(paths.verified, source);
      await mkdir(outDir, { recursive: true });
      await writeFile(path.join(outDir, file), JSON.stringify(verified, null, 2));
    }
  }
  console.log("\nDone. Run `npm run report` to compare sources.");
}

async function verifyFile(
  itunes: ItunesClient,
  source: string,
  file: string,
  answers: Map<string, ReviewAnswer>,
): Promise<VerifiedFile> {
  const text = await readFile(path.join(paths.candidates, source, file), "utf8");
  let parsed;
  try {
    parsed = parseCandidates(text, yearOfFile(file));
  } catch (error) {
    console.log(`${source}/${file}: unreadable (${(error as Error).message})`);
    return { source, file, parseError: (error as Error).message, invalid: 0, rows: [] };
  }

  const rows: VerifiedRow[] = [];
  let skipped = 0;
  for (const [i, candidate] of parsed.candidates.entries()) {
    process.stdout.write(`\r${source}/${file}: ${i + 1}/${parsed.candidates.length}   `);
    try {
      const answer = answers.get(`${source}/${file}#${i}`);
      const result = applyReview(await verifyCandidate(itunes, candidate), answer);
      rows.push({ candidate: result.reviewed ? correctedCandidate(candidate, answer!) : candidate, result });
    } catch (error) {
      // One song Apple won't answer for mustn't stop the run. It isn't cached, so the next run retries it.
      skipped++;
      console.log(`\n  skipped "${candidate.title}" (${(error as Error).message}); retried next run`);
    }
  }
  const found = rows.filter((r) => r.result.status === "found").length;
  const notes = [parsed.invalid ? `${parsed.invalid} invalid` : "", skipped ? `${skipped} skipped` : ""].filter(Boolean);
  console.log(`\r${source}/${file}: ${found}/${rows.length} found${notes.length ? `, ${notes.join(", ")}` : ""}`);
  return { source, file, invalid: parsed.invalid, rows };
}

/**
 * Searches title + film, then title + lead singer, then the title alone, stopping at the first
 * clean match. iTunes often ranks a remix or a cover above the soundtrack version, so a
 * `check` result is worth a second search too.
 */
async function verifyCandidate(itunes: ItunesClient, candidate: Candidate): Promise<MatchResult> {
  const title = baseTitle(candidate.title);
  const singer = candidate.singers[0];
  const queries = [`${title} ${candidate.film}`, ...(singer ? [`${title} ${singer}`] : []), title];

  let best: MatchResult | undefined;
  for (const query of queries) {
    const result = evaluate(candidate, await itunes.search(query));
    best = best ? better(best, result) : result;
    if (best.status === "found") break;
  }
  return best!;
}
