import { parseArgs } from "node:util";
import { importDownloads } from "./commands/import.ts";
import { writeSearchIndex } from "./commands/index.ts";
import { writePrompts } from "./commands/prompts.ts";
import { writeReport } from "./commands/report.ts";
import { writeReviewPrompts } from "./commands/review.ts";
import { verifyAll } from "./commands/verify.ts";
import { PILOT_YEARS, parseYears } from "./config.ts";

const USAGE = "Usage: npm run <prompts|import|verify|report|review|index> [-- --years 2003,2013 | 2000-2026] [-- --per-angle] [-- --search]";

async function main(): Promise<void> {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
      years: { type: "string" },
      "per-angle": { type: "boolean", default: false },
      search: { type: "boolean", default: false },
    },
  });
  const years = values.years ? parseYears(values.years) : [...PILOT_YEARS];

  switch (positionals[0]) {
    case "prompts":
      return writePrompts(years, { perAngle: values["per-angle"], search: values.search });
    case "import":
      return importDownloads();
    case "verify":
      return verifyAll();
    case "report":
      return writeReport();
    case "review":
      return writeReviewPrompts();
    case "index":
      return writeSearchIndex();
    default:
      console.log(USAGE);
      process.exitCode = 1;
  }
}

try {
  await main();
} catch (error) {
  console.error(`Error: ${(error as Error).message}`);
  process.exitCode = 1;
}
