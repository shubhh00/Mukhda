import { type Firestore, Timestamp } from "firebase-admin/firestore";
import { logger } from "firebase-functions";
import {
  type Reveal,
  type RoundDoc,
  type ScheduleDoc,
  type ShowResponse,
  type SongDoc,
  type SongStatsDoc,
  type Tier,
  type TierState,
  TIERS,
} from "./model.ts";
import { type PreviewLookup, confirmPreview } from "./previews.ts";
import { currentStep, topPercent } from "./rules.ts";
import { istDate, showEndsAt } from "./time.ts";

export const roundId = (uid: string, date: string, tier: Tier) => `${uid}_${date}_${tier}`;
export const statsId = (date: string, tier: Tier) => `${date}_${tier}`;

/** No show scheduled for the day: the schedule ran out. Logged loudly; the app shows an error. */
export class NoShowError extends Error {
  constructor(date: string) {
    super(`No show scheduled for ${date}`);
  }
}

export async function readSchedule(db: Firestore, date: string): Promise<ScheduleDoc> {
  const snapshot = await db.doc(`schedule/${date}`).get();
  if (!snapshot.exists) throw new NoShowError(date);
  return snapshot.data() as ScheduleDoc;
}

async function readSong(db: Firestore, songId: string): Promise<SongDoc> {
  const snapshot = await db.doc(`songs/${songId}`).get();
  if (!snapshot.exists) throw new Error(`Scheduled song ${songId} is missing from songs`);
  return snapshot.data() as SongDoc;
}

/** The scheduled song if iTunes still has its preview (re-dating a stale one), else the backup. */
async function chooseSong(db: Firestore, date: string, tier: Tier, entry: ScheduleDoc["tiers"][Tier], now: Date, lookup: PreviewLookup): Promise<SongDoc> {
  const main = await readSong(db, entry.songId);
  const checked = await confirmPreview(
    { trackId: main.trackId, previewUrl: main.previewUrl, cachedAtMs: main.previewUrlCachedAt.toMillis() },
    now,
    lookup,
  );
  if (!checked.previewUrl) {
    logger.warn("Scheduled song's preview is gone; playing the backup", { date, tier, songId: main.songId, backupSongId: entry.backupSongId });
    return readSong(db, entry.backupSongId);
  }
  if (!checked.refreshed) return main;
  const refreshed = { ...main, previewUrl: checked.previewUrl, previewUrlCachedAt: Timestamp.fromDate(now) };
  await db.doc(`songs/${main.songId}`).update({ previewUrl: refreshed.previewUrl, previewUrlCachedAt: refreshed.previewUrlCachedAt });
  return refreshed;
}

/**
 * The songs [tiers] play today. Each is decided the first time its tier is served and stored on
 * the schedule, so every player and every later call gets the same one: the scheduled song while
 * iTunes still has its preview, otherwise the backup. Tiers decided together are stored in one
 * transaction, so a show's first call doesn't contend with itself.
 */
export async function activeSongs(db: Firestore, date: string, tiers: readonly Tier[], now: Date, lookup: PreviewLookup): Promise<Map<Tier, SongDoc>> {
  const scheduleRef = db.doc(`schedule/${date}`);
  const schedule = (await scheduleRef.get()).data() as ScheduleDoc | undefined;
  if (!schedule) throw new NoShowError(date);

  const songs = new Map<Tier, SongDoc>();
  const undecided = tiers.filter((tier) => !schedule.tiers[tier].activeSongId);
  await Promise.all(
    tiers.map(async (tier) => {
      const decided = schedule.tiers[tier].activeSongId;
      songs.set(tier, decided ? await readSong(db, decided) : await chooseSong(db, date, tier, schedule.tiers[tier], now, lookup));
    }),
  );
  if (undecided.length === 0) return songs;

  // Two first calls can race here; the first to store a tier's choice wins and the other follows it.
  const stored = await db.runTransaction(async (tx) => {
    const fresh = (await tx.get(scheduleRef)).data() as ScheduleDoc;
    const updates: Record<string, string> = {};
    const winners = new Map<Tier, string>();
    for (const tier of undecided) {
      const existing = fresh.tiers[tier].activeSongId;
      winners.set(tier, existing ?? songs.get(tier)!.songId);
      if (!existing) updates[`tiers.${tier}.activeSongId`] = songs.get(tier)!.songId;
    }
    if (Object.keys(updates).length > 0) tx.update(scheduleRef, updates);
    return winners;
  });
  await Promise.all(
    [...stored].map(async ([tier, songId]) => {
      if (songs.get(tier)!.songId !== songId) songs.set(tier, await readSong(db, songId));
    }),
  );
  return songs;
}

/** The song [tier] plays today; see [activeSongs]. */
export async function activeSong(db: Firestore, date: string, tier: Tier, now: Date, lookup: PreviewLookup): Promise<SongDoc> {
  return (await activeSongs(db, date, [tier], now, lookup)).get(tier)!;
}

/** What a finished round shows: the song, and how the player did against everyone so far. */
export function revealOf(song: SongDoc, stats: SongStatsDoc | undefined, solvedAtStep: number | null): Reveal {
  return {
    songId: song.songId,
    title: song.title,
    filmTitle: song.filmTitle,
    year: song.year,
    composer: song.composer,
    artworkUrl: song.artworkUrl,
    appleMusicUrl: song.appleMusicUrl,
    topPercent: topPercent(stats, solvedAtStep),
  };
}

/**
 * Today's show for one player: each tier's clip and where the player is in it. The answer is
 * only in a tier's `reveal`, and only once that player's round is over.
 */
export async function getShow(db: Firestore, uid: string, now: Date, lookup: PreviewLookup): Promise<ShowResponse> {
  const date = istDate(now);
  const schedule = await readSchedule(db, date);
  const rounds = new Map(
    await Promise.all(
      TIERS.map(async (tier) => [tier, (await db.doc(`rounds/${roundId(uid, date, tier)}`).get()).data() as RoundDoc | undefined] as const),
    ),
  );
  // A round already started keeps the song it started with, even if the tier later swapped.
  const fresh = await activeSongs(db, date, TIERS.filter((tier) => !rounds.get(tier)), now, lookup);
  const tiers = await Promise.all(
    TIERS.map(async (tier): Promise<[Tier, TierState]> => {
      const round = rounds.get(tier);
      const song = round ? await readSong(db, round.songId) : fresh.get(tier)!;
      const finished = round !== undefined && (round.status === "WON" || round.status === "LOST");
      const stats = finished ? ((await db.doc(`songStats/${statsId(date, tier)}`).get()).data() as SongStatsDoc | undefined) : undefined;
      const tries = round?.tries ?? [];
      return [
        tier,
        {
          previewUrl: song.previewUrl,
          startOffsetMs: song.startOffsetMs,
          status: round?.status ?? "OPEN",
          triesUsed: tries.length,
          currentStep: currentStep(tries.length),
          tries: tries.map((t) => ({ requestId: t.requestId, type: t.type, songId: t.songId, result: t.result })),
          reveal: finished ? revealOf(song, stats, round.solvedAtStep) : null,
        },
      ];
    }),
  );
  return { date, showNo: schedule.showNo, endsAt: showEndsAt(date).toISOString(), tiers: Object.fromEntries(tiers) };
}
