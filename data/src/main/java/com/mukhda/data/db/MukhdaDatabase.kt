package com.mukhda.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mukhda.data.round.RoundDao
import com.mukhda.data.round.RoundEntity
import com.mukhda.data.round.TryDao
import com.mukhda.data.round.TryEntity

/** The player's own data: rounds and tries. The search index has its own database. */
@Database(
    entities = [
        RoundEntity::class,
        TryEntity::class,
    ],
    version = 2,
)
abstract class MukhdaDatabase : RoomDatabase() {
    abstract fun roundDao(): RoundDao
    abstract fun tryDao(): TryDao

    companion object {
        const val NAME = "mukhda.db"

        /** Version 2 moved the search index to [com.mukhda.data.search.SearchDatabase]; rounds and tries stay. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (moment in listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")) {
                    db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_search_songs_fts_$moment")
                }
                db.execSQL("DROP TABLE IF EXISTS search_songs_fts")
                db.execSQL("DROP TABLE IF EXISTS search_songs")
                db.execSQL("DROP TABLE IF EXISTS search_index_meta")
            }
        }
    }
}
