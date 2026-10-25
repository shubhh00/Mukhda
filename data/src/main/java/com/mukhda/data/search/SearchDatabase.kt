package com.mukhda.data.search

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The search index, in a database of its own: it ships with the app ready built
 * (`assets/search_index/v<N>.db`, made by the curation tool's `index` command from this schema), so
 * a first launch copies a file rather than parsing and inserting thousands of songs. The player's
 * rounds and tries stay in [com.mukhda.data.db.MukhdaDatabase], so replacing the index never
 * touches them.
 */
@Database(
    entities = [SearchSongEntity::class, SearchSongFtsEntity::class, SearchIndexMetaEntity::class],
    version = 1,
)
abstract class SearchDatabase : RoomDatabase() {
    abstract fun searchIndexDao(): SearchIndexDao

    companion object {
        const val NAME = "search_index.db"
    }
}

/** Opens the search index, once. An interface so tests can hand over an in-memory index. */
interface SearchIndexStore {
    /** The index's DAO. The first call opens it, which may copy the bundled file: IO only. */
    suspend fun dao(): SearchIndexDao
}

/**
 * Opens [SearchDatabase] from the newest bundled file. An app update that bundles a newer index
 * replaces the one on the phone: the old file is deleted and the new one copied in.
 */
@Singleton
class BundledSearchIndexStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val asset: SearchIndexAsset,
) : SearchIndexStore {
    private val mutex = Mutex()
    private var database: SearchDatabase? = null

    override suspend fun dao(): SearchIndexDao = mutex.withLock { (database ?: open().also { database = it }).searchIndexDao() }

    private suspend fun open(): SearchDatabase {
        val bundled = checkNotNull(asset.latestVersion()) { "No search index bundled" }
        val opened = build(bundled)
        // Room opens the file on its first query. A file it can't open (corrupt, or from an older
        // schema) is replaceable data: dropped and copied again from the bundle.
        val version = try {
            opened.searchIndexDao().version()
        } catch (e: CancellationException) {
            opened.close()
            throw e
        } catch (e: Exception) {
            null
        }
        if (version == bundled) return opened
        opened.close()
        context.deleteDatabase(SearchDatabase.NAME)
        return build(bundled)
    }

    private fun build(version: Int): SearchDatabase =
        Room.databaseBuilder(context, SearchDatabase::class.java, SearchDatabase.NAME)
            .createFromAsset(asset.path(version))
            .build()
}
