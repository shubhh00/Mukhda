package com.mukhda.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mukhda.app.R
import com.mukhda.app.ui.common.ObserveAsEvents
import com.mukhda.app.ui.common.UiText
import com.mukhda.app.ui.common.rememberHideKeyboard
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.drawBehindKeyed
import com.mukhda.app.ui.theme.paper
import com.mukhda.app.ui.theme.rememberAnimationsEnabled
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.Verdict
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Composable
fun GameRoot(viewModel: GameViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val animationsEnabled = rememberAnimationsEnabled()
    // Counts wins this session; each new one pops a burst of petals from the stage's middle,
    // as big as the verdict.
    var celebrations by remember { mutableIntStateOf(0) }
    var celebrated by remember { mutableStateOf(Verdict.HIT) }
    val stageCenter = remember { mutableStateOf(Offset.Unspecified) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            // A new message replaces the one showing, so repeated hints don't queue up and an
            // error isn't shown long after the thing that caused it.
            is GameEvent.ShowMessage -> scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(event.message.asString(context))
            }
            is GameEvent.Celebrate -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                celebrated = event.verdict
                celebrations++
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // One stable callback, so children taking only onAction can skip recomposition.
        val onAction = remember(viewModel) { viewModel::onAction }
        GameScreen(
            state = state,
            onAction = onAction,
            animationsEnabled = animationsEnabled,
            onStageCenter = { stageCenter.value = it },
        )
        PetalBurst(
            trigger = celebrations,
            verdict = celebrated,
            origin = { stageCenter.value },
            animationsEnabled = animationsEnabled,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }
}

@Composable
fun GameScreen(
    state: GameState,
    onAction: (GameAction) -> Unit,
    animationsEnabled: Boolean = true,
    /** Where the stage's middle is on screen, for the win's petal burst. */
    onStageCenter: (Offset) -> Unit = {},
) {
    val colors = MukhdaTheme.colors
    val hideKeyboard = rememberHideKeyboard()
    // Height of the guess bar (with the keyboard's padding); taps there keep the keyboard up.
    val barHeight = remember { mutableIntStateOf(0) }
    ClearFocusWhenKeyboardCloses()
    // Back closes the suggestion list before it leaves the app.
    BackHandler(enabled = state.isDropdownOpen) { onAction(GameAction.OnDismissSuggestions) }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .paper(colors, dots = colors.red.copy(alpha = 0.10f))
                .hideKeyboardOnTapAbove(hideKeyboard) { barHeight.intValue },
        ) {
            ShowHeader(showNo = state.showNo, dateLabel = state.dateLabel, streak = state.streak)
            val round = state.round
            when {
                state.loadError != null -> LoadError(state.loadError, onRetry = { onAction(GameAction.OnRetryClick) })
                state.isLoading || round == null -> Loading()
                else -> {
                    TierTabs(
                        selected = state.selectedTier,
                        finished = state.rounds.filterValues { !it.isOpen }.keys,
                        onTierClick = { onAction(GameAction.OnTierClick(it)) },
                    )
                    // The suggestion list floats over the lower part of the round, just above the guess
                    // box. With the keyboard up it fills this space and scrolls; with the keyboard down
                    // the record stays visible above it. A tap anywhere else closes it.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                        RoundContent(state.selectedTier, round, state.isPlaying, animationsEnabled, onAction, onStageCenter)
                        if (state.isDropdownOpen) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(interactionSource = null, indication = null) {
                                        hideKeyboard()
                                        onAction(GameAction.OnDismissSuggestions)
                                    },
                            )
                            SearchDropdown(
                                suggestions = state.suggestions,
                                totalMatches = state.suggestionsTotal,
                                isPlaying = state.isPlaying,
                                onAction = onAction,
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 6.dp),
                            )
                        }
                    }
                    // Kept across recompositions: a fresh insets modifier never equals the last one, so the
                    // bar would recompose on every state change, even a play tap.
                    val barInsets = remember {
                        // Measured outside the paddings, so the height includes the keyboard's space.
                        Modifier.onSizeChanged { barHeight.intValue = it.height }.navigationBarsPadding().imePadding()
                    }
                    if (round.isOpen) {
                        GuessBar(
                            guessText = state.guessText,
                            roundOpen = true,
                            canGuess = state.canGuess,
                            canSkip = state.canSkip,
                            onAction = onAction,
                            modifier = barInsets,
                        )
                    } else {
                        ResultBar(
                            nextTier = state.nextTier,
                            endsAtEpochMs = state.endsAtEpochMs,
                            onAction = onAction,
                            modifier = barInsets,
                        )
                    }
                }
            }
        }
        val round = state.round
        if (state.isTicketOpen && round?.verdict != null) {
            TicketOverlay(
                ticket = TicketUi(
                    showNo = state.showNo,
                    dateLabel = state.dateLabel,
                    tier = state.selectedTier,
                    verdict = round.verdict,
                    solvedAtStep = round.solvedAtStep,
                    slots = round.slots,
                    streak = state.streak,
                ),
                round = round,
                onAction = onAction,
            )
        }
    }
}

/**
 * Drops focus from the guess box once the keyboard is down, whether Back or a tap closed it.
 * Clearing focus while the keyboard is still up makes Gboard redraw a blank keyboard on its way
 * out, which flashes; so everything else only hides the keyboard and leaves focus to this.
 */
