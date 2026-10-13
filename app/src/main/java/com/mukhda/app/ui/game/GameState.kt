package com.mukhda.app.ui.game

import androidx.compose.runtime.Stable
import com.mukhda.app.ui.common.UiText
import com.mukhda.domain.model.Tier

@Stable
data class GameState(
    val isLoading: Boolean = true,
    val loadError: UiText? = null,
    val showNo: Int = 0,
    /** "MON 13 OCT", from the server's show date. */
    val dateLabel: String = "",
    val streak: Int = 0,
    val selectedTier: Tier = Tier.EASY,
    val rounds: Map<Tier, RoundUi> = emptyMap(),
    val guessText: String = "",
    /** The song picked from the dropdown. Guess only works with one, so free text is never sent. */
    val selectedSong: SongUi? = null,
    val suggestions: List<SuggestionUi> = emptyList(),
    /** All matches for the query, for "5 of N shown". */
    val suggestionsTotal: Int = 0,
    val isPlaying: Boolean = false,
    val isSubmitting: Boolean = false,
) {
    val round: RoundUi? get() = rounds[selectedTier]
    val isDropdownOpen: Boolean get() = suggestions.isNotEmpty() && selectedSong == null
    val canGuess: Boolean get() = round?.isOpen == true && selectedSong != null && !isSubmitting
    val canSkip: Boolean get() = round?.isOpen == true && !isSubmitting
}

data class RoundUi(
    /** 0..4, the clip ladder step the player is on. */
    val currentStep: Int,
    val slots: List<TrySlotUi>,
    val isOpen: Boolean,
    val isWon: Boolean,
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
}

sealed interface GameEvent {
    data class ShowMessage(val message: UiText) : GameEvent
}
