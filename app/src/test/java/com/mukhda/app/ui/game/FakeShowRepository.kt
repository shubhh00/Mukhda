package com.mukhda.app.ui.game

import com.mukhda.domain.DataError
import com.mukhda.domain.Outcome
import com.mukhda.domain.audio.Preview
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.SongReveal
import com.mukhda.domain.round.TryRecord
import com.mukhda.domain.show.Show
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.show.TryError
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.time.LocalDate

/** In-memory server. Tests decide how guesses are judged with [judge]. */
class FakeShowRepository : ShowRepository {
    val date: LocalDate = LocalDate.of(2026, 10, 13)
    var rounds = Tier.entries.associateWith { RoundState(it) }
    val previews = Tier.entries.associateWith { Preview("https://example.com/${it.name.lowercase()}.m4a") }
    var loadError: DataError? = null
    var nextTryError: TryError? = null
    var judge: (songId: String) -> TryResult = { TryResult.WRONG }
    /** When set, tries wait for it before the "server" answers, to see the screen in between. */
    var answerGate: CompletableDeferred<Unit>? = null
    val requestIds = mutableListOf<String>()
    var loads = 0
        private set
    val reveal = SongReveal("s7", "Kal Ho Naa Ho", "Kal Ho Naa Ho", 2003, "https://example.com/art.jpg", "https://music.apple.com/x")

    override suspend fun todaysShow(): Outcome<Show, DataError> {
        loads++
        loadError?.let { return Outcome.Error(it) }
        return Outcome.Success(
            Show(date, showNo = 12, endsAt = Instant.parse("2026-10-13T18:30:00Z"), rounds = rounds, previews = previews),
        )
    }

    override suspend fun submitTry(
        date: LocalDate,
        tier: Tier,
        requestId: String,
        type: TryType,
        songId: String?,
    ): Outcome<RoundState, TryError> {
        requestIds += requestId
        answerGate?.await()
        nextTryError?.let {
            nextTryError = null
            return Outcome.Error(it)
        }
        val result = if (type == TryType.SKIP) TryResult.SKIPPED else judge(songId!!)
        val applied = rounds.getValue(tier).apply(TryRecord(requestId, type, songId, result))
        val judged = (applied as Outcome.Success).data
        // Like the server: the song is revealed once the round is over, never before.
        val round = if (judged.status.isFinished) judged.copy(reveal = reveal) else judged
        rounds = rounds + (tier to round)
        return Outcome.Success(round)
    }
}
