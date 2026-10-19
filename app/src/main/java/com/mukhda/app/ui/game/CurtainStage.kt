package com.mukhda.app.ui.game

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaColors
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.drawBehindKeyed
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val PRESS_MS = 90
private const val SWAY_OUT_MS = 250
private const val SWAY_BACK_MS = 300
private const val GLOW_MS = 120
/** One lap of the light running along the footlights. */
private const val CHASE_MS = 1_400
/** Lit stretches running along the row at once. */
private const val CHASE_WAVES = 3
private val FootlightsHeight = 18.dp
private val BulbSpacing = 22.dp
private val BulbRadius = 2.5.dp
private const val MARQUEE_FADE_MS = 180
private val SwayDistance = 5.dp
private val ValanceHeight = 24.dp
private const val BUTTON_FRACTION = 0.5f
private const val HALF_FRACTION = 0.52f
/** The narrowest each gathered curtain gets, as a fraction of the stage width. */
private const val MIN_SIDE_FRACTION = 0.06f
/** The valance with its fringe; the cover sits below it. */
private val PelmetHeight = 33.dp
/** The curtain draws back slowly: it's the reveal. */
private const val PART_MS = 1_600
/** How long the curtain waits for the cover before opening anyway. */
private const val POSTER_WAIT_MS = 1_500L
private val MinButton = 80.dp
private val MaxButton = 128.dp

/**
 * "Now showing": a framed stage with a closed velvet curtain and the play button. Nothing on it
 * hints at the answer. The only motion follows a tap: while a clip plays the footlights come up
 * and the curtain eases open a touch. It fills the size [modifier] gives it; the button scales with it.
 *
 * Once the round is over, [reveal] arrives and the curtain parts on the soundtrack's cover,
 * gathering at both sides. A round already over when the stage appears shows it open, without
 * the animation.
 */
@Composable
fun CurtainStage(
    isPlaying: Boolean,
    animationsEnabled: Boolean,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
    reveal: RevealUi? = null,
    /** The tier's colour, for the frame and its corners, so the stage matches the selected tab. */
    accent: Color = MukhdaTheme.colors.ink,
    /** Lettered on the valance: what's on now. */
    marquee: String = stringResource(R.string.now_showing),
) {
    val colors = MukhdaTheme.colors
    val revealed = reveal != null
    val openness = remember { Animatable(if (revealed) 1f else 0f) }
    // Set once the cover has loaded (or failed), so the curtain opens on the picture rather than
    // on an empty stage that fills in a moment later.
    var posterLoaded by remember(reveal?.artworkUrl) { mutableStateOf(reveal?.artworkUrl == null) }
    LaunchedEffect(revealed, animationsEnabled) {
        if (!revealed) {
            openness.snapTo(0f)
            return@LaunchedEffect
        }
        if (!animationsEnabled) {
            openness.snapTo(1f)
            return@LaunchedEffect
        }
        withTimeoutOrNull(POSTER_WAIT_MS) { snapshotFlow { posterLoaded }.first { it } }
        openness.animateTo(1f, tween(PART_MS, easing = FastOutSlowInEasing))
    }
    // The curtain eases open a touch while sound plays and settles back when it stops, so the
    // motion lasts exactly as long as the clip.
    val sway = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, animationsEnabled) {
        if (!animationsEnabled) {
            sway.snapTo(0f)
        } else {
            sway.animateTo(if (isPlaying) 1f else 0f, tween(if (isPlaying) SWAY_OUT_MS else SWAY_BACK_MS))
        }
    }
    val glow by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(if (animationsEnabled) GLOW_MS else 0),
        label = "footlights",
    )
    // While sound plays, a light runs along the footlights and the glow over them breathes with
    // it. It only runs while playing, and is read in the draw phase, so it never recomposes.
    val chase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, animationsEnabled) {
        chase.snapTo(0f)
        if (isPlaying && animationsEnabled) {
            while (true) {
                chase.animateTo(1f, tween(CHASE_MS, easing = LinearEasing))
                chase.snapTo(0f)
            }
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, accent)
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
                // When open, the cover runs from the fringe to the bottom edge, square where the
                // stage is wide enough (cropped a little where it isn't), and each half gathers into
                // the space beside it without covering it.
                val openingHeight = maxHeight - PelmetHeight
                val posterWidth = min(maxWidth * (1f - 2 * MIN_SIDE_FRACTION), openingHeight)
                val gathered = ((maxWidth - posterWidth) / 2) / (maxWidth * HALF_FRACTION)
                if (reveal != null) {
                    Poster(
                        reveal = reveal,
                        onLoaded = { posterLoaded = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(posterWidth, openingHeight),
                    )
                }
                // Each half is its own layer, so a sway or the parting only moves cached drawings.
                CurtainHalf(left = true, colors = colors, swayPx = { swayOffset(sway.value) }, gathered = gathered, openness = { openness.value })
                CurtainHalf(left = false, colors = colors, swayPx = { swayOffset(sway.value) }, gathered = gathered, openness = { openness.value })
                // Stage lighting; it lifts as the curtain opens, so the cover shows in its own colours.
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - openness.value * 0.8f }
                        .drawBehindKeyed(Unit) { drawStageShade() },
                )
                Valance(colors)
                // Lettered on the pelmet, like a cinema's marquee board.
                Crossfade(
                    targetState = marquee,
                    animationSpec = tween(if (animationsEnabled) MARQUEE_FADE_MS else 0),
                    modifier = Modifier.align(Alignment.TopCenter).height(ValanceHeight),
                    label = "marquee",
                ) { text ->
                    Text(
                        text = text.uppercase(),
                        style = MukhdaTheme.typography.caption.copy(letterSpacing = MukhdaTheme.typography.caption.letterSpacing * 2),
                        color = colors.marigold,
                        maxLines = 1,
                        modifier = Modifier.fillMaxHeight().wrapContentHeight(Alignment.CenterVertically),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.42f)
                        .graphicsLayer { alpha = glow * (0.82f + 0.18f * sin(chase.value * 2 * PI.toFloat() * 2)) }
                        .drawBehindKeyed(colors.marigold) {
                            drawRect(Brush.verticalGradient(listOf(colors.marigold.copy(alpha = 0f), colors.marigold.copy(alpha = 0.55f))))
                        },
                )
                Footlights(
                    colors = colors,
                    lit = { glow },
                    chase = { chase.value },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FootlightsHeight)
                        // Once the poster is up they'd dot the picture: they go with the curtain
                        // and come back only while the song plays.
                        .graphicsLayer { alpha = maxOf(1f - openness.value, glow) },
                )
                if (!revealed || openness.value < 1f) {
                    Box(Modifier.graphicsLayer { alpha = 1f - openness.value }) {
                        PlayButton(isPlaying, animationsEnabled, size = buttonSize, onClick = onPlayClick, enabled = !revealed)
                    }
                }
            }
        }
        FrameCorners(accent)
    }
}

