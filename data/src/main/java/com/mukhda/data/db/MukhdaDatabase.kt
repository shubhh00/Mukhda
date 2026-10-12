package com.mukhda.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mukhda.data.round.RoundDao
import com.mukhda.data.round.RoundEntity
import com.mukhda.data.round.TryDao
import com.mukhda.data.round.TryEntity
import com.mukhda.data.search.SearchIndexDao
import com.mukhda.data.search.SearchIndexMetaEntity
import com.mukhda.data.search.SearchSongEntity
import com.mukhda.data.search.SearchSongFtsEntity

@Database(
    entities = [
        RoundEntity::class,
        TryEntity::class,
        SearchSongEntity::class,
        SearchSongFtsEntity::class,
        SearchIndexMetaEntity::class,
    ],
    version = 1,
)
abstract class MukhdaDatabase : RoomDatabase() {
    abstract fun roundDao(): RoundDao
    abstract fun tryDao(): TryDao
    abstract fun searchIndexDao(): SearchIndexDao

    companion object {
        const val NAME = "mukhda.db"
    }
}
