package com.mukhda.app.ui.game

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mukhda.app.R
import com.mukhda.app.ui.common.ObserveAsEvents
import com.mukhda.app.ui.common.UiText
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.halftone
import com.mukhda.app.ui.theme.hardShadow
import com.mukhda.app.ui.theme.rememberAnimationsEnabled
import com.mukhda.domain.model.Tier
import kotlinx.coroutines.launch

@Composable
fun GameRoot(viewModel: GameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is GameEvent.ShowMessage -> scope.launch { snackbar.showSnackbar(event.message.asString(context)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
        GameScreen(state = state, onAction = viewModel::onAction, animationsEnabled = rememberAnimationsEnabled())
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
}

@Composable
fun GameScreen(state: GameState, onAction: (GameAction) -> Unit, animationsEnabled: Boolean = true) {
    val colors = MukhdaTheme.colors
    val accent = state.selectedTier.colors()
    // Changes only when a tier is tapped; read at draw time, so the crossfade doesn't recompose.
    val wash = animateColorAsState(
        targetValue = accent.container,
        animationSpec = tween(if (animationsEnabled) TIER_CROSSFADE_MS else 0),
        label = "tierWash",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .halftone(colors.pink)
            .tierWash { wash.value },
    ) {
        TitleBand(showNo = state.showNo, dateLabel = state.dateLabel, streak = state.streak)
        val round = state.round
        when {
            state.loadError != null -> LoadError(state.loadError, onRetry = { onAction(GameAction.OnRetryClick) })
            state.isLoading || round == null -> Loading()
            else -> {
                TierTabs(selected = state.selectedTier, onTierClick = { onAction(GameAction.OnTierClick(it)) })
                // While searching, the dropdown takes this space, so the guess bar and the Apple
                // Music credit below it never get pushed off screen by the keyboard.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    if (state.isDropdownOpen) {
                        SearchDropdown(
                            state = state,
                            onAction = onAction,
                            modifier = Modifier
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                        )
                    } else {
                        RoundContent(state, round, animationsEnabled, onAction)
                    }
                }
                GuessBar(state = state, onAction = onAction, modifier = Modifier.navigationBarsPadding().imePadding())
            }
        }
    }
}

private const val TIER_CROSSFADE_MS = 300

/** A soft wash of the selected tier's colour from the top of the paper, fading out by mid-screen. */
private fun Modifier.tierWash(color: () -> Color): Modifier = drawBehind {
    drawRect(Brush.verticalGradient(0f to color().copy(alpha = 0.30f), 0.6f to Color.Transparent))
}

@Composable
private fun RoundContent(state: GameState, round: RoundUi, animationsEnabled: Boolean, onAction: (GameAction) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RecordPlayer(
                isPlaying = state.isPlaying,
                animationsEnabled = animationsEnabled,
                onPlayClick = { onAction(GameAction.OnPlayClick) },
                accent = state.selectedTier.accentColors(),
                modifier = Modifier.padding(top = 4.dp),
            )
            // Apple requires the credit where previews play; small, right under the record.
            Text(
                text = stringResource(R.string.apple_music_credit),
                style = MukhdaTheme.typography.label.copy(fontSize = 9.sp),
                color = MukhdaTheme.colors.muted,
            )
        }
        ClipLadderStrip(currentStep = round.currentStep, accent = state.selectedTier.accentColors())
        if (!round.isOpen) RoundOver(won = round.isWon)
        TrySlots(slots = round.slots, modifier = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun TierTabs(selected: Tier, onTierClick: (Tier) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tier.entries.forEach { tier ->
            val tierColors = tier.colors()
            val isSelected = tier == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    // The selected tab is raised.
                    .graphicsLayer { translationY = if (isSelected) -4.dp.toPx() else 0f }
                    .then(if (isSelected) Modifier.hardShadow(MukhdaTheme.colors.maroon, 3.dp) else Modifier)
                    .background(tierColors.container)
                    .border(2.dp, MukhdaTheme.colors.maroon)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onTierClick(tier) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tier.labelRes).uppercase(),
                    style = MukhdaTheme.typography.label,
                    color = tierColors.content,
                )
            }
        }
    }
}

@Composable
private fun RoundOver(won: Boolean) {
    Text(
        text = stringResource(if (won) R.string.round_won else R.string.round_lost),
        style = MukhdaTheme.typography.bodyStrong,
        color = MukhdaTheme.colors.maroon,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MukhdaTheme.colors.pink)
    }
}

@Composable
private fun LoadError(message: UiText, onRetry: () -> Unit) {
    val colors = MukhdaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message.asString(), style = MukhdaTheme.typography.body, color = colors.ink, textAlign = TextAlign.Center)
        Text(
            text = stringResource(R.string.retry).uppercase(),
            style = MukhdaTheme.typography.label,
            color = colors.ink,
            modifier = Modifier
                .padding(top = 16.dp)
                .hardShadow(colors.maroon, 3.dp)
                .background(colors.saffron)
                .border(2.dp, colors.maroon)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Preview
@Composable
private fun GameScreenPreview() {
    MukhdaTheme {
        GameScreen(
            state = GameState(
                isLoading = false,
                showNo = 12,
                dateLabel = "MON 13 OCT",
                streak = 12,
                selectedTier = Tier.HARD,
                rounds = Tier.entries.associateWith {
                    RoundUi(
                        currentStep = 2,
                        slots = listOf(
                            TrySlotUi.Used(1, "Ishq Jaisa Kuch", "Fighter · 2024", Stamp.WRONG),
                            TrySlotUi.Used(2, "Sher Khul Gaye", "Fighter · 2024", Stamp.RIGHT_FILM),
                            TrySlotUi.Empty(3),
                            TrySlotUi.Empty(4),
                            TrySlotUi.Empty(5),
                        ),
                        isOpen = true,
                        isWon = false,
                    )
                },
                guessText = "kal",
                suggestions = listOf(
                    SuggestionUi(SongUi("s7", "Kal Ho Naa Ho", "Kal Ho Naa Ho · 2003"), alreadyGuessed = false),
                    SuggestionUi(SongUi("s8", "Kuch To Hua Hai", "Kal Ho Naa Ho · 2003"), alreadyGuessed = true),
                ),
                suggestionsTotal = 7,
            ),
            onAction = {},
        )
    }
}
