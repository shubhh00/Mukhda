package com.mukhda.data.search

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.mukhda.domain.DataError
import com.mukhda.domain.Outcome
import com.mukhda.domain.search.SearchResults
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class RoomSongIndexDataSourceTest {

    private val dao = FakeSearchIndexDao()
    private val store = FakeSearchIndexStore(dao)
    private val source = RoomSongIndexDataSource(store, UnconfinedTestDispatcher())

    /** The index as it ships, ready built. */
    private suspend fun bundle(vararg songs: SearchSongDto) = dao.replaceIndex(songs.map { it.toEntity() }, version = 1)

    private suspend fun ids(query: String): List<String> =
        (source.search(query) as Outcome.Success).data.items.map { it.songId }

    @Test
    fun `searches the bundled index`() = runTest {
        bundle(song("s1", "Tum Hi Ho", "Aashiqui 2"))

        assertThat(ids("tum hi")).containsExactly("s1")
        assertThat(ids("tum")).containsExactly("s1")
    }

    @Test
    fun `short query returns nothing without touching the database`() = runTest {
        bundle(song("s1", "Tum Hi Ho", "Aashiqui 2"))

        store.openError = IllegalStateException("not opened")

        assertThat(source.search("t")).isEqualTo(Outcome.Success(SearchResults.EMPTY))
    }

    @Test
    fun `full page of prefix hits is served from FTS alone`() = runTest {
        bundle(*lootera)

        val results = (source.search("lootera") as Outcome.Success).data
        assertThat(results.items.size).isEqualTo(5)
        assertThat(results.totalMatches).isEqualTo(6)
        assertThat(dao.fullScans).isEqualTo(0)
    }

    @Test
    fun `typo falls back to ranking the whole index`() = runTest {
        bundle(*lootera)

        assertThat(ids("manmarzian")).containsExactly("l3")
        assertThat(dao.fullScans).isEqualTo(1)
    }

    @Test
    fun `run-together title falls back to ranking the whole index`() = runTest {
        bundle(song("s1", "Kal Ho Naa Ho", "Kal Ho Naa Ho"))

        assertThat(ids("kalhonaho")).containsExactly("s1")
    }

    @Test
    fun `an index that won't open is reported, not thrown`() = runTest {
        store.openError = IllegalStateException("corrupt")

        assertThat(source.prepare()).isEqualTo(Outcome.Error(DataError.Local.UNKNOWN))
    }

    private val lootera = arrayOf(
        song("l1", "Sawaar Loon", "Lootera"),
        song("l2", "Shikayatein", "Lootera"),
        song("l3", "Manmarziyan", "Lootera"),
        song("l4", "Zinda", "Lootera"),
        song("l5", "Ankahee", "Lootera"),
        song("l6", "Monta Re", "Lootera"),
    )

    private fun song(id: String, title: String, film: String) =
        SearchSongDto(id, title, "f_" + film.lowercase(), film, 2013)
}
