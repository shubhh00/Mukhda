package com.mukhda.data.audio

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class LeadInDetectorTest {

    private fun levels(vararg runs: Pair<Int, Double>): List<Double> = runs.flatMap { (count, level) -> List(count) { level } }

    @Test
    fun `music from the first window has no lead-in`() {
        assertThat(LeadInDetector.leadInMs(levels(400 to 4000.0))).isEqualTo(0L)
    }

    @Test
    fun `two seconds of hiss before the music is skipped, plus the safety margin`() {
        // 84 windows of 25 ms = 2.1 s of hiss at ~30, then music at ~5000.
        assertThat(LeadInDetector.leadInMs(levels(84 to 30.0, 400 to 5000.0))).isEqualTo(2_100L + 50)
    }

    @Test
    fun `a soft intro counts as music once it reaches a tenth of the loud parts`() {
        assertThat(LeadInDetector.leadInMs(levels(20 to 30.0, 40 to 600.0, 400 to 5000.0))).isEqualTo(500L + 50)
    }

    @Test
    fun `a preview that is quiet throughout is left alone`() {
        assertThat(LeadInDetector.leadInMs(levels(10 to 0.0, 400 to 200.0))).isEqualTo(0L)
    }

    @Test
    fun `nothing decoded means no lead-in`() {
        assertThat(LeadInDetector.leadInMs(emptyList())).isEqualTo(0L)
    }
}
