package com.mukhda.app.ui.game

import androidx.compose.runtime.Stable
import com.mukhda.app.ui.common.UiText
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.Verdict
import com.mukhda.domain.telemetry.AppGate
import java.time.LocalDate

@Stable
data class GameState(
    val isLoading: Boolean = true,
    /** The phone can't reach the internet: shown as a strip under the header. */
    val isOffline: Boolean = false,
    val loadError: UiText? = null,
    val showNo: Int = 0,
    /** "MON 13 OCT", from the server's show date. */
    val dateLabel: String = "",
    val streak: Int = 0,
    /** The longest housefull run so far. */
    val bestStreak: Int = 0,
    /** Show days with at least one try, from the rounds kept on the phone, for the calendar. */
    val playedDays: Set<LocalDate> = emptySet(),
    /** Today's show date, from the server. */
    val showDate: LocalDate? = null,
    /** The housefull calendar is open over the screen. */
    val isHousefullOpen: Boolean = false,
    /** "Top X% today" on the result and the ticket: switched on from the server (Remote Config). */
    val showTopPercent: Boolean = false,
    /** Whether the server lets this version play; anything but open covers the whole screen. */
    val gate: AppGate = AppGate.OPEN,
    /** The show's last five minutes: the marquee counts down to its end. */
    val isLastCall: Boolean = false,
    /** The server's clock minus the phone's, for countdowns. */
    val clockOffsetMs: Long = 0,
    val selectedTier: Tier = Tier.EASY,
    val rounds: Map<Tier, RoundUi> = emptyMap(),
    val guessText: String = "",
    /** The song picked from the dropdown. Guess only works with one, so free text is never sent. */
    val selectedSong: SongUi? = null,
    val suggestions: List<SuggestionUi> = emptyList(),
    /** All matches for the query, for "5 of N shown". */
    val suggestionsTotal: Int = 0,
    /** The player closed the list (tap outside, Back) without clearing what they typed. */
    val suggestionsDismissed: Boolean = false,
    val isPlaying: Boolean = false,
    val isSubmitting: Boolean = false,
    /** When the show closes (midnight IST, from the server), for the "Next show in" countdown. */
    val endsAtEpochMs: Long = 0,
    /** The shareable ticket is open over the screen. */
    val isTicketOpen: Boolean = false,
) {
    val round: RoundUi? get() = rounds[selectedTier]
    val isDropdownOpen: Boolean get() = suggestions.isNotEmpty() && selectedSong == null && !suggestionsDismissed
    // Not tied to isSubmitting: a skip shows at once, so the buttons don't flicker off while the
    // server confirms it (a tap in that moment is ignored).
    val canGuess: Boolean get() = round?.isOpen == true && round?.isSending != true && selectedSong != null
    val canSkip: Boolean get() = round?.isOpen == true && round?.isSending != true

    /** The tier to suggest after this one: the next not yet started, else the next still open. */
    val nextTier: Tier?
        get() {
            val order = Tier.entries.let { all -> all.drop(selectedTier.ordinal + 1) + all.take(selectedTier.ordinal) }
            return order.firstOrNull { rounds[it]?.let { r -> r.isOpen && !r.isStarted } == true }
                ?: order.firstOrNull { rounds[it]?.isOpen == true }
        }
}

data class RoundUi(
    /** 0..4, the clip ladder step the player is on. */
    val currentStep: Int,
    val slots: List<TrySlotUi>,
    val isOpen: Boolean,
    val isWon: Boolean,
    /** Set once the round is won or lost. */
    val verdict: Verdict? = null,
    /** The clip step it was solved on, for "Got it on the 1-second clip". */
    val solvedAtStep: Int? = null,
    /** The song, once the server reveals it. */
    val reveal: RevealUi? = null,
    /** A try is waiting for the connection; no other try until it's through. */
    val isSending: Boolean = false,
) {
    val isStarted: Boolean get() = slots.any { it is TrySlotUi.Used }
}


data class RevealUi(
    val title: String,
    /** "Film · Year". */
    val detail: String,
    val artworkUrl: String?,
    val appleMusicUrl: String?,
    /** "Top X% today", for a win; null for a loss or before anyone else has finished. */
    val topPercent: Int? = null,
)

sealed interface TrySlotUi {
    val number: Int

    data class Used(
        override val number: Int,
        /** Null for a skip, or a guess whose song isn't in the index. */
        val title: String?,
        /** "Film · Year". */
        val detail: String?,
        val stamp: Stamp,
    ) : TrySlotUi

    data class Empty(override val number: Int) : TrySlotUi

    /** A try made but not yet through to the server: a skip ([title] null) or a guess. */
    data class Sending(override val number: Int, val title: String?, val detail: String?) : TrySlotUi
}

enum class Stamp { WRONG, RIGHT_FILM, SKIPPED, CORRECT }

data class SongUi(val songId: String, val title: String, val detail: String)

data class SuggestionUi(val song: SongUi, val alreadyGuessed: Boolean)

sealed interface GameAction {
    data class OnTierClick(val tier: Tier) : GameAction
    data class OnGuessTextChange(val text: String) : GameAction
    data class OnSuggestionClick(val songId: String) : GameAction
    data object OnGuessClick : GameAction
    data object OnSkipClick : GameAction
    data object OnPlayClick : GameAction
    data object OnRetryClick : GameAction
    /** "Try again" while the game is paused: asks the server whether it's back. */
    data object OnGateTryAgainClick : GameAction
    /** ✕ in the guess box. */
    data object OnClearGuessClick : GameAction
    /** The guess box got focus: bring back the suggestions for what's typed. */
    data object OnGuessFieldFocus : GameAction
    /** Tap outside the list, or Back while it's open. */
    data object OnDismissSuggestions : GameAction
    /** "Next: Medium" on a finished round. */
    data object OnNextTierClick : GameAction
    data object OnShareTicketClick : GameAction
    /** Back, or the close button, on the ticket. */
    data object OnTicketClose : GameAction
    /** The ticket went to the share sheet. */
    data object OnTicketShared : GameAction
    /** "Listen on Apple Music". */
    data object OnAppleMusicClick : GameAction
    /** The game came on screen, or went to the background: waiting tries only retry while shown. */
    data object OnScreenShown : GameAction
    data object OnScreenHidden : GameAction
    /** Closes the show-over popup. */
    /** The "N days housefull" badge. */
    data object OnStreakClick : GameAction
    /** Back, the close button, or a tap outside the housefull calendar. */
    data object OnHousefullClose : GameAction
}

sealed interface GameEvent {
    data class ShowMessage(val message: UiText) : GameEvent

    /** The player just guessed the song: once, at the moment of the win, never on a reload. */
    data class Celebrate(val verdict: Verdict) : GameEvent
}
