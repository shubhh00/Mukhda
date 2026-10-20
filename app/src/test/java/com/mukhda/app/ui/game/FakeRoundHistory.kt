package com.mukhda.app.ui.game

import com.mukhda.domain.round.RoundHistory
import com.mukhda.domain.round.Streak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Rounds kept on the phone; tests set [rounds] to what the player has played. */
class FakeRoundHistory : RoundHistory {
    val rounds = MutableStateFlow<List<Streak.DayRound>>(emptyList())
    override fun dayRounds(): Flow<List<Streak.DayRound>> = rounds
}
