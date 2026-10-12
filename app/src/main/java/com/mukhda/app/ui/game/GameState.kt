package com.mukhda.app.ui.game

import com.mukhda.domain.model.Tier

data class GameState(
    val selectedTier: Tier = Tier.EASY,
    val guessText: String = "",
)

sealed interface GameAction {
    data class OnTierClick(val tier: Tier) : GameAction
    data class OnGuessTextChange(val text: String) : GameAction
}
