package com.mukhda.data.di

import android.content.Context
import androidx.room.Room
import com.mukhda.data.db.MukhdaDatabase
import com.mukhda.data.round.RoundDao
import com.mukhda.data.round.TryDao
import com.mukhda.data.search.BundledSearchIndexAsset
import com.mukhda.data.search.RoomSongIndexDataSource
import com.mukhda.data.search.SearchIndexAsset
import com.mukhda.data.search.SearchIndexDao
import com.mukhda.data.show.StandInShowRepository
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.show.ShowRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MukhdaDatabase =
        Room.databaseBuilder(context, MukhdaDatabase::class.java, MukhdaDatabase.NAME).build()

    @Provides
    fun roundDao(db: MukhdaDatabase): RoundDao = db.roundDao()

    @Provides
    fun tryDao(db: MukhdaDatabase): TryDao = db.tryDao()

    @Provides
    fun searchIndexDao(db: MukhdaDatabase): SearchIndexDao = db.searchIndexDao()

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {

    @Binds
    abstract fun songIndex(source: RoomSongIndexDataSource): SongIndexDataSource

    @Binds
    abstract fun searchIndexAsset(asset: BundledSearchIndexAsset): SearchIndexAsset

    // Swapped for the Firebase-backed repository once the Cloud Functions exist.
    @Binds
    abstract fun showRepository(repository: StandInShowRepository): ShowRepository
}
