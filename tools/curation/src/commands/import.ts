import { copyFile, mkdir, readdir, readFile, rename, unlink } from "node:fs/promises";
import path from "node:path";
import { parseCandidates } from "../candidates.ts";
import { CHAT_FOLDER, DOWNLOADS_DIR, DOWNLOAD_PREFIX, PROMPT_KINDS, type PromptKind, candidateFileName, paths } from "../config.ts";

export interface DownloadName {
  year: number;
  kind: PromptKind;
}

// Browsers add " (1)", "(2)" ... for repeat downloads; chats sometimes save as .txt.
const DOWNLOAD = new RegExp(String.raw`^${DOWNLOAD_PREFIX}-(\d{4})-([a-z_]+?)(?:\s?\(\d+\))?\.(json|txt)$`, "i");

/** Reads `mukhda-2004-all (1).json` as 2004 / all; anything else is null. */
export function parseDownloadName(fileName: string): DownloadName | null {
  const match = DOWNLOAD.exec(fileName);
  if (!match) return null;
  const kind = match[2]!.toLowerCase() as PromptKind;
  if (!PROMPT_KINDS.includes(kind)) return null;
  return { year: Number(match[1]), kind };
}

/**
 * Where a new answer goes: `2003-chart_hits.json`, or `2003-chart_hits-2.json`, `-3` ... when
 * earlier runs exist, so re-running a prompt adds songs instead of replacing them. Returns null
 * when the exact same answer is already there (the same file downloaded twice).
 */
async function destinationFor(dir: string, name: DownloadName, text: string): Promise<string | null> {
  const base = candidateFileName(name.year, name.kind).replace(/\.json$/, "");
  const runFile = new RegExp(`^${base}(-\\d+)?\\.json$`);
  const existing = (await readdir(dir).catch(() => [] as string[])).filter((f) => runFile.test(f));

  for (const file of existing) {
    if ((await readFile(path.join(dir, file), "utf8")) === text) return null;
  }
  if (!existing.includes(`${base}.json`)) return path.join(dir, `${base}.json`);
  for (let run = 2; ; run++) {
    if (!existing.includes(`${base}-${run}.json`)) return path.join(dir, `${base}-${run}.json`);
  }
}

async function move(from: string, to: string): Promise<void> {
  try {
    await rename(from, to);
  } catch {
    // Downloads may be on another drive, where rename can't move files.
    await copyFile(from, to);
    await unlink(from);
  }
}

/** Files chat downloads (`mukhda-2004-all.json`) into data/candidates/chat/. */
export async function importDownloads(
  downloadsDir: string = DOWNLOADS_DIR,
  candidatesDir: string = paths.candidates,
  { quietWhenEmpty = false } = {},
): Promise<void> {
  const entries = await readdir(downloadsDir).catch(() => null);
  if (entries === null) {
    console.log(`Can't read ${downloadsDir}. Set CURATION_DOWNLOADS_DIR in .env to your downloads folder.`);
    return;
  }

  const downloads = entries.flatMap((file) => {
    const name = parseDownloadName(file);
    return name ? [{ file, name }] : [];
  });
  if (downloads.length === 0) {
    if (!quietWhenEmpty) {
      console.log(`No chat downloads found in ${downloadsDir} (looking for names like ${DOWNLOAD_PREFIX}-2004-all.json).`);
    }
    return;
  }

  for (const { file, name } of downloads) {
    const from = path.join(downloadsDir, file);
    try {
      const text = await readFile(from, "utf8");
      const { candidates, invalid } = parseCandidates(text, name.year);
      const dir = path.join(candidatesDir, CHAT_FOLDER);
      await mkdir(dir, { recursive: true });

      const to = await destinationFor(dir, name, text);
      if (to === null) {
        await unlink(from);
        console.log(`duplicate ${file}: same answer already imported, removed`);
        continue;
      }
      await move(from, to);
      console.log(`imported  ${CHAT_FOLDER}/${path.basename(to)}  (${candidates.length} songs${invalid ? `, ${invalid} invalid` : ""})`);
    } catch (error) {
      console.log(`skipped   ${file}: ${(error as Error).message}. Left where it was.`);
    }
  }
}
