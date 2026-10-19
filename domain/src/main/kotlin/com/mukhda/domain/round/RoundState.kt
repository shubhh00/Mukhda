package com.mukhda.domain.round

import com.mukhda.domain.Outcome
import com.mukhda.domain.RoundError
import com.mukhda.domain.model.ClipLadder
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.model.Verdict

data class TryRecord(
    val requestId: String,
    val type: TryType,
    val guessedSongId: String?,
    val result: TryResult,
)

/**
 * The answer, as the server reveals it once a round is won or lost. Never present for an open
 * round, so nothing on the client can give the song away early.
 */
data class SongReveal(
    val songId: String,
    val title: String,
    val filmTitle: String,
    val year: Int,
    /** Soundtrack cover, usually the film's poster. */
    val artworkUrl: String?,
    /** The track on Apple Music, for "Listen on Apple Music". */
    val appleMusicUrl: String?,
    val composer: String? = null,
    /** "Top X% today": the share of the day's finished players who did at least as well. */
    val topPercent: Int? = null,
)

/**
 * One player's round for one tier on one show day.
 *
 * The server judges every try; this applies the judged result so the client can render
 * state and stay consistent with the server's rules. It never knows the answer while the round
 * is open: [reveal] arrives from the server only once the round is over.
 */
data class RoundState(
    val tier: Tier,
    val tries: List<TryRecord> = emptyList(),
    val status: RoundStatus = RoundStatus.OPEN,
    val solvedAtStep: Int? = null,
    val reveal: SongReveal? = null,
) {
    val triesUsed: Int get() = tries.size
    val triesLeft: Int get() = ClipLadder.MAX_TRIES - triesUsed
    val isStarted: Boolean get() = tries.isNotEmpty()

    /** Clip step the player is on: one step per used try, capped at the full preview. */
    val currentStep: Int get() = minOf(triesUsed, ClipLadder.LAST_STEP)

    val verdict: Verdict? get() = Verdict.forRound(status, solvedAtStep)

    /**
     * Applies a judged try. A `requestId` that's already in the round is a no-op that
     * returns this same state, so a retried request never uses a second try.
     */
    fun apply(record: TryRecord): Outcome<RoundState, RoundError> {
        if (tries.any { it.requestId == record.requestId }) return Outcome.Success(this)
        if (status != RoundStatus.OPEN) return Outcome.Error(RoundError.ALREADY_FINISHED)
        if (triesUsed >= ClipLadder.MAX_TRIES) return Outcome.Error(RoundError.TRIES_EXHAUSTED)
        if (!record.isConsistent()) return Outcome.Error(RoundError.INCONSISTENT_TRY)

        val step = currentStep
        val newTries = tries + record
        val next = when {
            record.result == TryResult.CORRECT ->
                copy(tries = newTries, status = RoundStatus.WON, solvedAtStep = step)
            newTries.size >= ClipLadder.MAX_TRIES ->
                copy(tries = newTries, status = RoundStatus.LOST)
            else -> copy(tries = newTries)
        }
        return Outcome.Success(next)
    }

    /** Midnight IST: a started, open round becomes UNFINISHED. Untouched rounds stay as they are. */
    fun closeForDay(): RoundState =
        if (status == RoundStatus.OPEN && isStarted) copy(status = RoundStatus.UNFINISHED) else this

    private fun TryRecord.isConsistent(): Boolean = when (type) {
        TryType.SKIP -> result == TryResult.SKIPPED && guessedSongId == null
        TryType.GUESS -> result != TryResult.SKIPPED && guessedSongId != null
    }
}
