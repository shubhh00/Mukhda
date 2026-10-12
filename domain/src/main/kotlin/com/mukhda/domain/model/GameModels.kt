package com.mukhda.domain.model

/** Daily tiers, in display order. */
enum class Tier { EASY, MEDIUM, HARD, EXPERT, LEGEND }

enum class TryType { GUESS, SKIP }

/** Outcome of one try, as decided by the server. */
enum class TryResult {
    CORRECT,
    /** Different song from the right film. Still a wrong try. */
    RIGHT_FILM,
    WRONG,
    SKIPPED;

    val usesTry: Boolean get() = this != CORRECT
}

enum class RoundStatus {
    OPEN,
    WON,
    LOST,
    /** Started but not finished when the show closed at midnight IST. Not a loss. */
    UNFINISHED;

    val isFinished: Boolean get() = this != OPEN
}

/** Clip lengths per step. The last step plays the whole preview. */
object ClipLadder {
    const val STEP_COUNT = 5
    const val LAST_STEP = STEP_COUNT - 1
    const val MAX_TRIES = 5

    private val lengthsMs = longArrayOf(100, 500, 1_000, 2_000)

    /** Clip length for [step], or null for the full preview. */
    fun clipLengthMs(step: Int): Long? {
        require(step in 0..LAST_STEP) { "step out of range: $step" }
        return if (step == LAST_STEP) null else lengthsMs[step]
    }

    /** "0.1 sec", "1 sec", "Full" — used in the ladder label and the share text. */
    fun label(step: Int): String = when (val ms = clipLengthMs(step)) {
        null -> "Full"
        else -> if (ms % 1000 == 0L) "${ms / 1000} sec" else "${ms / 1000.0} sec"
    }
}

/** Box-office verdict for a finished round. */
enum class Verdict {
    BLOCKBUSTER, SUPERHIT, HIT, AVERAGE, FLOP, DISASTER;

    companion object {
        fun forSolvedStep(step: Int): Verdict {
            require(step in 0..ClipLadder.LAST_STEP) { "step out of range: $step" }
            return entries[step]
        }

        /** Null while the round is open or unfinished: those have no verdict. */
        fun forRound(status: RoundStatus, solvedAtStep: Int?): Verdict? = when (status) {
            RoundStatus.WON -> forSolvedStep(requireNotNull(solvedAtStep) { "WON round without solvedAtStep" })
            RoundStatus.LOST -> DISASTER
            RoundStatus.OPEN, RoundStatus.UNFINISHED -> null
        }
    }
}
