package com.mukhda.app.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mukhda.domain.search.SongIndexDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val songIndex: SongIndexDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(
        GameState(guessText = savedStateHandle[KEY_GUESS_TEXT] ?: "")
    )
    val state = _state.asStateFlow()

    init {
        // Load the search index now so the first keystroke in the guess box is instant.
        // A failure here is retried by the first search, so it's ignored.
        viewModelScope.launch { songIndex.prepare() }
    }

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
