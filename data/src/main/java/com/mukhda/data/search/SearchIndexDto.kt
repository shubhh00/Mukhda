package com.mukhda.data.search

import com.mukhda.domain.search.SearchNormalizer
import com.mukhda.domain.search.SearchSong
import kotlinx.serialization.Serializable

/** The index as JSON, as the curation tool writes it (`search_index.json`); tests build entities from it. */
@Serializable
data class SearchIndexDto(val songs: List<SearchSongDto>)

@Serializable
data class SearchSongDto(
    val songId: String,
    val title: String,
    val filmId: String,
    val filmTitle: String,
    val year: Int,
)

fun SearchSongDto.toEntity(): SearchSongEntity = SearchSongEntity(
    songId = songId,
    title = title,
    filmId = filmId,
    filmTitle = filmTitle,
    year = year,
    titleNorm = SearchNormalizer.normalize(title),
    filmNorm = SearchNormalizer.normalize(filmTitle),
)

fun SearchSongEntity.toSearchSong(): SearchSong = SearchSong(
    songId = songId,
    title = title,
    filmId = filmId,
    filmTitle = filmTitle,
    year = year,
)
