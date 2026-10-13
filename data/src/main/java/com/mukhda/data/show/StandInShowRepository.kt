package com.mukhda.data.show

import com.mukhda.domain.DataError
import com.mukhda.domain.GameError
import com.mukhda.domain.Outcome
import com.mukhda.domain.RoundError
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.ShowCalendar
import com.mukhda.domain.round.TryRecord
import com.mukhda.domain.show.Show
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.show.TryError
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder until the Cloud Functions exist: lets the game screen be played end to end without
 * a server. It knows no answers (none may live on the client), so every guess is judged WRONG and
 * every skip SKIPPED. The show date comes from the phone here only because there's no server yet;
 * the real repository takes it from `getShow()`.
 */
@Singleton
class StandInShowRepository @Inject constructor() : ShowRepository {

    private val date: LocalDate = LocalDate.now(ShowCalendar.IST)
    private val rounds = Tier.entries.associateWith { RoundState(it) }.toMutableMap()

    override suspend fun todaysShow(): Outcome<Show, DataError> = Outcome.Success(
        Show(
            date = date,
            showNo = 1,
            endsAt = date.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(ShowCalendar.IST).toInstant(),
            rounds = rounds.toMap(),
        )
    )

    override suspend fun submitTry(
        date: LocalDate,
        tier: Tier,
        requestId: String,
        type: TryType,
        songId: String?,
    ): Outcome<RoundState, TryError> {
        if (date != this.date) return Outcome.Error(TryError.Rule(GameError.ROUND_CLOSED))
        val result = if (type == TryType.SKIP) TryResult.SKIPPED else TryResult.WRONG
        val round = rounds.getValue(tier)
        return when (val applied = round.apply(TryRecord(requestId, type, songId, result))) {
            is Outcome.Success -> {
                rounds[tier] = applied.data
                Outcome.Success(applied.data)
            }
            is Outcome.Error -> Outcome.Error(
                when (applied.error) {
                    RoundError.ALREADY_FINISHED -> TryError.Rule(GameError.ALREADY_FINISHED)
                    RoundError.TRIES_EXHAUSTED -> TryError.Rule(GameError.TRIES_EXHAUSTED)
                    RoundError.INCONSISTENT_TRY -> TryError.Data(DataError.Network.BAD_REQUEST)
                }
            )
        }
    }
}
