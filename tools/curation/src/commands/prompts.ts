import { mkdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { ALL, ANGLE_KEYS, PILOT_YEARS, type PromptKind, downloadFileName, paths } from "../config.ts";
import { buildPrompt } from "../prompt.ts";

/**
 * Writes data/prompts/<year>-<kind>.txt: one prompt per year, or one per year and angle with
 * `perAngle`. Each asks the chat for a download named `mukhda-<year>-<kind>.json`.
 * The current year always uses the web-search prompt (it's newer than the model's memory);
 * `search` forces it for every year.
 */
export async function writePrompts(
  years: readonly number[] = PILOT_YEARS,
  { perAngle = false, search = false } = {},
): Promise<void> {
  const currentYear = new Date().getFullYear();
  const kinds: readonly PromptKind[] = perAngle ? ANGLE_KEYS : [ALL];
  await rm(paths.prompts, { recursive: true, force: true });
  await mkdir(paths.prompts, { recursive: true });
  for (const year of years) {
    for (const kind of kinds) {
      const prompt = buildPrompt(year, kind, downloadFileName(year, kind), { search: search || year >= currentYear });
      await writeFile(path.join(paths.prompts, `${year}-${kind}.txt`), prompt);
    }
  }

  console.log(`Wrote ${years.length * kinds.length} prompts to data/prompts/.`);
  const searching = search ? years : years.filter((y) => y >= currentYear);
  if (searching.length > 0) console.log(`Web-search prompts (not from memory): ${searching.join(", ")}`);
  console.log("\n1. Paste each prompt into the chat and download the file it gives you.");
  console.log("2. Save it into data/candidates/ (or run `npm run import` to collect it from Downloads).");
  console.log("If the chat replies with a code block instead of a file, save it yourself as the name the prompt asked for.");
}
