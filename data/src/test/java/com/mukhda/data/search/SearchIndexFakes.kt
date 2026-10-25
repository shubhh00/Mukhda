package com.mukhda.data.search

/** In-memory stand-in for Room. `matchPrefix` mimics FTS4: every `term*` prefixes a title or film word. */
class FakeSearchIndexDao : SearchIndexDao {
    private val songs = mutableListOf<SearchSongEntity>()
    private var meta: SearchIndexMetaEntity? = null
    var fullScans = 0
        private set

    override suspend fun version(): Int? = meta?.version

    override suspend fun matchPrefix(match: String): List<SearchSongEntity> {
        val terms = match.split(' ').map { it.removeSuffix("*") }
        return songs.filter { song ->
            val words = "${song.titleNorm} ${song.filmNorm}".split(' ')
            terms.all { term -> words.any { it.startsWith(term) } }
        }
    }

    override suspend fun all(): List<SearchSongEntity> {
        fullScans++
        return songs.toList()
    }

    override suspend fun byIds(ids: List<String>): List<SearchSongEntity> = songs.filter { it.songId in ids }

    override suspend fun clear() = songs.clear()

    override suspend fun insertAll(songs: List<SearchSongEntity>) {
        this.songs += songs
    }

    override suspend fun setMeta(meta: SearchIndexMetaEntity) {
        this.meta = meta
    }
}

/** Hands over [dao], or fails to open when [openError] is set. */
class FakeSearchIndexStore(private val dao: SearchIndexDao) : SearchIndexStore {
    var openError: Exception? = null

    override suspend fun dao(): SearchIndexDao {
        openError?.let { throw it }
        return dao
    }
}
