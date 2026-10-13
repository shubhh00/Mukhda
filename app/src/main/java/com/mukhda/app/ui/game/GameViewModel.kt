package com.mukhda.app.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mukhda.app.R
import com.mukhda.app.ui.common.UiText
import com.mukhda.app.ui.common.toUiText
import com.mukhda.domain.GameError
import com.mukhda.domain.Outcome
import com.mukhda.domain.model.ClipLadder
import com.mukhda.domain.model.TryType
import com.mukhda.domain.onFailure
import com.mukhda.domain.onSuccess
import com.mukhda.domain.search.SearchSong
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.search.SongSearcher
import com.mukhda.domain.show.Show
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.show.TryError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val showRepository: ShowRepository,
    private val songIndex: SongIndexDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow(GameState(guessText = savedStateHandle[KEY_GUESS_TEXT] ?: ""))
    val state = _state.asStateFlow()

    private val _events = Channel<GameEvent>()
    val events = _events.receiveAsFlow()

    private var show: Show? = null
    /** Every song seen in search results or tries, so slots can show what was guessed. */
    private val songs = mutableMapOf<String, SearchSong>()
    private var searchJob: Job? = null
    private var playJob: Job? = null

    init {
        // Load the search index now so the first keystroke in the guess box is instant.
        // A failure here is retried by the first search, so it's ignored.
        viewModelScope.launch { songIndex.prepare() }
        loadShow()
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.OnTierClick -> selectTier(action)
            is GameAction.OnGuessTextChange -> onGuessTextChange(action.text)
            is GameAction.OnSuggestionClick -> pickSuggestion(action.songId)
            GameAction.OnGuessClick -> _state.value.selectedSong?.let { submit(TryType.GUESS, it.songId) }
            GameAction.OnSkipClick -> submit(TryType.SKIP, songId = null)
            GameAction.OnPlayClick -> togglePlay()
            GameAction.OnRetryClick -> loadShow()
        }
    }

    private fun loadShow() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            showRepository.todaysShow()
                .onSuccess { loaded ->
                    show = loaded
                    rememberGuessedSongs(loaded)
                    publishShow()
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, loadError = error.toUiText()) } }
        }
    }

    private suspend fun rememberGuessedSongs(show: Show) {
        val ids = show.rounds.values.flatMap { round -> round.tries.mapNotNull { it.guessedSongId } }
        songIndex.songs(ids.filterNot(songs::containsKey)).onSuccess(songs::putAll)
    }

    private fun publishShow() {
        val show = show ?: return
        _state.update { state ->
            state.copy(
                isLoading = false,
                loadError = null,
                showNo = show.showNo,
                dateLabel = show.date.toDateLabel(),
                rounds = show.rounds.mapValues { (_, round) -> round.toRoundUi(songs) },
            )
        }
        refreshSuggestions()
    }

    private fun selectTier(action: GameAction.OnTierClick) {
        stopPlaying()
        _state.update { it.copy(selectedTier = action.tier) }
        // "Already guessed" depends on the tier's round.
        refreshSuggestions()
    }

    private fun onGuessTextChange(text: String) {
        savedStateHandle[KEY_GUESS_TEXT] = text
        _state.update { state ->
            // Editing the text after picking a song un-picks it.
            val selected = state.selectedSong?.takeIf { it.title == text }
            state.copy(guessText = text, selectedSong = selected)
        }
        searchJob?.cancel()
        if (text.trim().length < SongSearcher.MIN_QUERY_LENGTH || _state.value.selectedSong != null) {
            _state.update { it.copy(suggestions = emptyList(), suggestionsTotal = 0) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            songIndex.search(text).onSuccess { results ->
                results.items.forEach { songs[it.songId] = it }
                _state.update { it.copy(suggestionsTotal = results.totalMatches) }
                showSuggestions(results.items)
            }
        }
    }

    private fun showSuggestions(results: List<SearchSong>) {
        val guessed = guessedInCurrentRound()
        _state.update { state ->
            state.copy(suggestions = results.map { SuggestionUi(it.toSongUi(), alreadyGuessed = it.songId in guessed) })
        }
    }

    /** Re-marks "Already guessed" after a try or a tier change, without searching again. */
    private fun refreshSuggestions() {
        val current = _state.value.suggestions.mapNotNull { songs[it.song.songId] }
        if (current.isNotEmpty()) showSuggestions(current)
    }

    private fun guessedInCurrentRound(): Set<String> =
        show?.rounds?.get(_state.value.selectedTier)?.tries?.mapNotNullTo(mutableSetOf()) { it.guessedSongId }.orEmpty()

    private fun pickSuggestion(songId: String) {
        val song = songs[songId] ?: return
        if (songId in guessedInCurrentRound()) return
        searchJob?.cancel()
        savedStateHandle[KEY_GUESS_TEXT] = song.title
        _state.update {
            it.copy(guessText = song.title, selectedSong = song.toSongUi(), suggestions = emptyList(), suggestionsTotal = 0)
        }
    }

    private fun submit(type: TryType, songId: String?) {
        val show = show ?: return
        val state = _state.value
        val tier = state.selectedTier
        if (state.isSubmitting || state.round?.isOpen != true) return

        stopPlaying()
        _state.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            // One requestId per try; the server ignores repeats of the same id.
            val result = showRepository.submitTry(show.date, tier, UUID.randomUUID().toString(), type, songId)
            _state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Outcome.Success -> {
                    this@GameViewModel.show = show.copy(rounds = show.rounds + (tier to result.data))
                    clearGuess()
                    publishShow()
                }
                is Outcome.Error -> handleTryError(result.error)
            }
        }
    }

    private suspend fun handleTryError(error: TryError) {
        when (error) {
            is TryError.Data -> _events.send(GameEvent.ShowMessage(error.error.toUiText()))
            is TryError.Rule -> when (error.error) {
                GameError.ROUND_CLOSED -> {
                    _events.send(GameEvent.ShowMessage(UiText.StringResource(R.string.error_round_closed)))
                    loadShow()
                }
                // The server knows better than this screen: reload and trust it.
                GameError.TRIES_EXHAUSTED, GameError.ALREADY_FINISHED -> loadShow()
                GameError.INVALID_SONG ->
                    _events.send(GameEvent.ShowMessage(UiText.StringResource(R.string.error_invalid_song)))
            }
        }
    }

    private fun clearGuess() {
        savedStateHandle[KEY_GUESS_TEXT] = ""
        _state.update { it.copy(guessText = "", selectedSong = null, suggestions = emptyList(), suggestionsTotal = 0) }
    }

    /** Stands in for clip playback until the audio player lands: "plays" for the clip's length. */
    private fun togglePlay() {
        if (_state.value.isPlaying) {
            stopPlaying()
            return
        }
        val step = _state.value.round?.currentStep ?: return
        _state.update { it.copy(isPlaying = true) }
        playJob = viewModelScope.launch {
            delay(ClipLadder.clipLengthMs(step) ?: FULL_PREVIEW_MS)
            _state.update { it.copy(isPlaying = false) }
        }
    }

    private fun stopPlaying() {
        playJob?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    private companion object {
        const val KEY_GUESS_TEXT = "guessText"
        const val SEARCH_DEBOUNCE_MS = 150L
        const val FULL_PREVIEW_MS = 30_000L
    }
}
