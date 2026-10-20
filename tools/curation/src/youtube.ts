import { createHash } from "node:crypto";
import { mkdir, readFile } from "node:fs/promises";
import path from "node:path";
import { writeAtomically } from "./pool.ts";

export interface Upload {
  id: string;
  title: string;
}

/** A label channel's uploads playlist ("UU…", the channel id with UC swapped for UU). */
export interface LabelChannel {
  name: string;
  uploads: string;
}

const BASE_URL = "https://www.googleapis.com/youtube/v3";

/** The day's quota is spent; nothing more can be fetched until it resets (midnight Pacific). */
export class QuotaExceededError extends Error {
  constructor() {
    super("YouTube quota for today is used up");
  }
}

/**
 * YouTube Data API client for label channels. Listing uploads and reading view counts both cost 1
 * unit per 50 videos, of a default 10,000 a day. Every page and count is cached on disk, so a run
 * stopped by the quota carries on the next day where it stopped.
 */
export class YoutubeClient {
  private readonly apiKey: string;
  private readonly cacheDir: string;
  private views: Record<string, number> | null = null;

  constructor(apiKey: string, cacheDir: string) {
    this.apiKey = apiKey;
    this.cacheDir = cacheDir;
  }

  /** Every playlist id of the channel with [channelId]. Each page is cached as it arrives. */
  async playlists(channelId: string): Promise<string[]> {
    const ids: string[] = [];
    let pageToken = "";
    do {
      const cacheFile = path.join(this.cacheDir, "playlists", `${createHash("sha1").update(`${channelId}:${pageToken}`).digest("hex")}.json`);
      const hit = await readFile(cacheFile, "utf8").catch(() => null);
      let page: { ids: string[]; next?: string };
      if (hit !== null) {
        page = JSON.parse(hit) as { ids: string[]; next?: string };
      } else {
        const body = (await this.get("playlists", {
          part: "id",
          channelId,
          maxResults: "50",
          ...(pageToken ? { pageToken } : {}),
        })) as { items?: { id: string }[]; nextPageToken?: string };
        page = { ids: (body.items ?? []).map((item) => item.id), next: body.nextPageToken };
        await mkdir(path.dirname(cacheFile), { recursive: true });
        await writeAtomically(cacheFile, JSON.stringify(page));
      }
      ids.push(...page.ids);
      pageToken = page.next ?? "";
    } while (pageToken);
    return ids;
  }

  /** Every video in [playlist], in its order. Each page is cached as it arrives. */
  async uploads(playlist: string): Promise<Upload[]> {
    const all: Upload[] = [];
    let pageToken = "";
    do {
      const page = await this.uploadsPage(playlist, pageToken);
      all.push(...page.items);
      pageToken = page.next ?? "";
    } while (pageToken);
    return all;
  }

  private async uploadsPage(playlist: string, pageToken: string): Promise<{ items: Upload[]; next?: string }> {
    const cacheFile = path.join(this.cacheDir, "uploads", `${createHash("sha1").update(`${playlist}:${pageToken}`).digest("hex")}.json`);
    const hit = await readFile(cacheFile, "utf8").catch(() => null);
    if (hit !== null) return JSON.parse(hit) as { items: Upload[]; next?: string };

    const body = (await this.get("playlistItems", {
      part: "snippet",
      playlistId: playlist,
      maxResults: "50",
      ...(pageToken ? { pageToken } : {}),
    })) as { items?: { snippet: { title: string; resourceId: { videoId: string } } }[]; nextPageToken?: string };
    const page = {
      items: (body.items ?? []).map((item) => ({ id: item.snippet.resourceId.videoId, title: item.snippet.title })),
      next: body.nextPageToken,
    };
    await mkdir(path.dirname(cacheFile), { recursive: true });
    await writeAtomically(cacheFile, JSON.stringify(page));
    return page;
  }

  /** View counts for [ids], fetched 50 at a time; counts already fetched come from the cache. */
  async viewCounts(ids: string[]): Promise<Map<string, number>> {
    const file = path.join(this.cacheDir, "views.json");
    this.views ??= JSON.parse(await readFile(file, "utf8").catch(() => "{}")) as Record<string, number>;
    const views = this.views;
    const missing = [...new Set(ids)].filter((id) => !(id in views));
    try {
      for (let i = 0; i < missing.length; i += 50) {
        const batch = missing.slice(i, i + 50);
        const body = (await this.get("videos", { part: "statistics", id: batch.join(",") })) as {
          items?: { id: string; statistics: { viewCount?: string } }[];
        };
        // A removed or private video comes back without an item; it counts as no views.
        for (const id of batch) views[id] = 0;
        for (const item of body.items ?? []) views[item.id] = Number(item.statistics.viewCount) || 0;
      }
    } finally {
      await mkdir(this.cacheDir, { recursive: true });
      await writeAtomically(file, JSON.stringify(views));
    }
    return new Map(ids.map((id) => [id, views[id] ?? 0]));
  }

  private async get(endpoint: string, params: Record<string, string>): Promise<unknown> {
    const url = `${BASE_URL}/${endpoint}?${new URLSearchParams({ ...params, key: this.apiKey })}`;
    const response = await fetch(url, { signal: AbortSignal.timeout(30_000) });
    const body = (await response.json().catch(() => ({}))) as { error?: { errors?: { reason?: string }[]; message?: string } };
    if (response.ok) return body;
    const reason = body.error?.errors?.[0]?.reason;
    if (reason === "quotaExceeded" || reason === "dailyLimitExceeded") throw new QuotaExceededError();
    throw new Error(`YouTube request failed (${response.status} ${reason ?? ""}: ${body.error?.message ?? response.statusText})`);
  }
}
