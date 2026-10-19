package com.mukhda.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.model.Verdict
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val BURST_MS = 1_900

/** One pop of a burst: when it goes off (a fraction of the burst), how many petals, how hard. */
private class Pop(val at: Float, val petals: Int, val strength: Float)

/**
 * The bigger the verdict, the bigger the burst: a Blockbuster pops three times, a Flop (got it,
 * on the whole preview) gets a small puff.
 */
private fun Verdict.pops(): List<Pop> = when (this) {
    Verdict.BLOCKBUSTER -> listOf(Pop(0f, 64, 1.15f), Pop(0.08f, 36, 0.9f), Pop(0.17f, 26, 0.75f))
    Verdict.SUPERHIT -> listOf(Pop(0f, 56, 1f), Pop(0.09f, 26, 0.75f))
    Verdict.HIT -> listOf(Pop(0f, 44, 0.85f), Pop(0.09f, 16, 0.6f))
    Verdict.AVERAGE -> listOf(Pop(0f, 30, 0.7f))
    Verdict.FLOP -> listOf(Pop(0f, 16, 0.55f))
    Verdict.DISASTER -> emptyList()
}
/** How fast the air slows a petal, per second: the fan-out is quick, then it hangs. */
private const val DRAG = 3.2f
/** Fall, in screen heights per second squared, once the pop has spent itself. */
private const val GRAVITY = 0.55f

/** One petal's flight, fixed for the burst. */
private class Piece(
    /** When it leaves, as a fraction of the burst. */
    val start: Float,
    val angle: Float,
    /** Launch speed, in screen widths per second. */
    val speed: Float,
    val spin: Float,
    val tilt: Float,
    val size: Float,
    val round: Boolean,
    val color: Color,
)

/**
 * A pop of marigold and rose petals for a correct guess: they burst out of [origin] (the middle
 * of the stage), fan out fast, slow, drift down and fade, as big as the [verdict] ([pops]).
 * Each [trigger] increase starts one burst; 0 shows nothing. Drawn only, never laid out: the
 * frame reads the progress in the draw phase, so nothing recomposes while it plays. With
 * animations off there's no burst.
 */
@Composable
fun PetalBurst(trigger: Int, verdict: Verdict, origin: () -> Offset, animationsEnabled: Boolean, modifier: Modifier = Modifier) {
    if (trigger == 0 || !animationsEnabled) return
    val colors = MukhdaTheme.colors
    val palette = remember(colors) { listOf(colors.marigold, colors.marigoldDeep, colors.marigold, colors.red, colors.tierExpert) }
    val pieces = remember(trigger, verdict, palette) {
        val random = Random(trigger)
        fun piece(start: Float, strength: Float) = Piece(
            start = start + random.nextFloat() * 0.02f,
            angle = random.nextFloat() * 2 * PI.toFloat(),
            speed = strength * (0.9f + random.nextFloat() * 1.3f),
            spin = (random.nextFloat() - 0.5f) * 1_100f,
            tilt = random.nextFloat() * 360f,
            size = 0.7f + random.nextFloat() * 0.7f,
            round = random.nextInt(5) == 0,
            color = palette[random.nextInt(palette.size)],
        )
        verdict.pops().flatMap { pop -> List(pop.petals) { piece(pop.at, pop.strength) } }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) { progress.animateTo(1f, tween(BURST_MS, easing = LinearEasing)) }

    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val from = origin().takeIf { it.isSpecified() } ?: Offset(size.width / 2, size.height / 3)
        val length = 13.dp.toPx()
        pieces.forEach { drawPiece(it, t, from, length) }
    }
}

private fun Offset.isSpecified() = x.isFinite() && y.isFinite()

private fun DrawScope.drawPiece(piece: Piece, t: Float, origin: Offset, length: Float) {
    val age = (t - piece.start) * BURST_MS / 1_000f
    if (age <= 0f) return
    // Out along its angle, slowed by the air: distance = v (1 - e^(-k t)) / k; then a gentle fall.
    val travel = piece.speed * size.width * (1 - exp(-DRAG * age)) / DRAG
    val x = origin.x + cos(piece.angle) * travel
    val y = origin.y + sin(piece.angle) * travel + 0.5f * GRAVITY * size.height * age * age
    // The pop: each petal swells past full size in its first tenth of a second, then settles.
    val pop = when {
        age < 0.08f -> age / 0.08f * 1.25f
        age < 0.16f -> 1.25f - (age - 0.08f) / 0.08f * 0.25f
        else -> 1f
    }
    val alpha = if (t > 0.65f) ((1f - t) / 0.35f).coerceIn(0f, 1f) else 1f
    val w = length * piece.size
    translate(x, y) {
        scale(pop, pivot = Offset.Zero) {
            if (piece.round) {
                drawCircle(piece.color.copy(alpha = alpha), radius = w / 4, center = Offset.Zero)
            } else {
                rotate(piece.tilt + age * piece.spin, pivot = Offset.Zero) {
                    // Seen edge-on as it tumbles, a petal looks thinner.
                    val turn = 0.45f + 0.55f * abs(sin(age * 9f + piece.tilt))
                    drawOval(piece.color.copy(alpha = alpha), topLeft = Offset(-w / 2, -w * turn / 4), size = Size(w, w * turn / 2))
                }
            }
        }
    }
}
