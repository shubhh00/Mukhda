package com.mukhda.domain.model

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class VerdictTest {

    @Test
    fun `solved step maps to box-office verdict`() {
        assertThat((0..4).map { Verdict.forSolvedStep(it) }).containsExactly(
            Verdict.BLOCKBUSTER, Verdict.SUPERHIT, Verdict.HIT, Verdict.AVERAGE, Verdict.FLOP,
        )
    }

    @Test
    fun `lost round is a disaster`() {
        assertThat(Verdict.forRound(RoundStatus.LOST, null)).isEqualTo(Verdict.DISASTER)
    }

    @Test
    fun `open and unfinished rounds have no verdict`() {
        assertThat(Verdict.forRound(RoundStatus.OPEN, null)).isNull()
        assertThat(Verdict.forRound(RoundStatus.UNFINISHED, null)).isNull()
    }

    @Test
    fun `step beyond the ladder is rejected`() {
        assertFailure { Verdict.forSolvedStep(5) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `clip ladder lengths and labels`() {
        assertThat((0..4).map { ClipLadder.clipLengthMs(it) }).containsExactly(500L, 1_000L, 2_000L, 4_000L, null)
        assertThat((0..4).map { ClipLadder.label(it) }).containsExactly("0.5 sec", "1 sec", "2 sec", "4 sec", "Full")
    }
}
