import { readFile } from "node:fs/promises";
import path from "node:path";
import { initializeApp } from "firebase-admin/app";
import { type Firestore, Timestamp, getFirestore } from "firebase-admin/firestore";
import { ROOT, paths } from "../config.ts";
import { loadPool } from "../pool.ts";
import { TIERS, type Tier } from "../popularity.ts";
import { type SchedulableSong, type ScheduledDay, addDays, schedule } from "../schedule.ts";
import type { IndexSong } from "../search-index.ts";
import type { TieredSong } from "./tier.ts";
import { readVocalStarts } from "./vocals.ts";

export interface PublishOptions {
  /** How many days of shows to add after the last scheduled one. */
  days: number;
  /** The first day to schedule, if not the day after the last one (or today). */
  from?: string;
  /** Show No. 1's date. Needed on the first publish; later ones read it off the schedule. */
  launch?: string;
  seed: number;
  /** Write to the real project instead of the local emulator. */
  production: boolean;
  /** Only rewrite the songs (say, new clip starts), not the catalogue or the schedule. */
  songsOnly?: boolean;
}

/** Firestore takes at most 500 writes in a batch. */
const BATCH_SIZE = 400;

const todayIst = () => new Date(Date.now() + 5.5 * 60 * 60 * 1000).toISOString().slice(0, 10);

/** The Firebase project, from the repo's .firebaserc. */
async function projectId(): Promise<string> {
  const rc = JSON.parse(await readFile(path.resolve(ROOT, "..", "..", ".firebaserc"), "utf8")) as { projects: { default: string } };
  return rc.projects.default;
}

/**
 * The emulator unless [production] is asked for, and then only with credentials and no emulator
 * set, so a test run can never write to the live game by accident.
 */
async function connect(production: boolean): Promise<{ db: Firestore; where: string }> {
  const project = await projectId();
  if (production) {
    if (process.env.FIRESTORE_EMULATOR_HOST) throw new Error("FIRESTORE_EMULATOR_HOST is set; unset it to publish to production.");
    if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
      throw new Error("Publishing to production needs GOOGLE_APPLICATION_CREDENTIALS (a service account key for the project; keep it out of the repo).");
    }
  } else {
    process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
  }
  initializeApp({ projectId: project });
  return { db: getFirestore(), where: production ? `${project} (production)` : `${project} on the emulator at ${process.env.FIRESTORE_EMULATOR_HOST}` };
}

async function writeAll(db: Firestore, docs: [string, object][]): Promise<void> {
  for (let i = 0; i < docs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    for (const [docPath, data] of docs.slice(i, i + BATCH_SIZE)) batch.set(db.doc(docPath), data);
    await batch.commit();
  }
}

type PublishedTrack = { trackId: number; previewUrl: string; trackViewUrl: string; artworkUrl100?: string };

async function readIndex(): Promise<IndexSong[]> {
  return (JSON.parse(await readFile(path.join(paths.index, "search_index.json"), "utf8")) as { songs: IndexSong[] }).songs;
}

/**
 * The songs that can be answers: tiered, searchable (or nobody could guess them), and with a
 * preview to play.
 */
export async function answerSongs(index?: IndexSong[]): Promise<{ tiered: TieredSong; entry: IndexSong; track: PublishedTrack }[]> {
  const tiers = JSON.parse(await readFile(path.join(paths.tiers, "tiers.json"), "utf8").catch(() => "null")) as TieredSong[] | null;
  if (!tiers) throw new Error("No tiers yet. Run `npm run tier` first.");
  const indexed = new Map((index ?? (await readIndex())).map((s) => [s.songId, s]));
  const tracks = new Map((await loadPool()).map((s) => [s.songId, s]));
  const answers = tiers.flatMap((tiered) => {
    const entry = indexed.get(tiered.songId);
    const track = tracks.get(tiered.songId)?.track as PublishedTrack | undefined;
    return entry && track?.previewUrl ? [{ tiered, entry, track }] : [];
  });
  const left = tiers.length - answers.length;
  if (left > 0) console.log(`${left} tiered songs aren't in the search index or have no preview; left out. Rebuild the index to include them.`);
  return answers;
}

/**
 * Publishes the game's data (AGENTS.md §12.6): every tiered answer song (`songs`), every searchable
 * song's film for judging guesses (`catalog`), and [options.days] more days of `schedule`. Days
 * already scheduled are never changed: they may be live. With [PublishOptions.songsOnly], only the
 * songs are rewritten.
 */
