import type { ItunesTrack } from "./itunes.ts";
import { baseTitle, filmKey, isAlternateVersion } from "./match.ts";
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
  const add = (track: ItunesTrack, film: string, year: number) => {
    if (isAlternateVersion(track.trackName)) return;
    const title = baseTitle(track.trackName);
    const filmId = filmIdOf(film, year);
    const key = songKey(title, filmId);
    if (title.length === 0 || songs.has(key)) return;
    songs.set(key, { songId: songIdOf(track.trackId), title, filmId, filmTitle: film, year });
  };

  // Pool songs first (lowest track id first, so reruns are stable), then the rest of each soundtrack.
  for (const song of [...pool].sort((a, b) => a.track.trackId - b.track.trackId)) add(song.track, song.film, song.year);
  for (const album of albums) for (const track of album.tracks) add(track, album.film, album.year);

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

/** Singles and EPs carry one or two songs; only full soundtracks are worth expanding. */
export function isFullSoundtrack(album: { collectionName: string; trackCount?: number }): boolean {
  return (album.trackCount ?? 0) >= 3 && !/ - (single|ep)$/i.test(album.collectionName);
}
