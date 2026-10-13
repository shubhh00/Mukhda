import { mkdir, readdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { paths } from "../config.ts";
import type { Reason } from "../match.ts";
import { joined, normalize } from "../normalize.ts";
import type { VerifiedFile } from "./verify.ts";

export interface SourceSummary {
  source: string;
  files: number;
  unreadableFiles: number;
  candidates: number;
  invalid: number;
  found: number;
  check: number;
  missing: number;
  /** Same title + film suggested more than once by this source. */
  duplicates: number;
  /** Distinct iTunes tracks found: the usable yield. */
  uniqueFound: number;
  checkReasons: Partial<Record<Reason, number>>;
  /** Average stated confidence of found vs missing rows (pilot answers only); a well-calibrated model separates them. */
  meanConfidenceFound: number | null;
  meanConfidenceMissing: number | null;
  /** Found rows by recognisability 1..5, a first look at tier spread. */
  foundByRecognisability: number[];
  /** Found rate per year, to spot models that get worse on older years. */
  foundRateByYear: Record<number, number>;
  foundTrackIds: Set<number>;
}

const mean = (xs: (number | undefined)[]) => {
  const known = xs.filter((x): x is number => x !== undefined);
  return known.length ? known.reduce((a, b) => a + b, 0) / known.length : null;
};

export function summarize(files: VerifiedFile[]): SourceSummary[] {
  const bySource = Map.groupBy(files, (f) => f.source);
  return [...bySource.entries()].map(([source, sourceFiles]) => {
    const rows = sourceFiles.flatMap((f) => f.rows);
    const found = rows.filter((r) => r.result.status === "found");
    const missing = rows.filter((r) => r.result.status === "missing");

    const seen = new Set<string>();
    let duplicates = 0;
    for (const { candidate } of rows) {
      const key = `${joined(normalize(candidate.title))}|${joined(normalize(candidate.film))}`;
      if (seen.has(key)) duplicates++;
      else seen.add(key);
    }

    const checkReasons: Partial<Record<Reason, number>> = {};
    for (const r of rows) {
      if (r.result.status !== "check") continue;
      for (const reason of r.result.reasons) checkReasons[reason] = (checkReasons[reason] ?? 0) + 1;
    }

    const foundByRecognisability = [0, 0, 0, 0, 0];
    for (const r of found) {
      const level = Math.round(r.candidate.recognisability);
      foundByRecognisability[level - 1] = (foundByRecognisability[level - 1] ?? 0) + 1;
    }

    const foundRateByYear: Record<number, number> = {};
    for (const [year, yearRows] of Map.groupBy(rows, (r) => r.candidate.year)) {
      foundRateByYear[year] = yearRows.filter((r) => r.result.status === "found").length / yearRows.length;
    }

    const foundTrackIds = new Set(found.flatMap((r) => (r.result.track ? [r.result.track.trackId] : [])));

    return {
      source,
      files: sourceFiles.length,
      unreadableFiles: sourceFiles.filter((f) => f.parseError).length,
      candidates: rows.length,
      invalid: sourceFiles.reduce((n, f) => n + f.invalid, 0),
      found: found.length,
      check: rows.length - found.length - missing.length,
      missing: missing.length,
      duplicates,
      uniqueFound: foundTrackIds.size,
      checkReasons,
      meanConfidenceFound: mean(found.map((r) => r.candidate.confidence)),
      meanConfidenceMissing: mean(missing.map((r) => r.candidate.confidence)),
      foundByRecognisability,
      foundRateByYear,
      foundTrackIds,
    };
  });
}

const pct = (n: number, d: number) => (d ? `${Math.round((100 * n) / d)}%` : "–");
const num = (x: number | null) => (x === null ? "–" : x.toFixed(2));

export interface YearCount {
  year: number;
  /** Distinct usable iTunes tracks for the year, merged across every source and run. */
  usable: number;
  thin: boolean;
}

/** COVID closed cinemas, so these years really had fewer releases; a low count there is expected. */
const SHORT_RELEASE_YEARS = new Set([2020, 2021]);

/** Usable songs per release year. A year under 75% of the median is flagged as thin: worth a re-run. */
export function songsPerYear(files: VerifiedFile[]): YearCount[] {
  const byYear = new Map<number, Set<number>>();
  for (const { rows } of files) {
    for (const { candidate, result } of rows) {
      if (result.status !== "found" || !result.track) continue;
      const ids = byYear.get(candidate.year) ?? new Set<number>();
      ids.add(result.track.trackId);
      byYear.set(candidate.year, ids);
    }
  }
  const counts = [...byYear].map(([year, ids]) => ({ year, usable: ids.size })).sort((a, b) => a.year - b.year);
  const sorted = counts.map((c) => c.usable).sort((a, b) => a - b);
  const median = sorted.length ? sorted[Math.floor(sorted.length / 2)]! : 0;
  return counts.map((c) => ({ ...c, thin: c.usable < median * 0.75 }));
}

function renderYears(years: YearCount[]): string[] {
  const lines = ["", "## Usable songs per year", "", "| Year | Usable songs | |", "|---|---|---|"];
  for (const { year, usable, thin } of years) {
    const note = !thin ? "" : SHORT_RELEASE_YEARS.has(year) ? "thin (COVID year, fewer releases)" : "**thin: consider a re-run**";
    lines.push(`| ${year} | ${usable} | ${note} |`);
  }
  const total = years.reduce((n, y) => n + y.usable, 0);
  lines.push("", `Total: ${total} usable songs across ${years.length} years.`);
  return lines;
}

export function renderMarkdown(summaries: SourceSummary[], years: YearCount[] = []): string {
  const lines = ["# Curation report", "", "## By source", ""];
  lines.push("| | " + summaries.map((s) => s.source).join(" | ") + " |");
  lines.push("|---|" + summaries.map(() => "---").join("|") + "|");
  const row = (label: string, cell: (s: SourceSummary) => string) =>
    lines.push(`| ${label} | ${summaries.map(cell).join(" | ")} |`);

  row("Files (unreadable)", (s) => `${s.files} (${s.unreadableFiles})`);
  row("Candidates (invalid rows)", (s) => `${s.candidates} (${s.invalid})`);
  row("**Found** (real, right film, original, preview)", (s) => `**${s.found}** (${pct(s.found, s.candidates)})`);
  row("Check (needs a look)", (s) => `${s.check} (${pct(s.check, s.candidates)})`);
  row("Missing (likely invented or mis-titled)", (s) => `${s.missing} (${pct(s.missing, s.candidates)})`);
  row("Duplicates", (s) => String(s.duplicates));
  row("**Unique songs found**", (s) => `**${s.uniqueFound}**`);
  row("Confidence: found / missing", (s) => `${num(s.meanConfidenceFound)} / ${num(s.meanConfidenceMissing)}`);
  row("Found by recognisability 1→5", (s) => s.foundByRecognisability.join(" · "));
  row("Found rate by year", (s) =>
    Object.entries(s.foundRateByYear).map(([y, r]) => `${y}: ${pct(r, 1)}`).join(", "));
  row("Check reasons", (s) =>
    Object.entries(s.checkReasons).map(([k, v]) => `${k} ${v}`).join(", ") || "–");

  lines.push("", "## Overlap of found songs", "");
  const all = new Set(summaries.flatMap((s) => [...s.foundTrackIds]));
  for (const [i, a] of summaries.entries()) {
    for (const b of summaries.slice(i + 1)) {
      const shared = [...a.foundTrackIds].filter((id) => b.foundTrackIds.has(id)).length;
      lines.push(`- ${a.source} ∩ ${b.source}: ${shared}`);
    }
  }
  lines.push(`- Union across all sources: ${all.size} unique songs`);
  if (years.length > 0) lines.push(...renderYears(years));
  return lines.join("\n") + "\n";
}

export async function writeReport(): Promise<void> {
  const files: VerifiedFile[] = [];
  for (const source of await readdir(paths.verified).catch(() => [] as string[])) {
    for (const file of await readdir(path.join(paths.verified, source))) {
      files.push(JSON.parse(await readFile(path.join(paths.verified, source, file), "utf8")) as VerifiedFile);
    }
  }
  if (files.length === 0) {
    console.log("Nothing verified yet. Run `npm run verify` first.");
    return;
  }

  const markdown = renderMarkdown(summarize(files), songsPerYear(files));
  await mkdir(paths.reports, { recursive: true });
  const out = path.join(paths.reports, "curation.md");
  await writeFile(out, markdown);
  console.log(markdown);
  console.log(`Saved to ${path.relative(process.cwd(), out)}`);
}
