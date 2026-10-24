package com.mukhda.data.search

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens the bundled search index exactly as the app does (`createFromAsset`), on a device's own
 * SQLite: Room accepts the file the curation tool built, and its FTS finds songs.
 */
@RunWith(AndroidJUnit4::class)
class BundledSearchIndexTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val asset = BundledSearchIndexAsset(context)
    private lateinit var db: SearchDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(NAME)
        val version = assertThat(asset.latestVersion()).isNotNull().let { asset.latestVersion()!! }
        db = Room.databaseBuilder(context, SearchDatabase::class.java, NAME).createFromAsset(asset.path(version)).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun theBundledIndexOpensAndSearches() = runTest {
        val dao = db.searchIndexDao()
        assertThat(dao.version()).isEqualTo(asset.latestVersion())
        assertThat(dao.all().size).isGreaterThan(5_000)
        val hits = dao.matchPrefix("tum* hi*")
        assertThat(hits.isNotEmpty()).isTrue()
        assertThat(hits.all { "tum" in "${it.titleNorm} ${it.filmNorm}" }).isTrue()
    }

    private companion object {
        const val NAME = "bundled_index_test.db"
    }
}
