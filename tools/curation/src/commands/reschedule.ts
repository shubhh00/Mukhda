import { addDays, replaceSongs, type SchedulableSong } from "../schedule.ts";
import { answerSongs, connect, readSchedule, todayIst } from "./publish.ts";
import { hasNoEarlySinging, readVocalStarts } from "./vocals.ts";

export interface RescheduleOptions {
  production: boolean;
  /** Print the swaps without writing them. */
  dryRun: boolean;
  seed: number;
}

/**
 * Takes songs whose short clips can't reach a voice out of the days already scheduled: each such
 * main or backup, from tomorrow on, is swapped for another song of its tier under the scheduling
 * rules. Today is never touched, as rounds may be under way. Prints song ids, never titles.
 */
export async function reschedule(options: RescheduleOptions): Promise<void> {
  const vocalStarts = await readVocalStarts();
  if (vocalStarts.size === 0) throw new Error("No vocal results yet. Run the vocal finder first.");
  const answers = await answerSongs();
  const songs: SchedulableSong[] = answers.map(({ tiered, entry }) => ({
    songId: tiered.songId,
    filmId: entry.filmId,
    composer: tiered.composer,
    tier: tiered.tier,
    year: entry.year,
  }));
  const isOut = (songId: string) => {
    const found = vocalStarts.get(songId);
    return found !== undefined && hasNoEarlySinging(found);
  };

  const { db, where } = await connect(options.production);
  const days = await readSchedule(db);
  const from = addDays(todayIst(), 1);
  const live = days.filter((d) => d.date >= from && d.live).map((d) => d.date);
  if (live.length) throw new Error(`Days from ${from} already have a swapped song (${live.join(", ")}); not touching them.`);
  const changes = replaceSongs(songs, days, isOut, from, options.seed);

  console.log(`${where}: ${changes.length} slots to change from ${from}.`);
  for (const c of changes) console.log(`  ${c.date} ${c.tier.padEnd(6)} ${c.slot === "songId" ? "song  " : "backup"} ${c.from} -> ${c.to}`);
  if (options.dryRun || changes.length === 0) return;
  // One day at a time, each in a transaction that checks the slot still holds the song it replaces.
  for (const [date, dayChanges] of Map.groupBy(changes, (c) => c.date)) {
    const ref = db.doc(`schedule/${date}`);
    await db.runTransaction(async (tx) => {
      const tiers = (await tx.get(ref)).get("tiers") as Record<string, Record<string, string>>;
      // From 23:55 the server settles tomorrow's songs to send their clips ahead: a day it has
      // settled since it was read is live now, and left as it is.
      if (Object.values(tiers).some((t) => t.activeSongId !== undefined)) {
        throw new Error(`${date} went live meanwhile; nothing written for that day.`);
      }
      for (const c of dayChanges) {
        if (tiers[c.tier]?.[c.slot] !== c.from) throw new Error(`${date} ${c.tier} ${c.slot} changed meanwhile; nothing written for that day.`);
      }
      tx.update(ref, Object.fromEntries(dayChanges.map((c) => [`tiers.${c.tier}.${c.slot}`, c.to])));
    });
  }
  console.log(`Wrote ${changes.length} changes.`);
}
