package com.mukhda.app.ui.game

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import kotlinx.coroutines.delay

/** How long "Back online" stays before the banner goes. */
private const val BACK_ONLINE_MS = 2_000L

private enum class Connection { OFFLINE, BACK }

/**
 * The connection banner, dressed as the stage's pelmet. It drops from the top of the screen, just
 * under the status bar and over the game, while the phone is offline: "YOU'RE OFFLINE" in marigold
 * on ink with a marigold fringe, and what that means now (a try waiting, or clips that still play).
 * When the connection returns it turns marigold with an ink fringe, "BACK ONLINE" and a tick, for a
 * moment, then slides back up. Announced to screen readers.
 */
@Composable
fun ConnectionBanner(isOffline: Boolean, tryWaiting: Boolean, animationsEnabled: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf<Connection?>(if (isOffline) Connection.OFFLINE else null) }
    LaunchedEffect(isOffline) {
        if (isOffline) {
            shown = Connection.OFFLINE
        } else if (shown == Connection.OFFLINE) {
            shown = Connection.BACK
            delay(BACK_ONLINE_MS)
            shown = null
        }
    }
    // Below the status bar, so its icons stay on the paper they're coloured for.
    Box(modifier.statusBarsPadding().clipToBounds()) {
        AnimatedVisibility(
            visible = shown != null,
            enter = if (animationsEnabled) slideInVertically { -it } else EnterTransition.None,
            exit = if (animationsEnabled) slideOutVertically { -it } else ExitTransition.None,
        ) {
            // Keeps showing the last state while it slides away.
            var last by remember { mutableStateOf(Connection.OFFLINE) }
            shown?.let { last = it }
            AnimatedContent(
                targetState = last,
                transitionSpec = { if (animationsEnabled) fadeIn() togetherWith fadeOut() else EnterTransition.None togetherWith ExitTransition.None },
                label = "connection",
            ) { state ->
                when (state) {
                    Connection.OFFLINE -> Banner(
                        background = MukhdaTheme.colors.ink,
                        accent = MukhdaTheme.colors.marigold,
                        text = MukhdaTheme.colors.paper,
                        title = stringResource(R.string.offline_title),
                        detail = stringResource(if (tryWaiting) R.string.offline_waiting else R.string.offline_idle),
                        icon = { NoSignal(MukhdaTheme.colors.marigold) },
                    )
                    Connection.BACK -> Banner(
                        // Deep marigold, so it doesn't read as the Easy tier's colour.
                        background = MukhdaTheme.colors.marigoldDeep,
                        accent = MukhdaTheme.colors.ink,
                        text = MukhdaTheme.colors.ink,
                        title = stringResource(R.string.online_title),
                        detail = stringResource(if (tryWaiting) R.string.online_sending else R.string.online_detail),
                        icon = { Tick(MukhdaTheme.colors.ink) },
                    )
                }
            }
        }
    }
}

/**
 * The stage's pelmet as a notice: a band with the title in label caps and a line of detail, over a
 * fringe in the accent colour, like the curtain's valance and the housefull sheet's header.
 */
@Composable
private fun Banner(background: Color, accent: Color, text: Color, title: String, detail: String, icon: @Composable () -> Unit) {
    val type = MukhdaTheme.typography
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            icon()
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = type.label.copy(fontSize = 13.sp), color = accent)
                Text(detail, style = type.bodySmall, color = text, maxLines = 1)
            }
        }
        // The fringe: a rule and short ticks hanging below, as on the stage.
        Canvas(Modifier.fillMaxWidth().height(FringeHeight)) {
            drawRect(background, size = Size(size.width, size.height * 0.25f))
            val edge = 2.dp.toPx()
            drawRect(accent, topLeft = Offset(0f, size.height * 0.25f - edge / 2), size = Size(size.width, edge))
            val tick = 2.dp.toPx()
            val gap = 9.dp.toPx()
            var x = gap / 2
            while (x < size.width) {
                drawRect(accent, topLeft = Offset(x, size.height * 0.25f), size = Size(tick, size.height * 0.75f))
                x += gap
            }
        }
    }
}

private val FringeHeight = 9.dp

/** Signal bars, struck through. */
@Composable
private fun NoSignal(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        for ((i, height) in listOf(0.35f, 0.6f, 0.9f).withIndex()) {
            val x = w * (0.18f + i * 0.32f)
            drawLine(color.copy(alpha = 0.6f), Offset(x, h), Offset(x, h * (1 - height)), stroke, StrokeCap.Round)
        }
        drawLine(color, Offset(0f, 0f), Offset(w, h), stroke, StrokeCap.Round)
    }
}

/** A tick, for back online. */
@Composable
private fun Tick(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 2.4.dp.toPx()
        val w = size.width
        val h = size.height
        drawLine(color, Offset(w * 0.1f, h * 0.55f), Offset(w * 0.4f, h * 0.85f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.85f), Offset(w * 0.95f, h * 0.15f), stroke, StrokeCap.Round)
    }
}
