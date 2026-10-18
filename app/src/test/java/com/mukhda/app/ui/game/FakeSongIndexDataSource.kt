package com.mukhda.app.ui.game

import com.mukhda.domain.DataError
import com.mukhda.domain.EmptyOutcome
import com.mukhda.domain.Outcome
import com.mukhda.domain.search.SearchResults
import com.mukhda.domain.search.SearchSong
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.search.SongSearcher

class FakeSongIndexDataSource(private val all: List<SearchSong> = emptyList()) : SongIndexDataSource {
    private val searcher = SongSearcher(all)
    var prepareCalls = 0
        private set

    override suspend fun prepare(): EmptyOutcome<DataError.Local> {
        prepareCalls++
        return Outcome.Success(Unit)
    }

    override suspend fun search(query: String): Outcome<SearchResults, DataError.Local> =
        Outcome.Success(searcher.search(query))

    override suspend fun songs(ids: Collection<String>): Outcome<Map<String, SearchSong>, DataError.Local> =
        Outcome.Success(all.filter { it.songId in ids }.associateBy { it.songId })
}
