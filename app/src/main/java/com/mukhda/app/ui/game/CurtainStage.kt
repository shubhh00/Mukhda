package com.mukhda.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaColors
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.drawBehindKeyed
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

private const val PRESS_MS = 90
private const val SWAY_MS = 1000
private const val GLOW_MS = 120
private val SwayDistance = 5.dp
private val ValanceHeight = 24.dp
private const val BUTTON_FRACTION = 0.5f
private val MinButton = 80.dp
private val MaxButton = 128.dp

/**
 * "Now showing": a framed stage with a closed velvet curtain and the play button. Nothing on it
 * hints at the answer. The only motion follows a tap: while a clip plays the footlights come up
 * and the curtain sways once. It fills the size [modifier] gives it; the button scales with it.
 */
@Composable
fun CurtainStage(
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MukhdaTheme.colors
    val sway = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, animationsEnabled) {
        if (isPlaying && animationsEnabled) {
            sway.snapTo(0f)
            sway.animateTo(1f, tween(SWAY_MS))
        }
        sway.snapTo(0f)
    }
    val glow by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) GLOW_MS else 0),
        label = "footlights",
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.5.dp, colors.ink)
                .padding(6.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(colors.ink),
                contentAlignment = Alignment.Center,
            ) {
                val buttonSize = (min(maxWidth, maxHeight) * BUTTON_FRACTION).coerceIn(MinButton, MaxButton)
                // Each half is its own layer, so a sway only moves cached drawings.
                CurtainHalf(left = true, colors = colors, swayPx = { swayOffset(sway.value) })
                CurtainHalf(left = false, colors = colors, swayPx = { swayOffset(sway.value) })
                Box(Modifier.fillMaxSize().drawBehindKeyed(Unit) { drawStageShade() })
                Valance(colors)
                // Lettered on the pelmet, like a cinema's marquee board.
                Text(
                    text = stringResource(R.string.now_showing).uppercase(),
                    style = MukhdaTheme.typography.caption.copy(letterSpacing = MukhdaTheme.typography.caption.letterSpacing * 2),
                    color = colors.marigold,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .height(ValanceHeight)
                        .wrapContentHeight(Alignment.CenterVertically),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.42f)
                        .graphicsLayer { alpha = glow }
                        .drawBehindKeyed(colors.marigold) {
                            drawRect(Brush.verticalGradient(listOf(colors.marigold.copy(alpha = 0f), colors.marigold.copy(alpha = 0.55f))))
                        },
                )
                PlayButton(isPlaying, animationsEnabled, size = buttonSize, onClick = onPlayClick)
            }
        }
        FrameCorners(colors)
    }
}

/** One full sway: out and back, peaking halfway. */
private fun Density.swayOffset(progress: Float): Float =
    sin(progress * PI).toFloat() * SwayDistance.toPx()

@Composable
private fun BoxScope.CurtainHalf(
    left: Boolean,
    colors: MukhdaColors,
    swayPx: Density.() -> Float,
) {
    Box(
        modifier = Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.52f)
            .graphicsLayer { translationX = if (left) -swayPx() else swayPx() }
            .drawBehindKeyed(colors to left) { drawVelvet(colors, innerEdgeOnRight = left) },
    )
}

/** Vertical folds: dark, mid, highlight, mid, dark, deepest, every 26 dp; shaded toward the opening. */
private fun DrawScope.drawVelvet(colors: MukhdaColors, innerEdgeOnRight: Boolean) {
    val period = 26.dp.toPx()
    drawRect(
        Brush.horizontalGradient(
            0f to colors.velvetDark,
            5f / 26 to colors.velvetMid,
            9f / 26 to colors.velvetLight,
            14f / 26 to colors.velvetMid,
            19f / 26 to colors.velvetDark,
            23f / 26 to colors.velvetDeep,
            1f to colors.velvetDark,
            startX = 0f,
            endX = period,
            tileMode = TileMode.Repeated,
        ),
    )
    val shadow = 16.dp.toPx()
    val edge = if (innerEdgeOnRight) size.width else 0f
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0f), Color.Black.copy(alpha = 0.45f)),
            startX = if (innerEdgeOnRight) edge - shadow else shadow,
            endX = edge,
        ),
    )
}

/** Darker at the top under the valance and at the hem, like stage lighting. */
private fun DrawScope.drawStageShade() {
    drawRect(
        Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.3f),
            0.32f to Color.Black.copy(alpha = 0f),
            0.68f to Color.Black.copy(alpha = 0f),
            1f to Color.Black.copy(alpha = 0.4f),
        ),
    )
}

/** Ink pelmet with a marigold edge and fringe. */
@Composable
private fun BoxScope.Valance(colors: MukhdaColors) {
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(33.dp)
            .drawBehindKeyed(colors) {
                val band = ValanceHeight.toPx()
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

/** Red diamonds on the frame's four corners. */
@Composable
private fun BoxScope.FrameCorners(colors: MukhdaColors) {
    val corner = Modifier.size(8.dp).rotate(45f).background(colors.red)
    Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).then(corner))
    Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).then(corner))
    Box(Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp).then(corner))
    Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).then(corner))
}

/** Red disc ringed in marigold and ink; the glyph is marigold. Shrinks a little while pressed. */
@Composable
private fun PlayButton(isPlaying: Boolean, animationsEnabled: Boolean, size: Dp, onClick: () -> Unit) {
    val colors = MukhdaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animationsEnabled) 0.92f else 1f,
        animationSpec = tween(PRESS_MS),
        label = "pressScale",
    )
    val label = stringResource(if (isPlaying) R.string.cd_stop_clip else R.string.cd_play_clip)
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
            },
    ) {
        Canvas(Modifier.fillMaxSize()) { drawPlayButton(colors, isPlaying) }
    }
}

private fun DrawScope.drawPlayButton(colors: MukhdaColors, isPlaying: Boolean) {
    val r = min(size.width, size.height) / 2
    drawCircle(colors.ink, radius = r)
    drawCircle(colors.marigold, radius = r - 2.5.dp.toPx())
    val disc = r - 7.dp.toPx()
    drawCircle(colors.buttonRed, radius = disc)
    val s = disc * 0.36f
    if (isPlaying) {
        drawRect(colors.marigold, topLeft = Offset(center.x - s * 0.8f, center.y - s * 0.8f), size = Size(s * 1.6f, s * 1.6f))
    } else {
        val triangle = Path().apply {
            moveTo(center.x - s * 0.7f, center.y - s)
            lineTo(center.x + s, center.y)
            lineTo(center.x - s * 0.7f, center.y + s)
            close()
        }
        drawPath(triangle, colors.marigold)
    }
}
