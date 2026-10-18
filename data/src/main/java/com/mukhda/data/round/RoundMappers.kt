package com.mukhda.data.round

import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.Streak
import com.mukhda.domain.round.TryRecord
import java.time.LocalDate

/** Builds the round from its confirmed tries. Pending and failed tries aren't part of the round yet. */
fun RoundEntity.toRoundState(tries: List<TryEntity>): RoundState = RoundState(
    tier = Tier.valueOf(tier),
    tries = tries
        .filter { it.state == TryState.CONFIRMED.name && it.result != null }
        .sortedBy { it.createdAt }
        .map { it.toTryRecord() },
    status = RoundStatus.valueOf(status),
    solvedAtStep = solvedAtStep,
)

fun TryEntity.toTryRecord(): TryRecord = TryRecord(
    requestId = requestId,
    type = TryType.valueOf(type),
    guessedSongId = guessedSongId,
    result = TryResult.valueOf(requireNotNull(result) { "try $requestId has no result" }),
)

fun DayRoundTuple.toDayRound(): Streak.DayRound = Streak.DayRound(LocalDate.parse(date), triesUsed)