export async function publish(options: PublishOptions): Promise<void> {
  const index = await readIndex();
  const indexed = new Map(index.map((s) => [s.songId, s]));
  const answered = await answerSongs(index);
  const answers = answered.map((a) => a.tiered);
  const tracks = new Map(answered.map((a) => [a.tiered.songId, a.track]));
  const vocalStarts = await readVocalStarts();

  const { db, where } = await connect(options.production);
  console.log(`Publishing to ${where}.`);

  const plan = options.songsOnly ? null : await planDays(db, answers, indexed, options);

  // A song already published keeps the preview the server last checked: the server refreshes it,
  // and the pool's copy may be older. A new song's preview is marked never checked, so the server
  // confirms it with iTunes before trusting it, and plays the backup if it's gone.
  // A clip start set on the server (by hand) stays until the vocal finder has checked the song.
  const published = new Map<string, { previewUrl: string; previewUrlCachedAt: Timestamp }>();
  const publishedStarts = new Map<string, number>();
  for (let i = 0; i < answers.length; i += BATCH_SIZE) {
    const refs = answers.slice(i, i + BATCH_SIZE).map((t) => db.doc(`songs/${t.songId}`));
    for (const snap of await db.getAll(...refs)) {
      const data = snap.data() as { previewUrl?: string; previewUrlCachedAt?: Timestamp; startOffsetMs?: number } | undefined;
      if (data?.previewUrl && data.previewUrlCachedAt) published.set(snap.id, { previewUrl: data.previewUrl, previewUrlCachedAt: data.previewUrlCachedAt });
      if (data?.startOffsetMs) publishedStarts.set(snap.id, data.startOffsetMs);
    }
  }
  const neverChecked = Timestamp.fromMillis(0);
  const songDocs: [string, object][] = answers.map((t) => {
    const entry = indexed.get(t.songId)!;
    const track = tracks.get(t.songId)!;
    return [
      `songs/${t.songId}`,
      {
        songId: t.songId,
        trackId: track.trackId,
        title: entry.title,
        filmId: entry.filmId,
        filmTitle: entry.filmTitle,
        year: entry.year,
        composer: t.composer,
        previewUrl: published.get(t.songId)?.previewUrl ?? track.previewUrl,
        previewUrlCachedAt: published.get(t.songId)?.previewUrlCachedAt ?? neverChecked,
        // Just before the singing (see vocals/find_vocal_start.py).
        startOffsetMs: vocalStarts.get(t.songId)?.clipStartMs ?? publishedStarts.get(t.songId) ?? 0,
        artworkUrl: track.artworkUrl100?.replace("100x100bb", "600x600bb") ?? null,
        appleMusicUrl: track.trackViewUrl ?? null,
        tier: t.tier,
        tierSource: "DATA",
        status: "ACTIVE",
      },
    ];
  });
  await writeAll(db, songDocs);
  const starts = songDocs.filter(([, doc]) => (doc as { startOffsetMs: number }).startOffsetMs > 0).length;
  if (!plan) {
    console.log(`Wrote ${songDocs.length} songs (${starts} start before the singing, the rest at 0). Catalogue and schedule unchanged.`);
    return;
  }
  const { existing, days } = plan;
  await writeAll(db, index.map((s) => [`catalog/${s.songId}`, { filmId: s.filmId }]));
  // create(), not set(): a day someone else scheduled meanwhile fails loudly instead of being replaced.
  for (const day of days) await db.doc(`schedule/${day.date}`).create({ showNo: day.showNo, tiers: day.tiers });

  console.log(`Wrote ${songDocs.length} songs (${starts} start before the singing), ${index.length} catalogue entries, and ${days.length} days: ${days[0]!.date} (Show No. ${days[0]!.showNo}) to ${days.at(-1)!.date}.`);
  const perTier = Map.groupBy(answers, (t) => t.tier);
  const used = existing.length + days.length;
  console.log(`Songs left per tier at a song a day: ${TIERS.map((t) => `${t} ${(perTier.get(t)?.length ?? 0) - used}`).join(", ")}.`);
}

/** The days to add after those already scheduled, which are never changed: they may be live. */
async function planDays(
  db: Firestore,
  answers: TieredSong[],
  indexed: Map<string, IndexSong>,
  options: PublishOptions,
): Promise<{ existing: ScheduledDay[]; days: ScheduledDay[] }> {
  const existing = (await db.collection("schedule").get()).docs.map((doc) => {
    const data = doc.data() as { showNo: number; tiers: Record<Tier, { songId: string; backupSongId: string; activeSongId?: string }> };
    // What actually played counts for the year-long gap.
    const tiersPlayed = Object.fromEntries(
      TIERS.map((t) => [t, { songId: data.tiers[t].activeSongId ?? data.tiers[t].songId, backupSongId: data.tiers[t].backupSongId }]),
    ) as ScheduledDay["tiers"];
    return { date: doc.id, showNo: data.showNo, tiers: tiersPlayed };
  });
  existing.sort((a, b) => a.date.localeCompare(b.date));
  const last = existing.at(-1);
  const launch = options.launch ?? (last ? addDays(last.date, 1 - last.showNo) : undefined);
  if (!launch) throw new Error("First publish: give the launch date (Show No. 1), e.g. `-- --launch 2026-11-01`.");
  const from = options.from ?? (last ? addDays(last.date, 1) : todayIst());
  if (existing.some((d) => d.date >= from && d.date < addDays(from, options.days))) {
    throw new Error(`Days from ${from} are already scheduled; they're never rewritten.`);
  }

  const schedulable: SchedulableSong[] = answers.map((t) => ({
    songId: t.songId,
    filmId: indexed.get(t.songId)!.filmId,
    composer: t.composer,
    tier: t.tier,
    year: indexed.get(t.songId)!.year,
  }));
  const days = schedule(schedulable, existing, from, options.days, launch, options.seed);

  return { existing, days };
}
