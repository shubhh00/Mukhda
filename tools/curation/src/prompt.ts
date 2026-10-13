import { ALL, ANGLES, ANGLE_KEYS, type PromptKind } from "./config.ts";

const SONGS_PER_ANGLE = 40;

export interface PromptOptions {
  /**
   * Search the web instead of answering from memory. Needed for the current year, which is newer
   * than what the model knows; also useful for a recent year whose answer came back thin.
   */
  search?: boolean;
}

function scope(year: number, kind: PromptKind, search: boolean): string {
  const films = search
    ? `Hindi feature films released in India in ${year} so far`
    : `Hindi feature films released in India in ${year}`;
  if (kind !== ALL) {
    return `List up to ${SONGS_PER_ANGLE} songs from ${films}.
Focus: ${ANGLES[kind]}`;
  }
  const groups = ANGLE_KEYS.map((angle, i) => `${i + 1}. ${ANGLES[angle]}`).join("\n");
  return `List up to ${SONGS_PER_ANGLE * ANGLE_KEYS.length} songs from ${films},
roughly balanced across these groups (each song in one group only; exact counts don't matter):
${groups}`;
}

function method(year: number, search: boolean): string {
  if (!search) {
    return `Answer from memory. Don't search the web, look anything up or double-check your list:
we verify every song against Apple Music afterwards, so speed matters more than polish.`;
  }
  return `${year} is more recent than your training data, so search the web instead of relying on memory:
1. Find the Hindi films released in India in ${year} so far (box-office and release lists,
   Wikipedia's "List of Hindi films of ${year}", streaming premieres).
2. For each film, look up its soundtrack (song list, composer, singers).
3. Pick songs from those soundtracks for the groups above. Leave out films whose songs
   you can't find.
Don't re-check songs once you've found them in a soundtrack listing: we verify every song
against Apple Music afterwards.`;
}

/**
 * The candidate prompt for a chat app. Built for speed: by default the model answers from memory
 * without self-checking (the iTunes step verifies everything); for years it can't know, it works
 * from web search instead. Either way it writes only the fields the tool uses, as compact JSON,
 * straight into a downloadable file. `kind` is one angle, or `all` for the whole year.
 */
export function buildPrompt(year: number, kind: PromptKind, downloadFileName: string, options: PromptOptions = {}): string {
  const search = options.search ?? false;
  const groupField = kind === ALL ? `, "group": 1-${ANGLE_KEYS.length}` : "";
  return `You are helping build the song list for a Bollywood "guess the song from a 2-second clip" game.

${scope(year, kind, search)}

${method(year, search)}

Rules:
- Only songs from the soundtrack of a Hindi feature film released in ${year}.
  No non-film singles, indie pop, web series or regional-language films.
- Official film and song titles, romanised Hindi (not translated).
- Original film versions only, no remixes or reprises. If the well-known version is a
  "recreated" version of an older song, include it with "is_recreation": true.
- If you're unsure a song exists or which film it's from, leave it out.

Each song is one compact JSON object with these fields only:
{"title", "film", "composer" (best guess, or ""), "singers": [main singers], "recognisability": 1-5${groupField}}
recognisability = how likely an average Hindi-film listener aged 18-40 is to name it from a
2-second clip (5 = almost everyone). Add "is_recreation": true only when it applies.

Output: create a downloadable file named exactly "${downloadFileName}" containing only the JSON
array, one song per line, no indentation. Don't show the list in the chat, just give the file.
If you can't create files, reply with the JSON array in a single code block instead.`;
}
