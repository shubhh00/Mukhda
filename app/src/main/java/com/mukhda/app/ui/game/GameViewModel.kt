package com.mukhda.app.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mukhda.app.R
import com.mukhda.app.ui.common.UiText
import com.mukhda.app.ui.common.toUiText
import com.mukhda.domain.GameError
import com.mukhda.domain.Outcome
import com.mukhda.domain.audio.ClipPlayer
import com.mukhda.domain.model.ClipLadder
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.onFailure
import com.mukhda.domain.onSuccess
import com.mukhda.domain.round.RoundHistory
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.Streak
import com.mukhda.domain.round.TryRecord
import com.mukhda.domain.search.SearchSong
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.search.SongSearcher
import com.mukhda.domain.show.Show
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.show.TryError
import com.mukhda.domain.show.isTransient
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class GameViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val showRepository: ShowRepository,
    private val songIndex: SongIndexDataSource,
    private val clipPlayer: ClipPlayer,
    private val roundHistory: RoundHistory,
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

    /** Days the player made a try on, from the rounds kept on the phone, for the streak. */
    private var playedDays: Set<LocalDate> = emptySet()

    /** A try per tier that hasn't reached the server yet; the repository keeps it and resends it. */
    private val sending = mutableMapOf<Tier, String?>()
    private var resendJob: Job? = null

    init {
        // Load the search index now so the first keystroke in the guess box is instant.
        // A failure here is retried by the first search, so it's ignored.
        viewModelScope.launch { songIndex.prepare() }
        // The streak follows the saved rounds, so a confirmed try today extends it at once.
        viewModelScope.launch {
            roundHistory.dayRounds().collect { rounds ->
                playedDays = Streak.playedDates(rounds)
                publishStreak()
            }
        }
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
            GameAction.OnClearGuessClick -> {
                searchJob?.cancel()
                clearGuess()
            }
            GameAction.OnGuessFieldFocus -> _state.update { it.copy(suggestionsDismissed = false) }
            GameAction.OnDismissSuggestions -> _state.update { it.copy(suggestionsDismissed = true) }
            GameAction.OnNextTierClick -> _state.value.nextTier?.let { selectTier(GameAction.OnTierClick(it)) }
            GameAction.OnShareTicketClick -> _state.update { it.copy(isTicketOpen = true) }
            GameAction.OnTicketClose -> _state.update { it.copy(isTicketOpen = false) }
        }
    }

    private fun loadShow() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            showRepository.todaysShow()
                .onSuccess { loaded ->
                    showLoaded(loaded)
                    // Get the selected tier's clip ready first, so an early tap plays at once, then
                    // download the rest. A failure here is retried, and reported, by the next play.
                    launch {
                        loaded.previews[_state.value.selectedTier]?.let { clipPlayer.cache(listOf(it)) }
                        preloadCurrentClip()
                        clipPlayer.cache(loaded.previews.values)
                    }
                }
                .onFailure { error -> _state.update { it.copy(isLoading = false, loadError = error.toUiText()) } }
        }
    }

    /**
     * A fresh show from the server. Loading it resends any try still waiting, so by now those are
     * on the server: confirmed (and in the show) or refused. A round that a waiting guess just
     * won gets its celebration now.
     */
    private suspend fun showLoaded(loaded: Show) {
        val before = show?.rounds
        show = loaded
        val wasSending = sending.keys.toSet()
        sending.clear()
        rememberGuessedSongs(loaded)
        publishShow()
        for (tier in wasSending) {
            val round = loaded.rounds[tier] ?: continue
            if (round.status == RoundStatus.WON && before?.get(tier)?.status != RoundStatus.WON) {
                round.verdict?.let { _events.send(GameEvent.Celebrate(it)) }
            }
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
                endsAtEpochMs = show.endsAt.toEpochMilli(),
                streak = Streak.current(playedDays, show.date),
                rounds = show.rounds.mapValues { (tier, round) ->
                    val ui = round.toRoundUi(songs)
                    if (tier in sending) ui.withSending(sending[tier]?.let(songs::get)) else ui
                },
            )
        }
        refreshSuggestions()
    }

    /** Counted up to the server's show date, never the phone's: today without a try doesn't break it. */
    private fun publishStreak() {
        val today = show?.date ?: return
        _state.update { it.copy(streak = Streak.current(playedDays, today)) }
    }

    private fun selectTier(action: GameAction.OnTierClick) {
        stopPlaying()
        // Tapping a tab is a tap outside the list, so it closes it.
        _state.update { it.copy(selectedTier = action.tier, suggestionsDismissed = true) }
        // "Already guessed" depends on the tier's round.
        refreshSuggestions()
        preloadCurrentClip()
    }

    private fun onGuessTextChange(text: String) {
        savedStateHandle[KEY_GUESS_TEXT] = text
        _state.update { state ->
            // Editing the text after picking a song un-picks it.
            val selected = state.selectedSong?.takeIf { it.title == text }
            state.copy(guessText = text, selectedSong = selected, suggestionsDismissed = false)
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
        val before = show.rounds[tier] ?: return
        if (state.isSubmitting || state.round?.isOpen != true) return

        stopPlaying()
        // One requestId per try; the server ignores repeats of the same id.
        val requestId = UUID.randomUUID().toString()
        // Shown at once, before the server answers. A skip is always a skip, so it lands as one
        // and the ladder moves on; a guess shows as sending until its verdict comes back.
        if (type == TryType.SKIP) {
            (before.apply(TryRecord(requestId, TryType.SKIP, null, TryResult.SKIPPED)) as? Outcome.Success)?.data?.let { skipped ->
                this.show = show.copy(rounds = show.rounds + (tier to skipped))
            }
        } else {
            sending[tier] = songId
        }
        clearGuess()
        _state.update { it.copy(isSubmitting = true) }
        publishShow()
        if (type == TryType.SKIP) preloadCurrentClip()

        viewModelScope.launch {
            val result = showRepository.submitTry(show.date, tier, requestId, type, songId)
            _state.update { it.copy(isSubmitting = false) }
            when (result) {
                is Outcome.Success -> {
                    sending.remove(tier)
                    showRound(tier, result.data)
                    if (result.data.status == RoundStatus.WON) result.data.verdict?.let { _events.send(GameEvent.Celebrate(it)) }
                    // A missed guess moves the ladder up a step: the next clip is longer. A skip
                    // already moved it, and got that clip ready, when it was shown.
                    if (type == TryType.GUESS) preloadCurrentClip()
                }
                is Outcome.Error -> if (result.error.isTransient) {
                    // It never reached the server: the repository keeps it and sends it again,
                    // with the same requestId, so it shows as sending and is retried until through.
                    sending[tier] = songId
                    showRound(tier, before)
                    resendWhenBack()
                    _events.send(GameEvent.ShowMessage(UiText.StringResource(R.string.try_queued)))
                } else {
                    // Refused: back to the round as it was, and the server has the last word.
                    sending.remove(tier)
                    showRound(tier, before)
                    handleTryError(result.error)
                }
            }
        }
    }

    /** Puts [round] in the show for [tier] and redraws. */
    private fun showRound(tier: Tier, round: RoundState) {
        show = show?.let { it.copy(rounds = it.rounds + (tier to round)) }
        publishShow()
    }

    /** Retries the waiting tries, waiting longer each time, until the show loads again. */
    private fun resendWhenBack() {
        if (resendJob?.isActive == true) return
        resendJob = viewModelScope.launch {
            var wait = RESEND_FIRST_MS
            while (sending.isNotEmpty()) {
                delay(wait)
                showRepository.todaysShow().onSuccess { showLoaded(it) }
                wait = (wait * 2).coerceAtMost(RESEND_MAX_MS)
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

    /** Plays the current step's clip of the selected tier; tapping again stops it. */
    private fun togglePlay() {
        if (_state.value.isPlaying) {
            stopPlaying()
            return
        }
        val tier = _state.value.selectedTier
        val round = _state.value.round ?: return
        val preview = show?.previews?.get(tier) ?: return
        if (clipPlayer.isVolumeLow()) {
            viewModelScope.launch { _events.send(GameEvent.ShowMessage(UiText.StringResource(R.string.volume_low))) }
        }
        _state.update { it.copy(isPlaying = true) }
        playJob = viewModelScope.launch {
            // Once the round is over, the player knows the song: play all of it.
            clipPlayer.play(preview, if (round.isOpen) ClipLadder.clipLengthMs(round.currentStep) else null)
                .onFailure { _events.send(GameEvent.ShowMessage(it.toUiText())) }
            _state.update { it.copy(isPlaying = false) }
        }
    }

    /**
     * Gets the clip the player is likely to ask for next ready, and wakes the speaker, so a tap in
     * the next couple of seconds plays at once.
     */
    private fun preloadCurrentClip() {
        val preview = show?.previews?.get(_state.value.selectedTier) ?: return
        val step = _state.value.round?.currentStep ?: return
        viewModelScope.launch { clipPlayer.preload(preview, ClipLadder.clipLengthMs(step)) }
        if (_state.value.round?.isOpen == true) viewModelScope.launch { clipPlayer.wakeSpeaker() }
    }

    private fun stopPlaying() {
        playJob?.cancel()
        _state.update { it.copy(isPlaying = false) }
    }

    override fun onCleared() {
        clipPlayer.release()
    }

    private companion object {
        const val KEY_GUESS_TEXT = "guessText"
        const val SEARCH_DEBOUNCE_MS = 150L
        const val RESEND_FIRST_MS = 3_000L
        const val RESEND_MAX_MS = 30_000L
    }
}
