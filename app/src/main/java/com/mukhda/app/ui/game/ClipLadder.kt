package com.mukhda.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.model.ClipLadder

@StringRes
fun clipLengthRes(step: Int): Int = when (step) {
    0 -> R.string.clip_0
    1 -> R.string.clip_1
    2 -> R.string.clip_2
    3 -> R.string.clip_3
    else -> R.string.clip_full
}

/** Each step's bar is roughly as wide as its clip is long: 0.1 s and 0.5 s narrow, full the widest. */
private val StepWeights = floatArrayOf(1f, 1f, 2f, 3f, 5f)

/** "CLIP 2 OF 5 · 0.5 SEC" over five bars: played ones dark, the current one red. */
@Composable
fun ClipLadderStrip(currentStep: Int, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
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
                color = colors.red,
            )
        }
        Row(
            modifier = Modifier
                .padding(top = 9.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(ClipLadder.STEP_COUNT) { step ->
                val color = when {
                    step < currentStep -> colors.ladderPlayed
                    step == currentStep -> colors.red
                    else -> colors.ladderEmpty
                }
                Box(Modifier.weight(StepWeights[step]).height(5.dp).background(color))
            }
        }
        Row(
            modifier = Modifier
                .padding(top = 5.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(ClipLadder.STEP_COUNT) { step ->
                Text(
                    text = stringResource(stepLabelRes(step)).uppercase(),
                    style = type.caption.copy(letterSpacing = type.caption.letterSpacing / 2),
                    color = if (step == currentStep) colors.red else colors.faint,
                    textAlign = if (step == ClipLadder.STEP_COUNT - 1) TextAlign.End else TextAlign.Start,
                    maxLines = 1,
                    modifier = Modifier.weight(StepWeights[step]),
                )
            }
        }
    }
}

/** Short labels under the bars; the first two bars are too narrow for "0.1 sec". */
@StringRes
private fun stepLabelRes(step: Int): Int = when (step) {
    0 -> R.string.ladder_0
    1 -> R.string.ladder_1
    2 -> R.string.clip_2
    3 -> R.string.clip_3
    else -> R.string.clip_full
}
