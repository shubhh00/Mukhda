package com.mukhda.domain.show

import com.mukhda.domain.AppError
import com.mukhda.domain.DataError
import com.mukhda.domain.GameError
import com.mukhda.domain.Outcome
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import java.time.Instant
import java.time.LocalDate

/** Today's show as the server describes it. Never contains the answer to an open round. */
data class Show(
    /** IST show date, from the server. */
    val date: LocalDate,
    val showNo: Int,
    /** Midnight IST that closes the show, from the server. */
    val endsAt: Instant,
    val rounds: Map<Tier, RoundState>,
)

/** Why a try wasn't accepted: the request failed, or the game's rules refused it. */
sealed interface TryError : AppError {
    data class Data(val error: DataError) : TryError
    data class Rule(val error: GameError) : TryError
}

interface ShowRepository {
    suspend fun todaysShow(): Outcome<Show, DataError>

    /**
     * Sends one try. [requestId] is generated once per try and reused on retries, so the server
     * never counts a try twice. Returns the round as the server judged it.
     */
    suspend fun submitTry(
        date: LocalDate,
        tier: Tier,
        requestId: String,
        type: TryType,
        songId: String?,
    ): Outcome<RoundState, TryError>
}
