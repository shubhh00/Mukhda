import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { setTimeout as sleep } from "node:timers/promises";

export interface ItunesTrack {
  trackId: number;
  trackName: string;
  collectionName: string;
  artistName: string;
  releaseDate: string;
  previewUrl?: string;
  trackViewUrl: string;
  primaryGenreName?: string;
  collectionId?: number;
  /** Tracks on the album; 1 or 2 usually means a single. */
  trackCount?: number;
  wrapperType?: string;
  kind?: string;
}

export interface ItunesAlbum {
  collectionId: number;
  collectionName: string;
  trackCount: number;
  releaseDate: string;
}

const BASE_URL = "https://itunes.apple.com";

/**
 * iTunes Search API client. Apple allows roughly 20 calls a minute, so network calls are
 * spaced out and every response is cached on disk: re-running a step costs nothing.
 */
export class ItunesClient {
  private readonly cacheDir: string;
  private readonly minIntervalMs: number;
  private lastCallAt = 0;

  constructor(cacheDir: string, minIntervalMs = 3_100) {
    this.cacheDir = cacheDir;
    this.minIntervalMs = minIntervalMs;
  }

  /** Songs matching [term]. Cached under the plain term (the original cache format). */
  async search(term: string): Promise<ItunesTrack[]> {
    const query = term.trim().toLowerCase();
    return this.cached(query, "/search", { term: query, media: "music", entity: "song", limit: "25" });
  }

  /** Every song on an album, in track order. */
  async albumTracks(collectionId: number): Promise<ItunesTrack[]> {
    const results = await this.cached<ItunesTrack>(`lookup:${collectionId}`, "/lookup", {
      id: String(collectionId),
      entity: "song",
      limit: "200",
    });
    return results.filter((r) => r.wrapperType === "track" && r.kind === "song");
  }

  /** Albums matching [term], to find a film's soundtrack when only singles were found. */
  async searchAlbums(term: string): Promise<ItunesAlbum[]> {
    const query = term.trim().toLowerCase();
    return this.cached(`albums:${query}`, "/search", { term: query, media: "music", entity: "album", limit: "10" });
  }

  private async cached<T>(key: string, endpoint: string, params: Record<string, string>): Promise<T[]> {
    const cacheFile = path.join(this.cacheDir, `${createHash("sha1").update(key).digest("hex")}.json`);
    const hit = await readFile(cacheFile, "utf8").catch(() => null);
    if (hit !== null) return JSON.parse(hit) as T[];

    const url = `${BASE_URL}${endpoint}?${new URLSearchParams({ ...params, country: "IN" })}`;
    const results = await this.fetchResults<T>(url, key);
    // null: Apple answered 404, which it sometimes does for an ordinary search. Treat it as no
    // results, but don't cache it, so the next run asks again.
    if (results === null) return [];
    await mkdir(this.cacheDir, { recursive: true });
    await writeFile(cacheFile, JSON.stringify(results));
    return results;
  }

  private async fetchResults<T>(url: string, label: string): Promise<T[] | null> {
    for (let attempt = 1; ; attempt++) {
      const wait = this.lastCallAt + this.minIntervalMs - Date.now();
      if (wait > 0) await sleep(wait);
      this.lastCallAt = Date.now();

      const response = await fetch(url, { signal: AbortSignal.timeout(30_000) });
      if (response.ok) {
        const body = (await response.json()) as { results: T[] };
        return body.results;
      }
      if (response.status === 404) return null;
      // Apple answers rate limiting with 403 or 429. Back off a full minute.
      const retryable = response.status === 403 || response.status === 429 || response.status >= 500;
      if (!retryable || attempt >= 3) {
        throw new Error(`iTunes request failed (${response.status}) for "${label}"`);
      }
      await sleep(60_000);
    }
  }
}
