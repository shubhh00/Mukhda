import { logger } from "firebase-functions";

/** Looks up a track's current preview URL; null when iTunes no longer has one. */
export type PreviewLookup = (trackId: number) => Promise<string | null>;

/**
 * iTunes lookup in India's store. Throws on a network or server failure, so a passing outage
 * isn't mistaken for a removed preview.
 */
export const itunesPreview: PreviewLookup = async (trackId) => {
  const url = `https://itunes.apple.com/lookup?${new URLSearchParams({ id: String(trackId), country: "IN", entity: "song" })}`;
  const response = await fetch(url, { signal: AbortSignal.timeout(5_000) });
  if (!response.ok) throw new Error(`iTunes lookup failed (${response.status}) for track ${trackId}`);
  const body = (await response.json()) as { results?: { trackId?: number; previewUrl?: string }[] };
  return body.results?.find((r) => r.trackId === trackId)?.previewUrl ?? null;
};

/** How long a confirmed preview URL is trusted before it's checked with iTunes again. */
export const PREVIEW_TTL_MS = 3 * 24 * 60 * 60 * 1000;

/**
 * The song's preview URL if it still has one: the stored URL while it's fresh, else a fresh
 * lookup. A lookup that fails keeps the stored URL, since the song most likely still plays.
 */
export async function confirmPreview(
  song: { trackId: number; previewUrl: string; cachedAtMs: number },
  now: Date,
  lookup: PreviewLookup,
): Promise<{ previewUrl: string | null; refreshed: boolean }> {
  if (now.getTime() - song.cachedAtMs < PREVIEW_TTL_MS) return { previewUrl: song.previewUrl, refreshed: false };
  try {
    return { previewUrl: await lookup(song.trackId), refreshed: true };
  } catch (error) {
    logger.warn("Preview check failed; keeping the stored URL", { trackId: song.trackId, error: String(error) });
    return { previewUrl: song.previewUrl, refreshed: false };
  }
}
