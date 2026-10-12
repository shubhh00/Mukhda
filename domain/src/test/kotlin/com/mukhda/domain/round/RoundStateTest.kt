package com.mukhda.domain.round

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import com.mukhda.domain.Outcome
import com.mukhda.domain.RoundError
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.model.Verdict
import org.junit.jupiter.api.Test

class RoundStateTest {

    private var nextId = 0
    private fun guess(result: TryResult, id: String = "r${nextId++}") =
        TryRecord(id, TryType.GUESS, "s_$nextId", result)
    private fun skip(id: String = "r${nextId++}") = TryRecord(id, TryType.SKIP, null, TryResult.SKIPPED)

    private fun RoundState.play(vararg records: TryRecord): RoundState =
        records.fold(this) { state, record ->
            when (val outcome = state.apply(record)) {
                is Outcome.Success -> outcome.data
                is Outcome.Error -> error("expected success, got ${outcome.error}")
            }
        }

    @Test
    fun `new round starts at step 0 with five tries`() {
        val round = RoundState(Tier.HARD)
        assertThat(round.currentStep).isEqualTo(0)
        assertThat(round.triesLeft).isEqualTo(5)
        assertThat(round.status).isEqualTo(RoundStatus.OPEN)
    }

    @Test
    fun `wrong guess, right film and skip each advance the clip step`() {
        val round = RoundState(Tier.HARD).play(guess(TryResult.WRONG), guess(TryResult.RIGHT_FILM), skip())
        assertThat(round.currentStep).isEqualTo(3)
        assertThat(round.triesLeft).isEqualTo(2)
        assertThat(round.status).isEqualTo(RoundStatus.OPEN)
    }

    @Test
    fun `correct guess wins at the current step`() {
        val round = RoundState(Tier.HARD).play(guess(TryResult.WRONG), skip(), guess(TryResult.CORRECT))
        assertThat(round.status).isEqualTo(RoundStatus.WON)
        assertThat(round.solvedAtStep).isEqualTo(2)
        assertThat(round.verdict).isEqualTo(Verdict.HIT)
    }

    @Test
    fun `first-try win is a blockbuster`() {
        assertThat(RoundState(Tier.EASY).play(guess(TryResult.CORRECT)).verdict).isEqualTo(Verdict.BLOCKBUSTER)
    }

    @Test
    fun `win on the fifth try is a flop`() {
        val round = RoundState(Tier.EASY).play(skip(), skip(), skip(), skip(), guess(TryResult.CORRECT))
        assertThat(round.status).isEqualTo(RoundStatus.WON)
        assertThat(round.verdict).isEqualTo(Verdict.FLOP)
    }

    @Test
    fun `five misses lose the round`() {
        val round = RoundState(Tier.LEGEND).play(
            guess(TryResult.WRONG), guess(TryResult.RIGHT_FILM), skip(), skip(), guess(TryResult.WRONG),
        )
        assertThat(round.status).isEqualTo(RoundStatus.LOST)
        assertThat(round.verdict).isEqualTo(Verdict.DISASTER)
        assertThat(round.currentStep).isEqualTo(4)
    }

    @Test
    fun `same requestId is applied once`() {
        val first = guess(TryResult.WRONG, id = "same")
        val round = RoundState(Tier.MEDIUM).play(first)
        val again = round.apply(first.copy(result = TryResult.CORRECT))
        assertThat((again as Outcome.Success).data).isSameInstanceAs(round)
        assertThat(round.triesUsed).isEqualTo(1)
    }

    @Test
    fun `finished round rejects more tries`() {
        val won = RoundState(Tier.MEDIUM).play(guess(TryResult.CORRECT))
        assertThat(won.apply(skip())).isEqualTo(Outcome.Error(RoundError.ALREADY_FINISHED))
    }

    @Test
    fun `inconsistent try is rejected`() {
        val round = RoundState(Tier.MEDIUM)
        assertThat(round.apply(TryRecord("x", TryType.SKIP, null, TryResult.WRONG)))
            .isEqualTo(Outcome.Error(RoundError.INCONSISTENT_TRY))
        assertThat(round.apply(TryRecord("y", TryType.GUESS, null, TryResult.WRONG)))
            .isEqualTo(Outcome.Error(RoundError.INCONSISTENT_TRY))
    }

    @Test
    fun `midnight marks a started open round unfinished`() {
        val closed = RoundState(Tier.HARD).play(skip()).closeForDay()
        assertThat(closed.status).isEqualTo(RoundStatus.UNFINISHED)
        assertThat(closed.verdict).isNull()
    }

    @Test
    fun `midnight leaves untouched and finished rounds alone`() {
        val untouched = RoundState(Tier.HARD)
        assertThat(untouched.closeForDay()).isSameInstanceAs(untouched)
        val won = RoundState(Tier.HARD).play(guess(TryResult.CORRECT))
        assertThat(won.closeForDay()).isSameInstanceAs(won)
    }
}
