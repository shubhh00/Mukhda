package com.mukhda.data.search

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface SearchIndexDao {
    @Query("SELECT version FROM search_index_meta WHERE id = 0")
    suspend fun version(): Int?

    /** [match] is an FTS4 query such as `lutera* shik*`: every term must prefix a title or film word. */
    @Query(
        """
        SELECT search_songs.* FROM search_songs
        JOIN search_songs_fts ON search_songs.rowid = search_songs_fts.rowid
        WHERE search_songs_fts MATCH :match
        """
    )
    suspend fun matchPrefix(match: String): List<SearchSongEntity>

    @Query("SELECT * FROM search_songs")
    suspend fun all(): List<SearchSongEntity>

    @Query("SELECT * FROM search_songs WHERE songId IN (:ids)")
    suspend fun byIds(ids: List<String>): List<SearchSongEntity>

    @Query("DELETE FROM search_songs")
    suspend fun clear()

    /** A repeated songId is skipped rather than failing the whole import. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<SearchSongEntity>)

    @Upsert
    suspend fun setMeta(meta: SearchIndexMetaEntity)

    /** Swaps the whole index in one transaction so a crash mid-import leaves the old index intact. */
    @Transaction
    suspend fun replaceIndex(songs: List<SearchSongEntity>, version: Int) {
        clear()
        insertAll(songs)
        setMeta(SearchIndexMetaEntity(version = version))
    }
}
