/**
 * Same normalisation as the app's `SearchNormalizer` (domain module), so the curation tool and
 * the in-app search agree on when two spellings are the same song. Keep the two in sync.
 */
const PHONETIC: ReadonlyArray<readonly [string, string]> = [
  ["ee", "i"],
  ["oo", "u"],
  ["ph", "f"],
  ["w", "v"],
  ["z", "j"],
];

export function normalize(text: string): string {
  let s = text
    .normalize("NFD")
    .replace(/\p{Mn}+/gu, "")
    .toLowerCase()
    .replace(/['’`"]/g, "")
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
  for (const [from, to] of PHONETIC) s = s.replaceAll(from, to);
  return s.replace(/([a-z])\1+/g, "$1");
}

export function joined(normalized: string): string {
  return normalized.replaceAll(" ", "");
}

export function editDistance(a: string, b: string): number {
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const curr = [i];
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(prev[j]! + 1, curr[j - 1]! + 1, prev[j - 1]! + cost);
    }
    prev = curr;
  }
  return prev[b.length]!;
}
