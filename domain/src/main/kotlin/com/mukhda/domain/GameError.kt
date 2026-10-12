package com.mukhda.domain

/** Game rule rejections returned by `submitTry`. */
enum class GameError : AppError {
    ROUND_CLOSED,
    TRIES_EXHAUSTED,
    ALREADY_FINISHED,
    INVALID_SONG,
}

/** Local reasons a judged try can't be applied to a [com.mukhda.domain.round.RoundState]. */
enum class RoundError : AppError {
    ALREADY_FINISHED,
    TRIES_EXHAUSTED,
    /** Type and result disagree, e.g. a SKIP judged WRONG. Means bad data, not a player mistake. */
    INCONSISTENT_TRY,
}
