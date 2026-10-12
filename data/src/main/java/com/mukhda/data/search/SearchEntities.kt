package com.mukhda.data.search

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/** One searchable song. Title and film only: artists are never stored. */
@Entity(tableName = "search_songs")
data class SearchSongEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val filmId: String,
    val filmTitle: String,
    val year: Int,
    /** [com.mukhda.domain.search.SearchNormalizer] output, so FTS tokens match normalised queries. */
    val titleNorm: String,
    val filmNorm: String,
)

/** Full-text index over the normalised columns of [SearchSongEntity]; Room keeps it in sync. */
@Fts4(contentEntity = SearchSongEntity::class)
@Entity(tableName = "search_songs_fts")
data class SearchSongFtsEntity(
    val titleNorm: String,
    val filmNorm: String,
)

/** Which index version is loaded. A single row with id 0. */
@Entity(tableName = "search_index_meta")
data class SearchIndexMetaEntity(
    @PrimaryKey val id: Int = 0,
    val version: Int,
)
