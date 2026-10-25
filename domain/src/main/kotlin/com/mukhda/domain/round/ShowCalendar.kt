package com.mukhda.domain.round

import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Show-day arithmetic. Every input comes from the server (dates, `endsAt`, timestamps);
 * nothing here reads the device clock.
 */
object ShowCalendar {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")

    fun istDateOf(instant: Instant): LocalDate = instant.atZone(IST).toLocalDate()

    /** showNo = (IST date − launch date) + 1. */
    fun showNo(date: LocalDate, launchDate: LocalDate): Int {
        require(!date.isBefore(launchDate)) { "date $date is before launch $launchDate" }
        return ChronoUnit.DAYS.between(launchDate, date).toInt() + 1
    }
}

/**
 * "Housefull streak": consecutive IST days with at least one try in any tier.
 *
 * If today has no try yet, the streak through yesterday still counts — it only breaks
 * once a whole show day passes without a try.
 */
object Streak {
    fun current(playedDates: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in playedDates) today else today.minusDays(1)
        var count = 0
        while (day in playedDates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    /** The longest run of consecutive played days, ever. */
    fun best(playedDates: Set<LocalDate>): Int =
        playedDates.maxOfOrNull { day ->
            // Count only from the first day of each run, so every run is walked once.
            if (day.minusDays(1) in playedDates) 0 else generateSequence(day) { it.plusDays(1) }.takeWhile { it in playedDates }.count()
        } ?: 0

    /** Days that count toward the streak. UNFINISHED rounds count; untouched rounds don't. */
    fun playedDates(rounds: Iterable<DayRound>): Set<LocalDate> =
        rounds.filter { it.triesUsed > 0 }.mapTo(mutableSetOf()) { it.date }

    data class DayRound(val date: LocalDate, val triesUsed: Int)
}

/** "Next: Expert" after a round ends. Suggests, never forces. */
object TierSuggestion {
    fun nextUnplayed(current: Tier, statusByTier: Map<Tier, RoundStatus>): Tier? {
        val tiers = Tier.entries
        val start = tiers.indexOf(current)
        return (1 until tiers.size)
            .map { tiers[(start + it) % tiers.size] }
            .firstOrNull { statusByTier[it]?.isFinished != true }
    }
}