@Composable
private fun ClearFocusWhenKeyboardCloses() {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(ime, density) {
        // The keyboard's height, not isImeVisible: that turns false as the slide-down starts, and
        // clearing focus mid-slide still interrupts it. Read in a flow, so the slide doesn't recompose.
        snapshotFlow { ime.getBottom(density) > 0 }
            .distinctUntilChanged()
            .drop(1)
            .collect { up ->
                if (!up) focusManager.clearFocus()
            }
    }
}

/**
 * A tap (not a scroll) anywhere above the bottom [keepHeight] px closes the keyboard. It only
 * watches: the tap still reaches the button or row under it. Focus is dropped later, once the
 * keyboard is down (see [ClearFocusWhenKeyboardCloses]).
 */
private fun Modifier.hideKeyboardOnTapAbove(hideKeyboard: () -> Unit, keepHeight: () -> Int): Modifier =
    pointerInput(hideKeyboard) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.y >= size.height - keepHeight()) return@awaitEachGesture
            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) hideKeyboard()
        }
    }

/**
 * Takes only what it shows, so a keystroke in the guess box doesn't rebuild the record, ladder
 * and slots, and tapping play only touches the record.
 */
@Composable
private fun RoundContent(
    tier: Tier,
    round: RoundUi,
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    onAction: (GameAction) -> Unit,
    onStageCenter: (Offset) -> Unit,
) {
    val onPlayClick = { onAction(GameAction.OnPlayClick) }
    val reveal = round.reveal
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val detailsHeight = if (reveal != null) RevealDetailsHeight else RoundDetailsHeight
        // Everything fits: the stage takes whatever height the details leave. On a short phone it
        // keeps a fixed height and the rest scrolls.
        val fits = maxHeight >= MinStageHeight + detailsHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // One stage per tier, at one place in the tree: when the round ends it stays the same
            // stage, so its curtain can draw back. Another tier gets its own, already open or shut.
            key(tier) {
                CurtainStage(
                    isPlaying = isPlaying,
                    animationsEnabled = animationsEnabled,
                    onPlayClick = onPlayClick,
                    reveal = reveal,
                    accent = tier.color(),
                    marquee = marquee(round, isPlaying),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .then(if (fits) Modifier.weight(1f) else Modifier.height(MinStageHeight))
                        .onGloballyPositioned { onStageCenter(it.boundsInRoot().center) },
                )
            }
            if (reveal != null) RevealDetails(round, reveal, isPlaying, onAction) else RoundDetails(round)
        }
    }
}

/**
 * The valance's lettering: the round told as a film's running order, from the opening to the
 * climax on the last try, then how it ended.
 */
@Composable
private fun marquee(round: RoundUi, isPlaying: Boolean): String = stringResource(
    when {
        !round.isOpen && isPlaying -> R.string.marquee_encore
        round.isWon -> R.string.marquee_won
        !round.isOpen -> R.string.marquee_lost
        else -> when (round.currentStep) {
            0 -> R.string.now_showing
            1 -> R.string.marquee_reel_two
            2 -> R.string.marquee_interval
            3 -> R.string.marquee_second_half
            else -> R.string.marquee_climax
        }
    },
)

@Composable
private fun RoundDetails(round: RoundUi) {
    ClipLadderStrip(currentStep = round.currentStep, modifier = Modifier.padding(top = 12.dp))
    if (!round.isOpen) RoundOver(won = round.isWon, modifier = Modifier.padding(top = 10.dp))
    TrySlots(slots = round.slots, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
}

private val MinStageHeight = 200.dp
/** The ladder, round-over line and five tries take about 330 dp. */
private val RoundDetailsHeight = 330.dp
/** The song, its actions and the result line take about 210 dp. */
private val RevealDetailsHeight = 210.dp

/** Five tabs set in credit-line caps: a dot in each tier's ink, the selected one underlined in it. */
@Composable
private fun TierTabs(selected: Tier, finished: Set<Tier>, onTierClick: (Tier) -> Unit) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .selectableGroup(),
    ) {
        Tier.entries.forEach { tier ->
            val isSelected = tier == selected
            val tierColor = tier.color()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onTierClick(tier) })
                    .drawBehindKeyed(Triple(isSelected, tierColor, colors.rule)) {
                        val line = if (isSelected) 3.dp.toPx() else 1.dp.toPx()
                        drawRect(
                            color = if (isSelected) tierColor else colors.rule,
                            topLeft = Offset(0f, size.height - line),
                            size = Size(size.width, line),
                        )
                    }
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tier in finished) Tick(tierColor) else Box(Modifier.size(6.dp).background(tierColor, CircleShape))
                Text(
                    text = stringResource(tier.labelRes).uppercase(),
                    style = if (isSelected) type.label.copy(fontWeight = FontWeight.SemiBold) else type.label,
                    color = if (isSelected) colors.ink else colors.faint,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/** A small check in the tier's ink, on the tab of a round that's over. */
@Composable
private fun Tick(color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(color, Offset(size.width * 0.1f, size.height * 0.55f), Offset(size.width * 0.4f, size.height * 0.85f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.95f, size.height * 0.15f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun RoundOver(won: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(if (won) R.string.round_won else R.string.round_lost),
        style = MukhdaTheme.typography.bodyStrong,
        color = if (won) MukhdaTheme.colors.teal else MukhdaTheme.colors.red,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MukhdaTheme.colors.red)
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
            style = MukhdaTheme.typography.button,
            color = colors.marigold,
            modifier = Modifier
                .padding(top = 16.dp)
                .background(colors.buttonRed)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 28.dp, vertical = 14.dp),
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
