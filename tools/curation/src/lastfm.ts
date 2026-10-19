import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";
import { setTimeout as sleep } from "node:timers/promises";

export interface LastfmTrack {
  name: string;
  artist: string;
  listeners: number;
}

const BASE_URL = "https://ws.audioscrobbler.com/2.0/";

/** Last.fm error codes worth waiting out: rate limit, temporarily unavailable, operation failed. */
const RETRYABLE = new Set([8, 11, 16, 29]);

/**
 * Last.fm client for public track stats. Last.fm asks for at most about 5 calls a second, so call
 * starts are spaced out, even with several lookups in flight; every response is cached on disk,
 * so re-running costs nothing.
 */
export class LastfmClient {
  private readonly apiKey: string;
  private readonly cacheDir: string;
  private readonly minIntervalMs: number;
  private lastCallAt = 0;

  constructor(apiKey: string, cacheDir: string, minIntervalMs = 250) {
    this.apiKey = apiKey;
    this.cacheDir = cacheDir;
    this.minIntervalMs = minIntervalMs;
  }

  /** Tracks named like [title], across every artist spelling, with their listener counts. */
  async searchTrack(title: string): Promise<LastfmTrack[]> {
    const query = title.trim().toLowerCase();
    const cacheFile = path.join(this.cacheDir, `${createHash("sha1").update(`search:${query}`).digest("hex")}.json`);
    const hit = await readFile(cacheFile, "utf8").catch(() => null);
    if (hit !== null) return JSON.parse(hit) as LastfmTrack[];

    const body = await this.call({ method: "track.search", track: query, limit: "50" });
    const matches = (body as { results?: { trackmatches?: { track?: RawTrack[] | RawTrack } } }).results?.trackmatches?.track;
    const tracks = (Array.isArray(matches) ? matches : matches ? [matches] : []).map((t) => ({
      name: t.name,
      artist: t.artist,
      listeners: Number(t.listeners) || 0,
    }));
    await mkdir(this.cacheDir, { recursive: true });
    await writeFile(cacheFile, JSON.stringify(tracks));
    return tracks;
  }

  private async call(params: Record<string, string>): Promise<unknown> {
    const url = `${BASE_URL}?${new URLSearchParams({ ...params, api_key: this.apiKey, format: "json" })}`;
    for (let attempt = 1; ; attempt++) {
      // Each call books the next free start time before waiting, so concurrent calls queue up.
      const now = Date.now();
      const start = Math.max(now, this.lastCallAt + this.minIntervalMs);
      this.lastCallAt = start;
      if (start > now) await sleep(start - now);

      const response = await fetch(url, { signal: AbortSignal.timeout(30_000) });
      const body = (await response.json().catch(() => ({}))) as { error?: number; message?: string };
      if (response.ok && body.error === undefined) return body;
      const retryable = response.status === 429 || response.status >= 500 || RETRYABLE.has(body.error ?? -1);
      if (!retryable || attempt >= 3) {
        throw new Error(`Last.fm request failed (${body.error ?? response.status}: ${body.message ?? response.statusText})`);
      }
      await sleep(30_000);
    }
  }
}

interface RawTrack {
  name: string;
  artist: string;
  listeners: string;
}
