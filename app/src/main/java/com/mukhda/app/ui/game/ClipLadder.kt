package com.mukhda.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.audio.ClipProgress
import com.mukhda.domain.model.ClipLadder
import com.mukhda.domain.model.Tier

@StringRes
fun clipLengthRes(step: Int): Int = when (step) {
    0 -> R.string.clip_0
    1 -> R.string.clip_1
    2 -> R.string.clip_2
    3 -> R.string.clip_3
    else -> R.string.clip_full
}

/** Each step's bar grows with its clip: 0.5 s narrowest, the full preview widest. */
private val StepWeights = floatArrayOf(1f, 1.4f, 2.2f, 3.4f, 4f)
private val BarGap = 4.dp
private val BarHeight = 5.dp
/** The play position: a notch as tall as the bar, so nothing sticks out of it. */
private val MarkWidth = 3.dp
/** Until the player knows the full clip's length, the last bar is drawn as this long. */
private const val FULL_CLIP_GUESS_MS = 26_000L

/**
 * "CLIP 2 OF 5 · 0.5 SEC" over five bars: played ones dark, the current one in the tier's colour.
 * While a clip plays it works like a player's progress bar: the bars the clip covers dim and fill
 * with the tier's colour as it plays, with a notch where it has got to.
 */
@Composable
fun ClipLadderStrip(tier: Tier, currentStep: Int, playhead: () -> ClipProgress?, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val bar = tier.color()
    val text = tier.textColor()
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row {
            Text(
                text = stringResource(R.string.clip_of, currentStep + 1, ClipLadder.STEP_COUNT).uppercase() + " · ",
                style = type.label,
                color = colors.muted,
            )
            Text(
                text = stringResource(clipLengthRes(currentStep)).uppercase(),
                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                color = text,
            )
        }
        val stepColors = List(ClipLadder.STEP_COUNT) { step ->
            when {
                step < currentStep -> colors.ladderPlayed
                step == currentStep -> bar
                else -> colors.ladderEmpty
            }
        }
        Canvas(
            Modifier
                .padding(top = 9.dp)
                .fillMaxWidth()
                .height(BarHeight),
        ) {
            val gap = BarGap.toPx()
            val height = size.height
            val top = 0f
            val unit = (size.width - gap * (ClipLadder.STEP_COUNT - 1)) / StepWeights.sum()
            val progress = playhead()
            var x = 0f
            var mark: Float? = null
            for (step in 0 until ClipLadder.STEP_COUNT) {
                val width = unit * StepWeights[step]
                if (progress == null || step > currentStep) {
                    drawRect(stepColors[step], Offset(x, top), Size(width, height))
                } else {
                    // Part of the clip that's playing: dimmed, and lit as the clip passes through.
                    val played = progress.fractionOf(step)
                    drawRect(stepColors[step].copy(alpha = 0.3f), Offset(x, top), Size(width, height))
                    drawRect(bar, Offset(x, top), Size(width * played, height))
                    // The marker: in the bar the clip is in now, or at the end of the last one.
                    if (mark == null && (step == currentStep || played < 1f && progress.fractionOf(step + 1) == 0f)) {
                        // Kept inside its bar, even at the very start or end.
                        val markWidth = MarkWidth.toPx()
                        mark = (x + width * played - markWidth / 2).coerceIn(x, x + width - markWidth)
                    }
                }
                x += width + gap
            }
            mark?.let { drawRect(text, Offset(it, top), Size(MarkWidth.toPx(), height)) }
        }
        Row(
            modifier = Modifier
                .padding(top = 5.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
        ) {
            repeat(ClipLadder.STEP_COUNT) { step ->
                Text(
                    text = stringResource(stepLabelRes(step)).uppercase(),
                    style = type.caption.copy(letterSpacing = type.caption.letterSpacing / 2),
                    color = if (step == currentStep) text else colors.faint,
                    textAlign = if (step == ClipLadder.STEP_COUNT - 1) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier.weight(StepWeights[step]),
                )
            }
        }
    }
}

/**
 * How much of [step]'s bar the clip has played through, 0 to 1. The bars are the clip's timeline:
 * each one runs from where the step before it ends to its own length.
 */
private fun ClipProgress.fractionOf(step: Int): Float {
    if (step >= ClipLadder.STEP_COUNT) return 0f
    val from = if (step == 0) 0L else ClipLadder.clipLengthMs(step - 1)!!
    val to = ClipLadder.clipLengthMs(step) ?: (lengthMs ?: FULL_CLIP_GUESS_MS)
    return ((playedMs - from).toFloat() / (to - from).coerceAtLeast(1)).coerceIn(0f, 1f)
}

/** Short labels under the bars; the first bar is too narrow for "0.5 sec". */
@StringRes
private fun stepLabelRes(step: Int): Int = when (step) {
    0 -> R.string.ladder_0
    1 -> R.string.ladder_1
    2 -> R.string.clip_2
    3 -> R.string.clip_3
    else -> R.string.clip_full
}
