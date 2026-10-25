package com.mukhda.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.mukhda.data.round.TryEntity
import com.mukhda.data.round.TryState
import com.mukhda.data.search.SearchDatabase
import com.mukhda.data.search.SearchSongDto
import com.mukhda.data.search.toEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device: checks the real SQLite FTS4 behaviour the JVM fakes only imitate. */
@RunWith(AndroidJUnit4::class)
class MukhdaDatabaseTest {

    private lateinit var db: MukhdaDatabase
    private lateinit var search: SearchDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, MukhdaDatabase::class.java).build()
        search = Room.inMemoryDatabaseBuilder(context, SearchDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
        search.close()
    }

    private fun song(id: String, title: String, film: String) =
        SearchSongDto(id, title, "f_" + film.lowercase(), film, 2013).toEntity()

    @Test
    fun ftsMatchesEveryTermAsAWordPrefixAcrossTitleAndFilm() = runTest {
        val dao = search.searchIndexDao()
        dao.replaceIndex(
            listOf(
                song("s1", "Sawaar Loon", "Lootera"),
                song("s2", "Shikayatein", "Lootera"),
                song("s3", "Tum Hi Ho", "Aashiqui 2"),
            ),
            version = 1,
        )

        assertThat(dao.matchPrefix("lutera* shik*").map { it.songId }).containsExactly("s2")
        assertThat(dao.matchPrefix("lutera*").map { it.songId }).containsExactlyInAnyOrder("s1", "s2")
        assertThat(dao.matchPrefix("tum* hi*").map { it.songId }).containsExactly("s3")
        assertThat(dao.matchPrefix("arijit*")).isEmpty()
    }

    @Test
    fun replacingTheIndexDropsOldSongsAndKeepsFtsInSync() = runTest {
        val dao = search.searchIndexDao()
        dao.replaceIndex(listOf(song("s1", "Tum Hi Ho", "Aashiqui 2")), version = 1)
        dao.replaceIndex(listOf(song("s2", "Kesariya", "Brahmastra Part One: Shiva")), version = 2)

        assertThat(dao.version()).isEqualTo(2)
        assertThat(dao.all().map { it.songId }).containsExactly("s2")
        assertThat(dao.matchPrefix("tum*")).isEmpty()
        assertThat(dao.matchPrefix("kesariya*").map { it.songId }).containsExactly("s2")
    }

    @Test
    fun sameRequestIdIsStoredOnceAndPendingTriesCanBeConfirmed() = runTest {
        val dao = db.tryDao()
        val attempt = TryEntity(
            requestId = "req-1",
            date = "2026-10-12",
            tier = "HARD",
            type = "SKIP",
            guessedSongId = null,
            result = null,
            state = TryState.PENDING.name,
            createdAt = 1,
        )
        dao.insert(attempt)
        dao.insert(attempt.copy(createdAt = 2))

        assertThat(dao.pending().map { it.requestId }).containsExactly("req-1")

        dao.updateState("req-1", TryState.CONFIRMED.name, "SKIPPED")
        assertThat(dao.pending()).isEmpty()
        dao.observeTries("2026-10-12", "HARD").test {
            assertThat(awaitItem().single().result).isEqualTo("SKIPPED")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
