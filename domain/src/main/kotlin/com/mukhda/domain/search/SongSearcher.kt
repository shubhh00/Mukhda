package com.mukhda.domain.search

/** A searchable song. Title and film only — artists are never indexed. */
data class SearchSong(
    val songId: String,
    val title: String,
    val filmId: String,
    val filmTitle: String,
    val year: Int,
)

data class SearchResults(
    val items: List<SearchSong>,
    /** All matches, so the UI can say "5 of N shown · type more to narrow". */
    val totalMatches: Int,
) {
    val isTruncated: Boolean get() = totalMatches > items.size

    companion object {
        val EMPTY = SearchResults(emptyList(), 0)
    }
}

/**
 * In-memory ranked search over the bundled index.
 *
 * Every query token must match the song's title or its film, so "lootera shik" finds
 * songs from Lootera whose title starts with "shik". Ranking:
 * exact prefix of the whole query > every token a word prefix > fuzzy.
 * Within a rank, title matches come before film-only matches.
 */
class SongSearcher(songs: List<SearchSong>) {

    private class Entry(
        val song: SearchSong,
        val titleJoined: String,
        val filmJoined: String,
        val titleTokens: List<String>,
        val filmTokens: List<String>,
    )

    private enum class Rank { EXACT_PREFIX, TOKEN_PREFIX, FUZZY }

    private val entries = songs.map { song ->
        val title = SearchNormalizer.normalize(song.title)
        val film = SearchNormalizer.normalize(song.filmTitle)
        Entry(
            song = song,
            titleJoined = SearchNormalizer.joined(title),
            filmJoined = SearchNormalizer.joined(film),
            titleTokens = SearchNormalizer.tokens(title),
            filmTokens = SearchNormalizer.tokens(film),
        )
    }

    fun search(query: String, limit: Int = MAX_RESULTS): SearchResults {
        if (query.trim().length < MIN_QUERY_LENGTH) return SearchResults.EMPTY
        val normalized = SearchNormalizer.normalize(query)
        val queryTokens = SearchNormalizer.tokens(normalized)
        if (queryTokens.isEmpty()) return SearchResults.EMPTY
        val queryJoined = SearchNormalizer.joined(normalized)

        val matches = entries.mapNotNull { entry ->
            match(entry, queryJoined, queryTokens)?.let { (rank, titleHit) -> Triple(entry, rank, titleHit) }
        }
        val ranked = matches.sortedWith(
            compareBy<Triple<Entry, Rank, Boolean>>(
                { it.second },
                { !it.third },
                { it.first.titleJoined.length },
                { it.first.song.title.lowercase() },
                { it.first.song.songId },
            )
        )
        return SearchResults(ranked.take(limit).map { it.first.song }, matches.size)
    }

    /** Returns the rank and whether the title (not just the film) matched, or null for no match. */
    private fun match(entry: Entry, queryJoined: String, queryTokens: List<String>): Pair<Rank, Boolean>? {
        if (entry.titleJoined.startsWith(queryJoined)) return Rank.EXACT_PREFIX to true
        if (entry.filmJoined.startsWith(queryJoined)) return Rank.EXACT_PREFIX to false

        val words = entry.titleTokens + entry.filmTokens
        if (queryTokens.all { q -> words.any { it.startsWith(q) } }) {
            return Rank.TOKEN_PREFIX to queryTokens.any { q -> entry.titleTokens.any { it.startsWith(q) } }
        }

        val fuzzyTitle = queryTokens.map { q -> fuzzyHit(q, entry.titleJoined, entry.titleTokens) }
        val fuzzyFilm = queryTokens.map { q -> fuzzyHit(q, entry.filmJoined, entry.filmTokens) }
        if (fuzzyTitle.indices.all { fuzzyTitle[it] || fuzzyFilm[it] }) {
            return Rank.FUZZY to fuzzyTitle.any { it }
        }
        return null
    }

    private fun fuzzyHit(q: String, joined: String, words: List<String>): Boolean {
        if (joined.contains(q)) return true
        if (q.length < FUZZY_MIN_TOKEN_LENGTH) return false
        // One typo against the start of a word: compare with word prefixes of length q±1.
        return words.any { word ->
            (q.length - 1..q.length + 1).any { len ->
                len <= word.length && editDistance(q, word.substring(0, len)) <= 1
            }
        }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_RESULTS = 5
        private const val FUZZY_MIN_TOKEN_LENGTH = 4

        internal fun editDistance(a: String, b: String): Int {
            var prev = IntArray(b.length + 1) { it }
            var curr = IntArray(b.length + 1)
            for (i in 1..a.length) {
                curr[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                }
                val t = prev; prev = curr; curr = t
            }
            return prev[b.length]
        }
    }
}
