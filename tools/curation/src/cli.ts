import { parseArgs } from "node:util";
import { importDownloads } from "./commands/import.ts";
import { writeSearchIndex } from "./commands/index.ts";
import { writePrompts } from "./commands/prompts.ts";
import { publish } from "./commands/publish.ts";
import { writeReport } from "./commands/report.ts";
import { writeReviewPrompts } from "./commands/review.ts";
import { writeTiers } from "./commands/tier.ts";
import { fetchLastfm } from "./commands/lastfm.ts";
import { fetchYoutube } from "./commands/youtube.ts";
import { verifyAll } from "./commands/verify.ts";
import { PILOT_YEARS, parseYears } from "./config.ts";

const USAGE = "Usage: npm run <prompts|import|verify|report|review|index|youtube|lastfm|tier|publish> [-- --years 2003,2013 | 2000-2026] [-- --per-angle] [-- --search]";

async function main(): Promise<void> {
  const { positionals, values } = parseArgs({
    allowPositionals: true,
    options: {
      years: { type: "string" },
      "per-angle": { type: "boolean", default: false },
      search: { type: "boolean", default: false },
      days: { type: "string", default: "60" },
      from: { type: "string" },
      launch: { type: "string" },
      seed: { type: "string", default: "1" },
      production: { type: "boolean", default: false },
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
    case "tier":
      return writeTiers();
    case "publish":
      return publish({
        days: Number.parseInt(values.days, 10),
        from: values.from,
        launch: values.launch,
        seed: Number.parseInt(values.seed, 10),
        production: values.production,
      });
    case "youtube":
      return fetchYoutube();
    case "lastfm":
      return fetchLastfm();
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
