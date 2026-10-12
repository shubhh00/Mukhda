package com.mukhda.app.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GameState(guessText = savedStateHandle[KEY_GUESS_TEXT] ?: "")
    )
    val state = _state.asStateFlow()

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.OnTierClick -> _state.update { it.copy(selectedTier = action.tier) }
            is GameAction.OnGuessTextChange -> {
                savedStateHandle[KEY_GUESS_TEXT] = action.text
                _state.update { it.copy(guessText = action.text) }
            }
        }
    }

    private companion object {
        const val KEY_GUESS_TEXT = "guessText"
    }
}
