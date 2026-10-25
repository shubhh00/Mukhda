package com.mukhda.data.search

import android.database.sqlite.SQLiteFullException
import com.mukhda.data.di.IoDispatcher
import com.mukhda.domain.DataError
import com.mukhda.domain.EmptyOutcome
import com.mukhda.domain.Outcome
import com.mukhda.domain.search.SearchNormalizer
import com.mukhda.domain.search.SearchResults
import com.mukhda.domain.search.SearchSong
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.search.SongSearcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Song index in Room, opened from the ready-built database bundled with the app (see
 * [SearchDatabase]) on first use.
 *
 * Search takes two paths. FTS4 finds songs where every query word prefixes a title or film
 * word; when that gives a full page of results, they're ranked and returned. Otherwise the
 * query is a typo, a run-together title ("kalhonaho") or simply rare, so the whole index is
 * ranked in memory by [SongSearcher], which also handles those cases.
 */
@Singleton
class RoomSongIndexDataSource @Inject constructor(
    private val store: SearchIndexStore,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SongIndexDataSource {

    private val mutex = Mutex()
    private var fullIndex: SongSearcher? = null

    /** Opens the index now, so the first keystroke doesn't wait for it. */
    override suspend fun prepare(): EmptyOutcome<DataError.Local> = local { store.dao().version(); Unit }

    override suspend fun search(query: String): Outcome<SearchResults, DataError.Local> = local {
        val tokens = SearchNormalizer.tokens(SearchNormalizer.normalize(query))
        if (query.trim().length < SongSearcher.MIN_QUERY_LENGTH || tokens.isEmpty()) {
            return@local SearchResults.EMPTY
        }
        val prefixMatches = store.dao().matchPrefix(tokens.joinToString(" ") { "$it*" })
        val fast = SongSearcher(prefixMatches.map { it.toSearchSong() }).search(query)
        if (fast.items.size >= SongSearcher.MAX_RESULTS) fast else fullIndex().search(query)
    }

    override suspend fun songs(ids: Collection<String>): Outcome<Map<String, SearchSong>, DataError.Local> = local {
        if (ids.isEmpty()) return@local emptyMap()
        store.dao().byIds(ids.distinct()).associate { it.songId to it.toSearchSong() }
    }

    private suspend fun fullIndex(): SongSearcher = mutex.withLock {
        fullIndex ?: SongSearcher(store.dao().all().map { it.toSearchSong() }).also { fullIndex = it }
    }

    private suspend fun <T> local(block: suspend () -> T): Outcome<T, DataError.Local> =
        withContext(ioDispatcher) {
            try {
                Outcome.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLiteFullException) {
                Outcome.Error(DataError.Local.DISK_FULL)
            } catch (e: Exception) {
                Outcome.Error(DataError.Local.UNKNOWN)
            }
        }
}
