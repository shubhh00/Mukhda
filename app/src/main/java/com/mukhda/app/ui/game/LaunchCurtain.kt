package com.mukhda.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaColors
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.drawBehindKeyed
import com.mukhda.app.ui.theme.rememberAnimationsEnabled
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val WORDS_OUT_MS = 220
/** The curtain parts quicker than the stage's reveal: the player is waiting to play. */
private const val PART_MS = 900
/** Long enough to read the line, even when the show is already in. */
private const val MIN_HOLD_MS = 700L
/** A slow network never keeps the curtain down: the game shows its own loading layout. */
private const val MAX_HOLD_MS = 2_500L
/** How far each half bunches up as it's drawn off. */
private const val GATHER = 0.45f
private val PelmetDepth = 28.dp

/**
 * The curtain the app opens on: मुखड़ा and what the game is, on closed velvet. Once the first
 * show is in ([ready]) and the line has had time to be read, the words fade and the curtain parts
 * on the game; then [onOpened] takes it away. Moves only through [graphicsLayer]. With animations
 * off it goes as soon as the show is ready.
 */
@Composable
fun LaunchCurtain(ready: Boolean, onOpened: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val animationsEnabled = rememberAnimationsEnabled()
    // Up from the first frame: the start-up wait is already behind the player.
    val words = remember { Animatable(1f) }
    val part = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val opened by rememberUpdatedState(onOpened)
    LaunchedEffect(Unit) {
        val shownAt = TimeSource.Monotonic.markNow()
        withTimeoutOrNull(MAX_HOLD_MS) { snapshotFlow { isReady }.first { it } }
        if (animationsEnabled) {
            delay(MIN_HOLD_MS.milliseconds - shownAt.elapsedNow())
            coroutineScope {
                launch { words.animateTo(0f, tween(WORDS_OUT_MS)) }
                part.animateTo(1f, tween(PART_MS, delayMillis = WORDS_OUT_MS / 2, easing = FastOutSlowInEasing))
            }
        }
        opened()
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            // The game underneath isn't playable until the curtain is up.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
    ) {
        LaunchHalf(left = true, colors = colors, part = { part.value })
        LaunchHalf(left = false, colors = colors, part = { part.value })
        Pelmet(colors = colors, part = { part.value })
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    alpha = words.value
                    val scale = 0.94f + 0.06f * words.value
                    scaleX = scale
                    scaleY = scale
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.wordmark),
                style = MukhdaTheme.typography.brand.copy(
                    fontSize = 76.sp,
                    shadow = Shadow(colors.velvetDeep, offset = Offset(0f, 8f)),
                ),
                color = colors.marigold,
            )
            // The double rule under the name, as on the app icon.
            Box(Modifier.width(196.dp).height(6.dp).background(colors.marigold))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.width(144.dp).height(3.dp).background(colors.marigold))
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.launch_tagline).uppercase(),
                style = MukhdaTheme.typography.label.copy(fontSize = 13.sp, letterSpacing = 0.24.em, lineHeight = 24.sp),
                color = colors.amberBg,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One half of the closed curtain; drawn off to its side, bunching up, as [part] goes to 1. */
@Composable
private fun BoxScope.LaunchHalf(left: Boolean, colors: MukhdaColors, part: () -> Float) {
    Box(
        modifier = Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.5f)
            .graphicsLayer {
                transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                scaleX = 1f - GATHER * part()
                // Bunched to (1 - GATHER) of its width, this is just far enough to clear the
                // screen, so the gap widens evenly over the whole move.
                translationX = (if (left) -1f else 1f) * size.width * (1f - GATHER) * part()
            }
            .drawBehindKeyed(colors to left) {
                drawVelvet(colors, innerEdgeOnRight = left)
                drawStageShade()
            },
    )
}

/** Ink pelmet with a marigold fringe across the top, under the status bar; it lifts away last. */
@Composable
private fun BoxScope.Pelmet(colors: MukhdaColors, part: () -> Float) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(top + PelmetDepth + 10.dp)
            .graphicsLayer { translationY = -size.height * part() }
            .drawBehindKeyed(colors to top) {
                val band = (top + PelmetDepth).toPx()
                val edge = 2.dp.toPx()
                drawRect(colors.ink, size = Size(size.width, band))
                drawRect(colors.marigoldDeep, topLeft = Offset(0f, band), size = Size(size.width, edge))
                val tick = 2.dp.toPx()
                val gap = 9.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(colors.marigoldDeep, topLeft = Offset(x, band + edge), size = Size(tick, size.height - band - edge))
                    x += gap
                }
            },
    )
}
