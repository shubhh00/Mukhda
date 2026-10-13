# Song curation

Builds Mukhda's answer pool and search index: chat-model candidates → iTunes verification →
review → search index → (later) tiering and publishing. Runs on Node 24 with no build step
(`.ts` files run directly). No API keys: candidates come from a chat app, pasted by hand.

```bash
cd tools/curation
npm install
```

## Collecting candidates

1. `npm run prompts -- --years 2000-2026` writes one prompt per year to `data/prompts/`
   (`--per-angle` for four smaller prompts per year). The current year's prompt works from web
   search, since it's newer than the model's memory; `--search` forces that for any year.
2. Paste each prompt into the chat and download the file it gives you, named like
   `mukhda-2004-all.json`. (If it replies with a code block instead, save the block under the
   name the prompt asked for.)
3. Save downloads straight into `data/candidates/`, or run `npm run import` to collect them from
   your Downloads folder (`CURATION_DOWNLOADS_DIR` in `.env` if it's elsewhere).
4. `npm run verify` files the downloads into `data/candidates/chat/`, then checks every song
   against the iTunes Search API. Apple allows about 20 calls a minute, so big batches take a
   while; responses are cached, so re-runs are quick.
5. `npm run report` counts usable songs per year, flags thin years worth a re-run, and saves
   `data/reports/curation.md`.

Each candidate ends up as:

| Status | Meaning |
|---|---|
| found | Real song, right film, original version, has a preview, released no earlier than the film |
| check | Title exists on iTunes but something's off (film, version, preview or year) |
| missing | No song with that title: likely invented or badly mis-titled |

## Review and search index

- `npm run review` writes prompts for the `check` and `missing` songs (each once) to
  `data/review/prompts/`; save the chat's answers into `data/review/answers/`.
- `npm run index` builds the app's search index: every verified song plus the rest of each
  film's soundtrack from iTunes, without remixes or instrumentals. Song ids are Apple track ids
  (`s_<trackId>`). It writes `data/index/search_index.json` and the next
  `assets/search_index/v<N>.json` in the app.

## Refreshing

Re-running a prompt never replaces an earlier answer: a second answer is saved as
`2004-all-2.json`, and songs are merged by iTunes track. Downloading the same answer twice is
ignored.

- **Every 3 months:** run the current year's prompt again (`-- --years 2026`) for new films.
- **Missed films:** re-run any year whose count looks thin.

`data/` holds generated working files and the song lists, and is never committed: the repo is
public and the lists are a draft of the answer pool.

## Checks

```bash
npm run typecheck
npm test
```

`src/normalize.ts` mirrors the app's `SearchNormalizer`; change both together.
