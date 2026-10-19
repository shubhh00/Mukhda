package com.mukhda.data.show

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.mukhda.data.firebase.decodeCallable
import com.mukhda.domain.audio.Preview
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class ShowMappersTest {

    /** A getShow result as the Firebase SDK hands it over: maps, lists, Integers, nulls. */
    private val callableResult: Map<String, Any?> = mapOf(
        "date" to "2026-10-19",
        "showNo" to 1,
        "endsAt" to "2026-10-19T18:30:00.000Z",
        "tiers" to mapOf(
            "EASY" to mapOf(
                "previewUrl" to "https://audio.example/a.m4a",
                "startOffsetMs" to 0,
                "status" to "WON",
                "triesUsed" to 2,
                "currentStep" to 2,
                "tries" to listOf(
                    mapOf("requestId" to "r1", "type" to "SKIP", "songId" to null, "result" to "SKIPPED"),
                    mapOf("requestId" to "r2", "type" to "GUESS", "songId" to "s_1", "result" to "CORRECT"),
                ),
                "reveal" to mapOf(
                    "songId" to "s_1",
                    "title" to "Kajra Re",
                    "filmTitle" to "Bunty Aur Babli",
                    "year" to 2005,
                    "composer" to "Shankar-Ehsaan-Loy",
                    "artworkUrl" to null,
                    "appleMusicUrl" to "https://music.apple.com/x",
                    "topPercent" to 12,
                ),
            ),
            "HARD" to mapOf(
                "previewUrl" to "https://audio.example/c.m4a",
                "startOffsetMs" to 1500,
                "status" to "OPEN",
                "triesUsed" to 0,
                "currentStep" to 0,
                "tries" to emptyList<Any>(),
                "reveal" to null,
            ),
            // A tier this app doesn't know yet.
            "MYTHIC" to mapOf("previewUrl" to "x", "status" to "OPEN", "triesUsed" to 0, "currentStep" to 0),
        ),
        "addedLater" to true,
    )

    @Test
    fun `a callable result becomes the show, rounds and previews`() {
        val show = decodeCallable<ShowDto>(callableResult).toShow()

        assertThat(show.date).isEqualTo(LocalDate.of(2026, 10, 19))
        assertThat(show.showNo).isEqualTo(1)
        assertThat(show.endsAt).isEqualTo(Instant.parse("2026-10-19T18:30:00Z"))
        assertThat(show.rounds.keys).containsOnly(Tier.EASY, Tier.HARD)
        assertThat(show.previews.getValue(Tier.HARD)).isEqualTo(Preview("https://audio.example/c.m4a", 1500))
    }

    @Test
    fun `a won round keeps its tries, the step it was won at, and the reveal`() {
        val easy = decodeCallable<ShowDto>(callableResult).toShow().rounds.getValue(Tier.EASY)

        assertThat(easy.status).isEqualTo(RoundStatus.WON)
        assertThat(easy.tries.map { it.requestId to it.result }).isEqualTo(listOf("r1" to TryResult.SKIPPED, "r2" to TryResult.CORRECT))
        assertThat(easy.tries[1].type).isEqualTo(TryType.GUESS)
        assertThat(easy.solvedAtStep).isEqualTo(1)
        assertThat(easy.reveal?.title).isEqualTo("Kajra Re")
        assertThat(easy.reveal?.composer).isEqualTo("Shankar-Ehsaan-Loy")
        assertThat(easy.reveal?.topPercent).isEqualTo(12)
    }

    @Test
    fun `an open round has no reveal and no solved step`() {
        val hard = decodeCallable<ShowDto>(callableResult).toShow().rounds.getValue(Tier.HARD)

        assertThat(hard.reveal).isNull()
        assertThat(hard.solvedAtStep).isNull()
        assertThat(hard.status).isEqualTo(RoundStatus.OPEN)
    }

    @Test
    fun `a submitTry result is read too`() {
        val judged = decodeCallable<SubmitTryDto>(
            mapOf("result" to "RIGHT_FILM", "triesUsed" to 3, "currentStep" to 3, "status" to "OPEN", "reveal" to null),
        )
        assertThat(judged).isEqualTo(SubmitTryDto("RIGHT_FILM", 3, 3, "OPEN", null))
    }
}
