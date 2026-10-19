package com.mukhda.data.round

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RoundDao {
    @Upsert
    suspend fun upsert(round: RoundEntity)

    @Query("SELECT * FROM rounds WHERE date = :date")
    fun observeRounds(date: String): Flow<List<RoundEntity>>

    @Query("SELECT * FROM rounds WHERE date = :date AND tier = :tier")
    suspend fun round(date: String, tier: String): RoundEntity?

    @Query("SELECT date, triesUsed FROM rounds")
    fun observeDayRounds(): Flow<List<DayRoundTuple>>
}

@Dao
interface TryDao {
    /** Ignores a second insert with the same requestId: one request, one try. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(attempt: TryEntity)

    @Query("SELECT * FROM tries WHERE date = :date AND tier = :tier ORDER BY createdAt")
    fun observeTries(date: String, tier: String): Flow<List<TryEntity>>

    /** Tries the server hasn't confirmed yet; resent first on the next launch. */
    @Query("SELECT * FROM tries WHERE state = 'PENDING' ORDER BY createdAt")
    suspend fun pending(): List<TryEntity>

    /** One round's tries the server hasn't confirmed yet, oldest first. */
    @Query("SELECT * FROM tries WHERE state = 'PENDING' AND date = :date AND tier = :tier ORDER BY createdAt")
    suspend fun pendingFor(date: String, tier: String): List<TryEntity>

    @Query("UPDATE tries SET state = :state, result = :result WHERE requestId = :requestId")
    suspend fun updateState(requestId: String, state: String, result: String?)
}
