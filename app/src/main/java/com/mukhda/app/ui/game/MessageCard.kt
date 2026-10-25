package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mukhda.app.ui.theme.MukhdaTheme

private val ShadowOffset = 4.dp

/**
 * A short message, printed like the poster: a paper card with an ink border and a hard red drop
 * (no blur), in place of the system's black snackbar.
 */
@Composable
fun MessageCard(message: String, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Text(
        text = message,
        style = MukhdaTheme.typography.bodyStrong,
        color = colors.ink,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp + ShadowOffset, bottom = 16.dp + ShadowOffset)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .drawBehind {
                val offset = ShadowOffset.toPx()
                drawRect(colors.red, topLeft = Offset(offset, offset), size = size)
            }
            .background(colors.input)
            .border(2.dp, colors.ink)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