private fun Density.swayOffset(progress: Float): Float = progress * SwayDistance.toPx()

/**
 * A row of bulbs along the foot of the stage: dim while it's quiet, lit while sound plays
 * ([lit] 0..1), with a light running along them ([chase], one lap per 0..1). Read only in the
 * draw phase.
 */
@Composable
private fun Footlights(colors: MukhdaColors, lit: () -> Float, chase: () -> Float, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val spacing = BulbSpacing.toPx()
            val radius = BulbRadius.toPx()
            val count = (size.width / spacing).toInt().coerceAtLeast(1)
            val first = (size.width - (count - 1) * spacing) / 2
            val y = size.height - radius * 2f
            val on = lit()
            val lap = chase()
            for (i in 0 until count) {
                // Each bulb is brightest as the running light passes it, about two bulbs wide.
                val wave = (sin((lap * count - i) / count * 2 * PI.toFloat() * CHASE_WAVES) + 1f) / 2f
                val brightness = on * (0.65f + 0.35f * wave)
                val center = Offset(first + i * spacing, y)
                if (brightness > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(colors.marigold.copy(alpha = 0.55f * brightness), colors.marigold.copy(alpha = 0f)),
                            center = center,
                            radius = radius * 4.5f,
                        ),
                        radius = radius * 4.5f,
                        center = center,
                    )
                }
                drawCircle(lerp(colors.velvetLight, colors.marigold, 0.25f + 0.75f * brightness), radius, center)
            }
        },
    )
}

/** The soundtrack's cover, filling [modifier]'s space. Falls back to the title on velvet. */
@Composable
private fun Poster(reveal: RevealUi, onLoaded: () -> Unit, modifier: Modifier) {
    val colors = MukhdaTheme.colors
    Box(modifier = modifier.background(colors.velvetDeep), contentAlignment = Alignment.Center) {
        Text(
            text = reveal.title,
            style = MukhdaTheme.typography.wordmark.copy(fontSize = MukhdaTheme.typography.numeral.fontSize * 1.4f),
            color = colors.marigold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
        if (reveal.artworkUrl != null) {
            AsyncImage(
                model = reveal.artworkUrl,
                contentDescription = stringResource(R.string.cd_poster, reveal.title),
                contentScale = ContentScale.Crop,
                onSuccess = { onLoaded() },
                onError = { onLoaded() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun BoxScope.CurtainHalf(
    left: Boolean,
    colors: MukhdaColors,
    swayPx: Density.() -> Float,
    /** Width, as a fraction of its closed width, once fully gathered at the side. */
    gathered: Float,
    openness: () -> Float,
) {
    Box(
        modifier = Modifier
            .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(HALF_FRACTION)
            .graphicsLayer {
                // Gathering squeezes the folds towards the outer edge, like real drapes.
                transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
                scaleX = 1f - (1f - gathered) * openness()
                translationX = if (left) -swayPx() else swayPx()
            }
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

/** Diamonds on the frame's four corners. */
@Composable
private fun BoxScope.FrameCorners(color: Color) {
    val corner = Modifier.size(8.dp).rotate(45f).background(color)
    Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).then(corner))
    Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).then(corner))
    Box(Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp).then(corner))
    Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).then(corner))
}

/** Red disc ringed in marigold and ink; the glyph is marigold. Shrinks a little while pressed. */
@Composable
private fun PlayButton(isPlaying: Boolean, animationsEnabled: Boolean, size: Dp, onClick: () -> Unit, enabled: Boolean = true) {
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
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
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
