package com.mukhda.data.round

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local copy of one round. Feeds the stats sheet and the streak; the server copy wins on conflict.
 * Enums are stored by name so renaming a Kotlin constant can't silently reinterpret old rows.
 */
@Entity(tableName = "rounds", primaryKeys = ["date", "tier"])
data class RoundEntity(
    /** IST show date from the server, `yyyy-mm-dd`. */
    val date: String,
    val tier: String,
    /** Only set once the round is revealed. Never stored for an open round. */
    val songId: String?,
    val status: String,
    val triesUsed: Int,
    val solvedAtStep: Int?,
    val startedAt: Long,
    val endedAt: Long?,
)

@Entity(tableName = "tries", indices = [Index("date", "tier")])
data class TryEntity(
    /** Client-generated; resent unchanged on retry so the server never counts a try twice. */
    @PrimaryKey val requestId: String,
    val date: String,
    val tier: String,
    val type: String,
    val guessedSongId: String?,
    /** Null until the server has judged the try. */
    val result: String?,
    val state: String,
    /** Device time, used only to keep tries in the order they were made. */
    val createdAt: Long,
)

enum class TryState { PENDING, CONFIRMED, FAILED }

/** Just the columns the streak needs. */
data class DayRoundTuple(val date: String, val triesUsed: Int)
