package com.mukhda.domain.round

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.round.Streak.DayRound
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class ShowCalendarTest {

    private val oct12: LocalDate = LocalDate.of(2026, 10, 12)

    @Test
    fun `IST day flips at 18-30 UTC`() {
        assertThat(ShowCalendar.istDateOf(Instant.parse("2026-10-12T18:29:59Z"))).isEqualTo(LocalDate.of(2026, 10, 12))
        assertThat(ShowCalendar.istDateOf(Instant.parse("2026-10-12T18:30:00Z"))).isEqualTo(LocalDate.of(2026, 10, 13))
    }

    @Test
    fun `a show ends at the next midnight IST`() {
        assertThat(ShowCalendar.showEnd(oct12)).isEqualTo(Instant.parse("2026-10-12T18:30:00Z"))
    }

    @Test
    fun `show number counts from launch day as 1`() {
        val launch = LocalDate.of(2026, 10, 1)
        assertThat(ShowCalendar.showNo(launch, launch)).isEqualTo(1)
        assertThat(ShowCalendar.showNo(oct12, launch)).isEqualTo(12)
    }

    @Test
    fun `show number before launch is rejected`() {
        assertFailure { ShowCalendar.showNo(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1)) }
            .isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `streak counts consecutive played days ending today`() {
        val played = setOf(oct12, oct12.minusDays(1), oct12.minusDays(2), oct12.minusDays(4))
        assertThat(Streak.current(played, oct12)).isEqualTo(3)
    }

    @Test
    fun `streak through yesterday survives until today is over`() {
        assertThat(Streak.current(setOf(oct12.minusDays(1), oct12.minusDays(2)), oct12)).isEqualTo(2)
    }

    @Test
    fun `best run is the longest run of played days, broken or not`() {
        val played = setOf(oct12, oct12.minusDays(1)) + (5L..8L).map { oct12.minusDays(it) }
        assertThat(Streak.best(played)).isEqualTo(4)
        assertThat(Streak.best(emptySet())).isEqualTo(0)
        assertThat(Streak.best(setOf(oct12))).isEqualTo(1)
    }

    @Test
    fun `missing a whole day breaks the streak`() {
        assertThat(Streak.current(setOf(oct12.minusDays(2), oct12.minusDays(3)), oct12)).isEqualTo(0)
    }

    @Test
    fun `streak across IST midnight uses IST dates, not UTC`() {
        // 23:59 IST on the 11th and 00:01 IST on the 12th are the same UTC date but two show days.
        val late = ShowCalendar.istDateOf(Instant.parse("2026-10-11T18:29:00Z"))
        val early = ShowCalendar.istDateOf(Instant.parse("2026-10-11T18:31:00Z"))
        assertThat(Streak.current(setOf(late, early), oct12)).isEqualTo(2)
    }

    @Test
    fun `unfinished rounds count, untouched rounds do not`() {
        val rounds = listOf(
            DayRound(oct12, triesUsed = 0),
            DayRound(oct12.minusDays(1), triesUsed = 2), // UNFINISHED at midnight
            DayRound(oct12.minusDays(2), triesUsed = 5),
        )
        val played = Streak.playedDates(rounds)
        assertThat(played).containsOnly(oct12.minusDays(1), oct12.minusDays(2))
        assertThat(Streak.current(played, oct12)).isEqualTo(2)
    }

    @Test
    fun `next unplayed tier wraps around and skips finished tiers`() {
        val statuses = mapOf(
            Tier.EASY to RoundStatus.OPEN,
            Tier.HARD to RoundStatus.WON,
            Tier.EXPERT to RoundStatus.LOST,
            Tier.LEGEND to RoundStatus.WON,
        )
        assertThat(TierSuggestion.nextUnplayed(Tier.EASY, statuses)).isEqualTo(Tier.MEDIUM)
        assertThat(TierSuggestion.nextUnplayed(Tier.HARD, statuses)).isEqualTo(Tier.EASY)
        assertThat(TierSuggestion.nextUnplayed(Tier.MEDIUM, statuses)).isEqualTo(Tier.EASY)
    }

    @Test
    fun `no suggestion when every other tier is finished`() {
        assertThat(TierSuggestion.nextUnplayed(Tier.EASY, Tier.entries.associateWith { RoundStatus.WON })).isNull()
    }
}
