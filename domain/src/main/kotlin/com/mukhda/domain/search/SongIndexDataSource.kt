package com.mukhda.domain.search

import com.mukhda.domain.DataError
import com.mukhda.domain.EmptyOutcome
import com.mukhda.domain.Outcome

/** The on-device song index used by the guess dropdown. Holds far more songs than the answer pool. */
interface SongIndexDataSource {
    /** Loads the bundled index if it's missing or outdated. Safe to call repeatedly. */
    suspend fun prepare(): EmptyOutcome<DataError.Local>

    /** Ranked title/film matches for [query]; fewer than two characters gives no results. */
    suspend fun search(query: String): Outcome<SearchResults, DataError.Local>
}
