import { mkdir, readdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { DOWNLOAD_PREFIX, paths } from "../config.ts";
import { joined, normalize } from "../normalize.ts";
import { loadReviewAnswers } from "../review-answers.ts";
import type { VerifiedFile, VerifiedRow } from "./verify.ts";

const BATCH_SIZE = 40;

export interface ReviewItem {
  /** `chat/2003-all.json#12`: which verified row this is, so answers can be matched back. */
  id: string;
  row: VerifiedRow;
}

function describe({ id, row }: ReviewItem): string {
  const { candidate, result } = row;
  const track = result.track;
  const found = track
    ? `Apple Music has: "${track.trackName}" on album "${track.collectionName}" (released ${track.releaseDate.slice(0, 10)})`
    : "Apple Music has no song with this title";
  return `${id} | "${candidate.title}" from the film "${candidate.film}" (${candidate.year}) | ${found} | flagged: ${result.reasons.join(", ")}`;
}

/** Prompt asking the chat to settle the songs the iTunes check couldn't confirm on its own. */
export function buildReviewPrompt(items: ReviewItem[], downloadFileName: string): string {
  return `You are reviewing songs for a Bollywood "guess the song from a 2-second clip" game.
An automatic check against Apple Music (India) couldn't confirm the songs below. For each one,
decide whether the song is real and from that film and year, and whether the Apple Music track
listed is the original film version. You may search the web.

What the flags mean:
- film_mismatch: the Apple Music album isn't that film (could be a single, a compilation, or a different song)
- not_original: the Apple Music track looks like a remix, cover, instrumental or other version
- year_mismatch: the Apple Music release date is well before the film's year
- no_preview: Apple Music has no preview clip
- no_title_match: no song with this title was found

Songs (id | song and film | what Apple Music has | flags):
${items.map(describe).join("\n")}

For each song, return one compact JSON object:
{"id", "real": true/false, "correct_film": "...", "correct_year": 2003,
 "apple_track_is_original": true/false/null (null = can't tell),
 "verdict": "keep" | "fix" | "drop", "note": "one short reason"}
- keep: the song and film are right and the Apple Music track is the original film version
- fix: the song is real but the film or year given is wrong (put the right ones in correct_film / correct_year)
- drop: not real, or no original version on Apple Music

Output: create a downloadable file named exactly "${downloadFileName}" containing only the JSON
array, one object per line. Don't show the list in the chat, just give the file.
If you can't create files, reply with the JSON array in a single code block instead.`;
}

/** Same song from the same film, however it was spelled. Its review decision applies to every copy. */
export function songKey(row: VerifiedRow): string {
  return `${joined(normalize(row.candidate.title))}|${joined(normalize(row.candidate.film))}`;
}

/**
 * Writes review prompts for every `check` and `missing` song, in batches, to data/review/prompts/.
 * A song suggested in several runs is reviewed once, and not at all if another run found it.
 */
export async function writeReviewPrompts(): Promise<void> {
  const rows: ReviewItem[] = [];
  for (const source of await readdir(paths.verified).catch(() => [] as string[])) {
    for (const file of (await readdir(path.join(paths.verified, source))).filter((f) => f.endsWith(".json")).sort()) {
      const verified = JSON.parse(await readFile(path.join(paths.verified, source, file), "utf8")) as VerifiedFile;
      verified.rows.forEach((row, i) => rows.push({ id: `${source}/${file}#${row.index ?? i}`, row }));
    }
  }

  // Rows already answered in an earlier review round aren't asked about again.
  const answered = await loadReviewAnswers();
  const foundSomewhere = new Set(rows.filter(({ row }) => row.result.status === "found").map(({ row }) => songKey(row)));
  const seen = new Set<string>();
  const items = rows.filter(({ id, row }) => {
    const key = songKey(row);
    if (row.result.status === "found" || answered.has(id) || foundSomewhere.has(key) || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  if (items.length === 0) {
    console.log("Nothing to review: every verified song was found.");
    return;
  }

  const promptDir = path.join(paths.review, "prompts");
  await rm(promptDir, { recursive: true, force: true });
  await mkdir(promptDir, { recursive: true });
  await mkdir(path.join(paths.review, "answers"), { recursive: true });

  const batches = Math.ceil(items.length / BATCH_SIZE);
  for (let b = 0; b < batches; b++) {
    const name = `review-${String(b + 1).padStart(3, "0")}`;
    const batch = items.slice(b * BATCH_SIZE, (b + 1) * BATCH_SIZE);
    await writeFile(path.join(promptDir, `${name}.txt`), buildReviewPrompt(batch, `${DOWNLOAD_PREFIX}-${name}.json`));
  }
  console.log(`${items.length} songs need a look. Wrote ${batches} review prompt(s) to data/review/prompts/.`);
  console.log("Paste each into the chat and save the file it gives you into data/review/answers/.");
}
