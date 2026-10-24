package com.mukhda.app.ui.game

import com.mukhda.domain.model.ClipLadder
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.SongReveal
import com.mukhda.domain.search.SearchSong
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_LABEL = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** "MON 13 OCT". */
fun LocalDate.toDateLabel(): String = format(DATE_LABEL).uppercase(Locale.ENGLISH)

fun SearchSong.toSongUi(): SongUi = SongUi(songId = songId, title = title, detail = "$filmTitle · $year")

fun TryResult.toStamp(): Stamp = when (this) {
    TryResult.CORRECT -> Stamp.CORRECT
    TryResult.RIGHT_FILM -> Stamp.RIGHT_FILM
    TryResult.WRONG -> Stamp.WRONG
    TryResult.SKIPPED -> Stamp.SKIPPED
}

/** The round with a try still waiting for the connection, in the first free slot. */
fun RoundUi.withSending(song: SearchSong?): RoundUi {
    val at = slots.indexOfFirst { it is TrySlotUi.Empty }
    if (at == -1) return this
    val sending = TrySlotUi.Sending(slots[at].number, song?.title, song?.let { "${it.filmTitle} · ${it.year}" })
    return copy(slots = slots.toMutableList().also { it[at] = sending }, isSending = true)
}

/** Five slots: one per try made, then numbered empty ones. [songs] fills in what was guessed. */
fun RoundState.toRoundUi(songs: Map<String, SearchSong>): RoundUi {
    val used = tries.mapIndexed { i, attempt ->
        val song = attempt.guessedSongId?.let(songs::get)
        TrySlotUi.Used(
            number = i + 1,
            title = song?.title,
            detail = song?.let { "${it.filmTitle} · ${it.year}" },
            stamp = attempt.result.toStamp(),
        )
    }
    val empty = (used.size + 1..ClipLadder.MAX_TRIES).map { TrySlotUi.Empty(it) }
    return RoundUi(
        currentStep = currentStep,
        slots = used + empty,
        isOpen = status == RoundStatus.OPEN,
        isWon = status == RoundStatus.WON,
        verdict = verdict,
        solvedAtStep = solvedAtStep,
        // Only a finished round can carry one; the check keeps an open round from ever showing it.
        reveal = reveal?.takeIf { status.isFinished }?.toRevealUi(won = status == RoundStatus.WON),
    )
}

fun SongReveal.toRevealUi(won: Boolean = true): RevealUi = RevealUi(
    title = title,
    detail = "$filmTitle · $year",
    artworkUrl = artworkUrl,
    appleMusicUrl = appleMusicUrl,
    // A loss is "Top 100%" by the server's count: not worth showing.
    topPercent = topPercent?.takeIf { won },
)
