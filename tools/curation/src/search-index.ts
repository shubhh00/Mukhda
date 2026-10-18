import type { ItunesTrack } from "./itunes.ts";
import { baseTitle, filmKey, filmMatches, filmOf, isAlternateVersion } from "./match.ts";
import { joined, normalize } from "./normalize.ts";

/** One row of the app's bundled search index. Title and film only: never artists. */
export interface IndexSong {
  songId: string;
  title: string;
  filmId: string;
  filmTitle: string;
  year: number;
}

/** A verified answer-pool song: its iTunes track and the film/year it was checked against. */
export interface PoolSong {
  track: ItunesTrack;
  film: string;
  year: number;
}

/** A soundtrack album and the film it belongs to. */
export interface AlbumSource {
  film: string;
  year: number;
  tracks: ItunesTrack[];
}

/** Song ids are Apple's track ids, so the app, the server and Apple Music all agree. */
export const songIdOf = (trackId: number): string => `s_${trackId}`;

/** The year keeps remakes apart: Don (1978) and Don (2006) are different films. */
export const filmIdOf = (film: string, year: number): string => `f_${filmKey(film)}_${year}`;

/** Background score, dialogue and medley tracks, and versions named in the title itself. */
const NOT_A_SONG =
  /\b(theme|dialogues?|medley|mashup|jukebox|interlude|bgm|remix|instrumental|unplugged|reprise|lo ?fi|karaoke|house track|club mix|version)\b/i;

/** "Main Hoon Saath Tere Feat. Shivangi Bhayana" -> "Main Hoon Saath Tere": artists never go in the index. */
const FEATURING = /\s+(feat\.?|ft\.?|featuring)\s.*$/i;

/** The searchable title: no version suffix, no featured artists. */
export function indexTitle(trackName: string): string {
  return baseTitle(trackName).replace(FEATURING, "").trim();
}

/** False for themes, dialogues, medleys, remixes and other tracks that aren't a playable song. */
export function isSong(trackName: string): boolean {
  return !isAlternateVersion(trackName) && !NOT_A_SONG.test(indexTitle(trackName));
}

/** Same song from the same film, however it's spelled or wherever it was released. */
const songKey = (title: string, filmId: string) => `${filmId}|${joined(normalize(title))}`;

/**
 * Builds the search index: every answer-pool song, plus every other song on the same films'
 * soundtracks. Remixes, reprises and instrumentals are left out, and each song appears once.
 * When a song exists twice (an album track and a single), the answer pool's track id wins, so
 * the index and the pool always use the same id for a song.
 */
export function assembleIndex(pool: PoolSong[], albums: AlbumSource[]): IndexSong[] {
  const songs = new Map<string, IndexSong>();
  const usedIds = new Set<number>();
  // Pool songs were verified as the film's own version, including recreations whose film version
  // is a remix ("Oh Ho Ho Ho (Remix)" in Hindi Medium), so they always go in. Only the rest of
  // each soundtrack is filtered for themes, dialogues and alternate versions.
  const add = (track: ItunesTrack, film: string, year: number, verified: boolean) => {
    if (!verified && !isSong(track.trackName)) return;
    const title = indexTitle(track.trackName);
    const filmId = filmIdOf(film, year);
    const key = songKey(title, filmId);
    // An Apple track belongs to one film: its id is the song id, so it can only appear once.
    if (title.length === 0 || songs.has(key) || usedIds.has(track.trackId)) return;
    usedIds.add(track.trackId);
    songs.set(key, { songId: songIdOf(track.trackId), title, filmId, filmTitle: film, year });
  };

  // Pool songs first (lowest track id first, so reruns are stable), then the rest of each soundtrack.
  // When one track was suggested for two films: an exact album match wins, then a looser match,
  // then the film whose year is closest to the track's release ("Ishq Vishk" 2003 vs "Rebound" 2024).
  const albumFit = (p: PoolSong) => (filmKey(p.film) === filmKey(filmOf(p.track)) ? 2 : filmMatches(p.film, p.track) ? 1 : 0);
  const yearGap = (p: PoolSong) => Math.abs(new Date(p.track.releaseDate).getUTCFullYear() - p.year);
  const ordered = [...pool].sort(
    (a, b) => albumFit(b) - albumFit(a) || yearGap(a) - yearGap(b) || a.track.trackId - b.track.trackId,
  );
  for (const song of ordered) add(song.track, song.film, song.year, true);
  for (const album of albums) for (const track of album.tracks) add(track, album.film, album.year, false);

  return [...songs.values()].sort(
    (a, b) => a.year - b.year || a.filmTitle.localeCompare(b.filmTitle) || a.title.localeCompare(b.title),
  );
}

/** The most common value, for picking one film name and year per album. */
export function mostCommon<T>(values: T[]): T | undefined {
  const counts = new Map<T, number>();
  for (const v of values) counts.set(v, (counts.get(v) ?? 0) + 1);
  return [...counts].sort((a, b) => b[1] - a[1])[0]?.[0];
}

/** Bigger than any real soundtrack: an album this size is a compilation of many films. */
const MAX_SOUNDTRACK_TRACKS = 30;

/**
 * Albums worth expanding into their whole track list. Apple labels many Bollywood soundtracks
 * "- EP" when they have only five to seven songs, so EPs count; singles and compilations don't.
 */
export function isFullSoundtrack(album: { collectionName: string; trackCount?: number }): boolean {
  const tracks = album.trackCount ?? 0;
  return tracks >= 3 && tracks <= MAX_SOUNDTRACK_TRACKS && !/ - single$/i.test(album.collectionName);
}
