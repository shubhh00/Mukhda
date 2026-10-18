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
    private val asset = FakeSearchIndexAsset()
    private val source = RoomSongIndexDataSource(dao, asset, UnconfinedTestDispatcher())

    private suspend fun ids(query: String): List<String> =
        (source.search(query) as Outcome.Success).data.items.map { it.songId }

    @Test
    fun `first search imports the bundled index once`() = runTest {
        asset.bundle(1, song("s1", "Tum Hi Ho", "Aashiqui 2"))

        assertThat(ids("tum hi")).containsExactly("s1")
        assertThat(ids("tum")).containsExactly("s1")
        assertThat(asset.reads).isEqualTo(1)
        assertThat(dao.version()).isEqualTo(1)
    }

    @Test
    fun `an index already at the bundled version is not reimported`() = runTest {
        dao.replaceIndex(listOf(song("s1", "Tum Hi Ho", "Aashiqui 2").toEntity()), version = 2)
        asset.bundle(2, song("s9", "Something Else", "Other"))

        source.prepare()

        assertThat(asset.reads).isEqualTo(0)
        assertThat(ids("tum hi")).containsExactly("s1")
    }

    @Test
    fun `a newer bundled index replaces the old one`() = runTest {
        dao.replaceIndex(listOf(song("s1", "Tum Hi Ho", "Aashiqui 2").toEntity()), version = 1)
        asset.bundle(2, song("s2", "Kesariya", "Brahmastra Part One: Shiva"))

        source.prepare()

        assertThat(dao.version()).isEqualTo(2)
        assertThat(ids("tum hi")).isEmpty()
        assertThat(ids("kesariya")).containsExactly("s2")
    }

    @Test
    fun `short query returns nothing without touching the database`() = runTest {
        asset.bundle(1, song("s1", "Tum Hi Ho", "Aashiqui 2"))

        assertThat(source.search("t")).isEqualTo(Outcome.Success(SearchResults.EMPTY))
        assertThat(asset.reads).isEqualTo(0)
    }

    @Test
    fun `full page of prefix hits is served from FTS alone`() = runTest {
        asset.bundle(1, *lootera)

        val results = (source.search("lootera") as Outcome.Success).data
        assertThat(results.items.size).isEqualTo(5)
        assertThat(results.totalMatches).isEqualTo(6)
        assertThat(dao.fullScans).isEqualTo(0)
    }

    @Test
    fun `typo falls back to ranking the whole index`() = runTest {
        asset.bundle(1, *lootera)

        assertThat(ids("manmarzian")).containsExactly("l3")
        assertThat(dao.fullScans).isEqualTo(1)
    }

    @Test
    fun `run-together title falls back to ranking the whole index`() = runTest {
        asset.bundle(1, song("s1", "Kal Ho Naa Ho", "Kal Ho Naa Ho"))

        assertThat(ids("kalhonaho")).containsExactly("s1")
    }

    @Test
    fun `broken bundled file is reported, not thrown`() = runTest {
        asset.bundleRaw(1, "{ not json")

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
