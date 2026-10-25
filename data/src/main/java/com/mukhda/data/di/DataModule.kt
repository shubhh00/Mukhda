package com.mukhda.data.di

import android.content.Context
import androidx.room.Room
import com.mukhda.data.audio.Media3ClipPlayer
import com.mukhda.data.connectivity.AndroidConnectivityMonitor
import com.mukhda.data.db.MukhdaDatabase
import com.mukhda.data.round.RoomRoundHistory
import com.mukhda.data.round.RoundDao
import com.mukhda.data.round.TryDao
import com.mukhda.data.search.BundledSearchIndexAsset
import com.mukhda.data.search.BundledSearchIndexStore
import com.mukhda.data.search.RoomSongIndexDataSource
import com.mukhda.data.search.SearchIndexAsset
import com.mukhda.data.search.SearchIndexStore
import com.mukhda.data.show.FirebaseShowRepository
import com.mukhda.data.telemetry.FirebaseTelemetry
import com.mukhda.data.telemetry.RemoteConfigFeatureFlags
import com.mukhda.domain.audio.ClipPlayer
import com.mukhda.domain.connectivity.ConnectivityMonitor
import com.mukhda.domain.round.RoundHistory
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.telemetry.FeatureFlags
import com.mukhda.domain.telemetry.Telemetry
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
        Room.databaseBuilder(context, MukhdaDatabase::class.java, MukhdaDatabase.NAME)
            .addMigrations(MukhdaDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun roundDao(db: MukhdaDatabase): RoundDao = db.roundDao()

    @Provides
    fun tryDao(db: MukhdaDatabase): TryDao = db.tryDao()

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
    abstract fun clipPlayer(player: Media3ClipPlayer): ClipPlayer

    @Binds
    abstract fun searchIndexAsset(asset: BundledSearchIndexAsset): SearchIndexAsset

    @Binds
    abstract fun searchIndexStore(store: BundledSearchIndexStore): SearchIndexStore

    @Binds
    abstract fun showRepository(repository: FirebaseShowRepository): ShowRepository

    @Binds
    abstract fun roundHistory(history: RoomRoundHistory): RoundHistory

    @Binds
    abstract fun telemetry(telemetry: FirebaseTelemetry): Telemetry

    @Binds
    abstract fun connectivity(monitor: AndroidConnectivityMonitor): ConnectivityMonitor

    @Binds
    abstract fun featureFlags(flags: RemoteConfigFeatureFlags): FeatureFlags
}
