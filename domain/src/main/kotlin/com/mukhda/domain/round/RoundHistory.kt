package com.mukhda.domain.round

import kotlinx.coroutines.flow.Flow

/**
 * The player's past rounds, as kept on this phone: the source of the housefull streak
 * (AGENTS.md §6.2). Days come from the server, so the device clock never decides a day.
 */
interface RoundHistory {
    /** Every round with its IST date and tries used, updated as tries are confirmed. */
    fun dayRounds(): Flow<List<Streak.DayRound>>
}
