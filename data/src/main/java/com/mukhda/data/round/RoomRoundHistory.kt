package com.mukhda.data.round

import com.mukhda.domain.round.RoundHistory
import com.mukhda.domain.round.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The rounds the show repository saves as tries are confirmed. */
class RoomRoundHistory @Inject constructor(private val roundDao: RoundDao) : RoundHistory {
    override fun dayRounds(): Flow<List<Streak.DayRound>> =
        roundDao.observeDayRounds().map { rounds -> rounds.map { it.toDayRound() } }
}
