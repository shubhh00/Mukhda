package com.mukhda.app.ui.game

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.TierColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val BULB_COUNT = 28
private const val PRESS_MS = 90

/**
 * Vinyl with a play/pause button in the middle and a ring of marquee bulbs around it. Static on
 * purpose: the only motion is the button shrinking slightly while it's pressed.
 */
@Composable
fun RecordPlayer(
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    onPlayClick: () -> Unit,
    /** The selected tier's colours, for the button. */
    accent: TierColors,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
) {
    val colors = MukhdaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed && animationsEnabled) 0.9f else 1f,
        animationSpec = tween(PRESS_MS),
        label = "pressScale",
    )

    val playLabel = stringResource(if (isPlaying) R.string.cd_stop_clip else R.string.cd_play_clip)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawBulbRing(colors.saffron, colors.maroon) }
        Canvas(Modifier.fillMaxSize(0.78f)) { drawVinyl(colors.ink, colors.maroonBand) }
        Box(
            modifier = Modifier
                .size(size * 0.3f)
                .graphicsLayer {
                    scaleX = buttonScale
                    scaleY = buttonScale
                }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onPlayClick)
                .semantics {
                    role = Role.Button
                    contentDescription = playLabel
                },
        ) {
            Canvas(Modifier.fillMaxSize()) { drawPlayButton(accent.container, accent.content, isPlaying) }
        }
    }
}

private fun DrawScope.drawBulbRing(bulb: Color, outline: Color) {
    val r = min(size.width, size.height) / 2 - 8.dp.toPx()
    val bulbRadius = 4.dp.toPx()
    repeat(BULB_COUNT) { i ->
        val angle = Math.toRadians(i * 360.0 / BULB_COUNT)
        val c = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
        drawCircle(bulb.copy(alpha = 0.35f), radius = bulbRadius * 1.9f, center = c)
        drawCircle(bulb, radius = bulbRadius, center = c)
        drawCircle(outline, radius = bulbRadius, center = c, style = Stroke(width = 1.dp.toPx()))
    }
}

private fun DrawScope.drawVinyl(disc: Color, label: Color) {
    val r = min(size.width, size.height) / 2
    drawCircle(disc, radius = r)
    val groove = Color.White.copy(alpha = 0.07f)
    var gr = r * 0.93f
    while (gr > r * 0.42f) {
        drawCircle(groove, radius = gr, style = Stroke(width = 1.dp.toPx()))
        gr -= 5.dp.toPx()
    }
    drawCircle(label, radius = r * 0.42f)
}

private fun DrawScope.drawPlayButton(fill: Color, glyph: Color, isPlaying: Boolean) {
    val r = min(size.width, size.height) / 2
    drawCircle(fill, radius = r)
    drawCircle(glyph, radius = r, style = Stroke(width = 2.dp.toPx()))
    val s = r * 0.42f
    if (isPlaying) {
        val barW = s * 0.42f
        drawRect(glyph, topLeft = Offset(center.x - s * 0.75f, center.y - s), size = Size(barW, s * 2))
        drawRect(glyph, topLeft = Offset(center.x + s * 0.75f - barW, center.y - s), size = Size(barW, s * 2))
    } else {
        val triangle = Path().apply {
            moveTo(center.x - s * 0.7f, center.y - s)
            lineTo(center.x + s, center.y)
            lineTo(center.x - s * 0.7f, center.y + s)
            close()
        }
        drawPath(triangle, glyph)
    }
}
