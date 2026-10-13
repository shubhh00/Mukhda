package com.mukhda.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.TierColors
import com.mukhda.domain.model.ClipLadder

@StringRes
fun clipLengthRes(step: Int): Int = when (step) {
    0 -> R.string.clip_0
    1 -> R.string.clip_1
    2 -> R.string.clip_2
    3 -> R.string.clip_3
    else -> R.string.clip_full
}

/** "1 sec · clip 3 of 5" over a film strip of five frames: played ones filled, the current one lit. */
@Composable
fun ClipLadderStrip(currentStep: Int, accent: TierColors, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(
                R.string.clip_label,
                stringResource(clipLengthRes(currentStep)),
                currentStep + 1,
                ClipLadder.STEP_COUNT,
            ).uppercase(),
            style = MukhdaTheme.typography.label,
            color = colors.ink,
        )
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .background(colors.ink)
                .filmSprockets(colors.paper)
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(ClipLadder.STEP_COUNT) { step ->
                val (fill, text) = when {
                    step < currentStep -> colors.maroonBand to colors.paperLight
                    step == currentStep -> accent.container to accent.content
                    else -> colors.paperLight.copy(alpha = 0.18f) to colors.paperLight.copy(alpha = 0.6f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(30.dp)
                        .background(fill)
                        .then(if (step == currentStep) Modifier.border(2.dp, colors.paperLight) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(clipLengthRes(step)), style = MukhdaTheme.typography.label, color = text)
                }
            }
        }
    }
}

/** Sprocket holes along the top and bottom edges of the strip. */
private fun Modifier.filmSprockets(hole: Color): Modifier = drawBehind {
    val w = 6.dp.toPx()
    val h = 4.dp.toPx()
    val gap = 12.dp.toPx()
    var x = gap / 2
    while (x + w < size.width) {
        drawRect(hole, Offset(x, 2.dp.toPx()), Size(w, h))
        drawRect(hole, Offset(x, size.height - h - 2.dp.toPx()), Size(w, h))
        x += gap
    }
}
