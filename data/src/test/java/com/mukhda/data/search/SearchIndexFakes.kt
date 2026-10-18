package com.mukhda.data.search

import kotlinx.serialization.json.Json

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

    override suspend fun count(): Int = songs.size

    override suspend fun songIdAt(offset: Int): String? = songs.map { it.songId }.sorted().getOrNull(offset)

    override suspend fun clear() = songs.clear()

    override suspend fun insertAll(songs: List<SearchSongEntity>) {
        this.songs += songs
    }

    override suspend fun setMeta(meta: SearchIndexMetaEntity) {
        this.meta = meta
    }
}

class FakeSearchIndexAsset : SearchIndexAsset {
    private val files = mutableMapOf<Int, String>()
    var reads = 0
        private set

    fun bundle(version: Int, vararg songs: SearchSongDto) {
        files[version] = Json.encodeToString(SearchIndexDto.serializer(), SearchIndexDto(songs.toList()))
    }

    fun bundleRaw(version: Int, json: String) {
        files[version] = json
    }

    override fun latestVersion(): Int? = files.keys.maxOrNull()

    override fun read(version: Int): String {
        reads++
        return files.getValue(version)
    }
}
