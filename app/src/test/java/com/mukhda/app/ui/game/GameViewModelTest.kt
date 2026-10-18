package com.mukhda.app.ui.game

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.mukhda.domain.DataError
import com.mukhda.domain.GameError
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.Verdict
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.search.SearchSong
import com.mukhda.domain.show.TryError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val songs = listOf(
        SearchSong("s7", "Kal Ho Naa Ho", "f_khnh", "Kal Ho Naa Ho", 2003),
        SearchSong("s8", "Kuch To Hua Hai", "f_khnh", "Kal Ho Naa Ho", 2003),
        SearchSong("s10", "Tum Hi Ho", "f_a2", "Aashiqui 2", 2013),
    )
    private val show = FakeShowRepository()
    private val songIndex = FakeSongIndexDataSource(songs)
    private val player = FakeClipPlayer()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = GameViewModel(handle, show, songIndex, player)

    private fun TestScope.type(vm: GameViewModel, text: String) {
        vm.onAction(GameAction.OnGuessTextChange(text))
        advanceUntilIdle()
    }

    @Test
    fun `loads the show with five empty slots per tier`() = runTest(dispatcher) {
        val vm = viewModel()
        val state = vm.state.value

        assertThat(state.isLoading).isFalse()
        assertThat(state.showNo).isEqualTo(12)
        assertThat(state.dateLabel).isEqualTo("TUE 13 OCT")
        assertThat(state.rounds.keys.toList()).containsExactly(*Tier.entries.toTypedArray())
        assertThat(state.round!!.slots.map { it::class }).containsExactly(*Array(5) { TrySlotUi.Empty::class })
        assertThat(songIndex.prepareCalls).isEqualTo(1)
    }

    @Test
    fun `a failed load shows an error and retry reloads`() = runTest(dispatcher) {
        show.loadError = DataError.Network.NO_INTERNET
        val vm = viewModel()
        assertThat(vm.state.value.loadError).isNotNull()

        show.loadError = null
        vm.onAction(GameAction.OnRetryClick)
        assertThat(vm.state.value.loadError).isNull()
        assertThat(vm.state.value.round).isNotNull()
    }

    @Test
    fun `fewer than two characters shows no suggestions`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "k")
        assertThat(vm.state.value.suggestions).isEmpty()
        assertThat(vm.state.value.isDropdownOpen).isFalse()
    }

    @Test
    fun `typing shows ranked suggestions with film and year`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "kal ho")

        val suggestions = vm.state.value.suggestions
        assertThat(suggestions.map { it.song.title }).containsExactly("Kal Ho Naa Ho", "Kuch To Hua Hai")
        assertThat(suggestions.first().song.detail).isEqualTo("Kal Ho Naa Ho · 2003")
        assertThat(vm.state.value.isDropdownOpen).isTrue()
    }

    @Test
    fun `search waits for typing to pause`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnGuessTextChange("tum hi"))
        advanceTimeBy(100)
        assertThat(vm.state.value.suggestions).isEmpty()
        advanceUntilIdle()
        assertThat(vm.state.value.suggestions).hasSize(1)
    }

    @Test
    fun `guess needs a song picked from the list`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "tum hi ho")
        assertThat(vm.state.value.canGuess).isFalse()

        vm.onAction(GameAction.OnGuessClick)
        assertThat(show.requestIds).isEmpty()
    }

    @Test
    fun `picking then guessing fills a slot and moves up the clip ladder`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "tum hi")
        vm.onAction(GameAction.OnSuggestionClick("s10"))
        assertThat(vm.state.value.guessText).isEqualTo("Tum Hi Ho")
        assertThat(vm.state.value.isDropdownOpen).isFalse()
        assertThat(vm.state.value.canGuess).isTrue()

        vm.onAction(GameAction.OnGuessClick)

        val state = vm.state.value
        val slot = state.round!!.slots.first() as TrySlotUi.Used
        assertThat(slot.title).isEqualTo("Tum Hi Ho")
        assertThat(slot.detail).isEqualTo("Aashiqui 2 · 2013")
        assertThat(slot.stamp).isEqualTo(Stamp.WRONG)
        assertThat(state.round!!.currentStep).isEqualTo(1)
        assertThat(state.guessText).isEqualTo("")
        assertThat(state.selectedSong).isNull()
    }

    @Test
    fun `right film is stamped amber`() = runTest(dispatcher) {
        show.judge = { TryResult.RIGHT_FILM }
        val vm = viewModel()
        type(vm, "kuch to")
        vm.onAction(GameAction.OnSuggestionClick("s8"))
        vm.onAction(GameAction.OnGuessClick)

        assertThat((vm.state.value.round!!.slots.first() as TrySlotUi.Used).stamp).isEqualTo(Stamp.RIGHT_FILM)
    }

    @Test
    fun `editing the text after picking un-picks the song`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "tum hi")
        vm.onAction(GameAction.OnSuggestionClick("s10"))
        type(vm, "Tum Hi H")

        assertThat(vm.state.value.selectedSong).isNull()
        assertThat(vm.state.value.canGuess).isFalse()
    }

    @Test
    fun `a song already guessed this round is disabled and can't be picked`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "tum hi")
        vm.onAction(GameAction.OnSuggestionClick("s10"))
        vm.onAction(GameAction.OnGuessClick)

        type(vm, "tum hi")
        assertThat(vm.state.value.suggestions.single().alreadyGuessed).isTrue()
        vm.onAction(GameAction.OnSuggestionClick("s10"))
        assertThat(vm.state.value.selectedSong).isNull()
    }

    @Test
    fun `already guessed only applies to the tier it was guessed in`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "tum hi")
        vm.onAction(GameAction.OnSuggestionClick("s10"))
        vm.onAction(GameAction.OnGuessClick)

        type(vm, "tum hi")
        vm.onAction(GameAction.OnTierClick(Tier.LEGEND))
        assertThat(vm.state.value.suggestions.single().alreadyGuessed).isFalse()
    }

    @Test
    fun `skip fills a skipped slot`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnSkipClick)

        val slot = vm.state.value.round!!.slots.first() as TrySlotUi.Used
        assertThat(slot.stamp).isEqualTo(Stamp.SKIPPED)
        assertThat(slot.title).isNull()
    }

    @Test
    fun `five misses close the round`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(5) { vm.onAction(GameAction.OnSkipClick) }

        val round = vm.state.value.round!!
        assertThat(round.isOpen).isFalse()
        assertThat(vm.state.value.canSkip).isFalse()
        vm.onAction(GameAction.OnSkipClick)
        assertThat(show.requestIds).hasSize(5)
    }

    @Test
    fun `each try gets its own request id`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnSkipClick)
        vm.onAction(GameAction.OnSkipClick)
        assertThat(show.requestIds.toSet()).hasSize(2)
    }

    @Test
    fun `tiers keep separate rounds`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnSkipClick)
        vm.onAction(GameAction.OnTierClick(Tier.EXPERT))

        assertThat(vm.state.value.round!!.currentStep).isEqualTo(0)
        vm.onAction(GameAction.OnTierClick(Tier.EASY))
        assertThat(vm.state.value.round!!.currentStep).isEqualTo(1)
    }

    @Test
    fun `server saying tries are used up reloads the show`() = runTest(dispatcher) {
        val vm = viewModel()
        show.nextTryError = TryError.Rule(GameError.TRIES_EXHAUSTED)
        vm.onAction(GameAction.OnSkipClick)
        assertThat(show.loads).isEqualTo(2)
    }

    @Test
    fun `a network failure on a try shows a message`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.events.test {
            show.nextTryError = TryError.Data(DataError.Network.NO_INTERNET)
            vm.onAction(GameAction.OnSkipClick)
            assertThat(awaitItem()).isInstanceOf<GameEvent.ShowMessage>()
        }
        assertThat(vm.state.value.isSubmitting).isFalse()
    }

    @Test
    fun `play lasts as long as the current clip`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnPlayClick)
        assertThat(vm.state.value.isPlaying).isTrue()
        advanceTimeBy(150)
        assertThat(vm.state.value.isPlaying).isFalse()
    }

    @Test
    fun `loading the show caches every preview`() = runTest(dispatcher) {
        viewModel()
        assertThat(player.cached.toSet()).isEqualTo(show.previews.values.toSet())
    }

    @Test
    fun `play uses the selected tier's preview and the current step's length`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnTierClick(Tier.HARD))
        vm.onAction(GameAction.OnSkipClick)
        vm.onAction(GameAction.OnPlayClick)
        assertThat(player.played.single()).isEqualTo(show.previews.getValue(Tier.HARD) to 500L)
    }

    @Test
    fun `the next clip is preloaded after loading, a try and a tier change`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(player.preloaded.last()).isEqualTo(show.previews.getValue(Tier.EASY) to 100L)
        vm.onAction(GameAction.OnSkipClick)
        assertThat(player.preloaded.last()).isEqualTo(show.previews.getValue(Tier.EASY) to 500L)
        vm.onAction(GameAction.OnTierClick(Tier.LEGEND))
        assertThat(player.preloaded.last()).isEqualTo(show.previews.getValue(Tier.LEGEND) to 100L)
    }

    @Test
    fun `the speaker is woken when a play is likely, but not once the round is over`() = runTest(dispatcher) {
        val vm = viewModel()
        val afterLoad = player.speakerWakes
        assertThat(afterLoad).isEqualTo(1)
        repeat(5) { vm.onAction(GameAction.OnSkipClick) }
        // Four skips each set up the next clip; the fifth closes the round.
        assertThat(player.speakerWakes).isEqualTo(afterLoad + 4)
    }

    @Test
    fun `a lost round reveals the song, its verdict and the next tier`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(5) { vm.onAction(GameAction.OnSkipClick) }
        val round = vm.state.value.round!!
        assertThat(round.isOpen).isFalse()
        assertThat(round.verdict).isEqualTo(Verdict.DISASTER)
        assertThat(round.reveal?.title).isEqualTo("Kal Ho Naa Ho")
        assertThat(round.reveal?.detail).isEqualTo("Kal Ho Naa Ho · 2003")
        assertThat(vm.state.value.nextTier).isEqualTo(Tier.MEDIUM)
    }

    @Test
    fun `an open round never shows a reveal, even if one arrives`() = runTest(dispatcher) {
        show.rounds = show.rounds + (Tier.EASY to RoundState(Tier.EASY, reveal = show.reveal))
        val vm = viewModel()
        assertThat(vm.state.value.round!!.reveal).isNull()
    }

    @Test
    fun `won rounds keep the step they were solved on`() = runTest(dispatcher) {
        show.judge = { TryResult.CORRECT }
        val vm = viewModel()
        vm.onAction(GameAction.OnSkipClick)
        type(vm, "kal ho")
        vm.onAction(GameAction.OnSuggestionClick("s7"))
        vm.onAction(GameAction.OnGuessClick)
        val round = vm.state.value.round!!
        assertThat(round.verdict).isEqualTo(Verdict.SUPERHIT)
        assertThat(round.solvedAtStep).isEqualTo(1)
    }

    @Test
    fun `next tier skips rounds already started, and moves there`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAction(GameAction.OnTierClick(Tier.MEDIUM))
        vm.onAction(GameAction.OnSkipClick)
        vm.onAction(GameAction.OnTierClick(Tier.EASY))
        repeat(5) { vm.onAction(GameAction.OnSkipClick) }
        assertThat(vm.state.value.nextTier).isEqualTo(Tier.HARD)
        vm.onAction(GameAction.OnNextTierClick)
        assertThat(vm.state.value.selectedTier).isEqualTo(Tier.HARD)
    }

    @Test
    fun `once a round is over, play gives the whole song`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(5) { vm.onAction(GameAction.OnSkipClick) }
        vm.onAction(GameAction.OnPlayClick)
        assertThat(player.played.single().second).isNull()
    }

    @Test
    fun `the ticket opens and closes`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(5) { vm.onAction(GameAction.OnSkipClick) }
        vm.onAction(GameAction.OnShareTicketClick)
        assertThat(vm.state.value.isTicketOpen).isTrue()
        vm.onAction(GameAction.OnTicketClose)
        assertThat(vm.state.value.isTicketOpen).isFalse()
    }

    @Test
    fun `the countdown runs to the server's end of show`() = runTest(dispatcher) {
        val vm = viewModel()
        assertThat(vm.state.value.endsAtEpochMs).isEqualTo(java.time.Instant.parse("2026-10-13T18:30:00Z").toEpochMilli())
    }

    @Test
    fun `the last step plays the whole preview`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(4) { vm.onAction(GameAction.OnSkipClick) }
        vm.onAction(GameAction.OnPlayClick)
        assertThat(player.played.single().second).isNull()
    }

    @Test
    fun `tapping play again stops the clip`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(4) { vm.onAction(GameAction.OnSkipClick) }
        vm.onAction(GameAction.OnPlayClick)
        vm.onAction(GameAction.OnPlayClick)
        assertThat(vm.state.value.isPlaying).isFalse()
        assertThat(player.played).hasSize(1)
    }

    @Test
    fun `a clip that fails to play shows a message`() = runTest(dispatcher) {
        val vm = viewModel()
        player.playError = DataError.Network.NO_INTERNET
        vm.events.test {
            vm.onAction(GameAction.OnPlayClick)
            assertThat(awaitItem()).isInstanceOf<GameEvent.ShowMessage>()
        }
        assertThat(vm.state.value.isPlaying).isFalse()
    }

    @Test
    fun `low volume shows a hint and still plays`() = runTest(dispatcher) {
        val vm = viewModel()
        player.volumeLow = true
        vm.events.test {
            vm.onAction(GameAction.OnPlayClick)
            assertThat(awaitItem()).isInstanceOf<GameEvent.ShowMessage>()
        }
        assertThat(player.played).hasSize(1)
    }

    @Test
    fun `closing the list keeps the typed text, and focusing the box brings it back`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "kal ho")
        assertThat(vm.state.value.isDropdownOpen).isTrue()

        vm.onAction(GameAction.OnDismissSuggestions)
        assertThat(vm.state.value.isDropdownOpen).isFalse()
        assertThat(vm.state.value.guessText).isEqualTo("kal ho")

        vm.onAction(GameAction.OnGuessFieldFocus)
        assertThat(vm.state.value.isDropdownOpen).isTrue()
    }

    @Test
    fun `typing again reopens a closed list`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "kal ho")
        vm.onAction(GameAction.OnDismissSuggestions)
        type(vm, "kal ho n")
        assertThat(vm.state.value.isDropdownOpen).isTrue()
    }

    @Test
    fun `tapping a tier closes the list`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "kal ho")
        vm.onAction(GameAction.OnTierClick(Tier.HARD))
        assertThat(vm.state.value.isDropdownOpen).isFalse()
    }

    @Test
    fun `clear empties the box, the pick and the list`() = runTest(dispatcher) {
        val vm = viewModel()
        type(vm, "tum hi")
        vm.onAction(GameAction.OnSuggestionClick("s10"))
        vm.onAction(GameAction.OnClearGuessClick)

        val state = vm.state.value
        assertThat(state.guessText).isEqualTo("")
        assertThat(state.selectedSong).isNull()
        assertThat(state.suggestions).isEmpty()
        assertThat(state.canGuess).isFalse()
    }

    @Test
    fun `guess text survives process death`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).onAction(GameAction.OnGuessTextChange("lootera shik"))

        assertThat(viewModel(handle).state.value.guessText).isEqualTo("lootera shik")
    }
}
