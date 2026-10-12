package com.mukhda.app.ui.game

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.mukhda.domain.model.Tier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val songIndex = FakeSongIndexDataSource()

    @Test
    fun `opening the screen prepares the search index`() {
        GameViewModel(SavedStateHandle(), songIndex)
        assertThat(songIndex.prepareCalls).isEqualTo(1)
    }

    @Test
    fun `tapping a tier selects it`() = runTest {
        val viewModel = GameViewModel(SavedStateHandle(), songIndex)

        viewModel.state.test {
            assertThat(awaitItem().selectedTier).isEqualTo(Tier.EASY)
            viewModel.onAction(GameAction.OnTierClick(Tier.LEGEND))
            assertThat(awaitItem().selectedTier).isEqualTo(Tier.LEGEND)
        }
    }

    @Test
    fun `guess text survives process death`() {
        val handle = SavedStateHandle()
        GameViewModel(handle, songIndex).onAction(GameAction.OnGuessTextChange("lootera shik"))

        val restored = GameViewModel(handle, songIndex)
        assertThat(restored.state.value.guessText).isEqualTo("lootera shik")
    }
}
