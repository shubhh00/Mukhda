package com.mukhda.data.round

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.round.Streak
import org.junit.jupiter.api.Test
import java.time.LocalDate

class RoundMappersTest {

    private val round = RoundEntity(
        date = "2026-10-12",
        tier = "HARD",
        songId = null,
        status = "OPEN",
        triesUsed = 2,
        solvedAtStep = null,
        startedAt = 0,
        endedAt = null,
    )

    private fun attempt(id: String, createdAt: Long, state: TryState, result: String?) = TryEntity(
        requestId = id,
        date = "2026-10-12",
        tier = "HARD",
        type = if (result == "SKIPPED") "SKIP" else "GUESS",
        guessedSongId = if (result == "SKIPPED") null else "s_$id",
        result = result,
        state = state.name,
        createdAt = createdAt,
    )

    @Test
    fun `round is built from confirmed tries in the order they were made`() {
        val state = round.toRoundState(
            listOf(
                attempt("b", createdAt = 20, TryState.CONFIRMED, "RIGHT_FILM"),
                attempt("p", createdAt = 30, TryState.PENDING, null),
                attempt("a", createdAt = 10, TryState.CONFIRMED, "SKIPPED"),
                attempt("f", createdAt = 40, TryState.FAILED, null),
            )
        )

        assertThat(state.tier).isEqualTo(Tier.HARD)
        assertThat(state.status).isEqualTo(RoundStatus.OPEN)
        assertThat(state.tries.map { it.requestId }).containsExactly("a", "b")
        assertThat(state.tries.map { it.result }).containsExactly(TryResult.SKIPPED, TryResult.RIGHT_FILM)
        assertThat(state.currentStep).isEqualTo(2)
    }

    @Test
    fun `day tuple maps to a streak day`() {
        assertThat(DayRoundTuple("2026-10-12", 3).toDayRound())
            .isEqualTo(Streak.DayRound(LocalDate.of(2026, 10, 12), 3))
    }
}
