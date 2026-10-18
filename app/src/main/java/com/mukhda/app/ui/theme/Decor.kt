package com.mukhda.app.ui.theme

import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.random.Random

/** A dashed outline, for empty try slots. */
fun Modifier.dashedBorder(color: Color, width: Dp = 1.dp, dash: Dp = 5.dp): Modifier =
    drawBehindKeyed(Triple(color, width, dash)) {
        val stroke = width.toPx()
        drawRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = size.copy(width = size.width - stroke, height = size.height - stroke),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx()))),
        )
    }

/**
 * Like drawBehind, but the drawing is redone only when [key] changes. A plain drawBehind gets a new
 * lambda on every recomposition of its caller, which Compose treats as a change and redraws the
 * whole layer. [key] must hold everything [onDraw] captures; state read inside [onDraw] still
 * triggers a redraw on its own.
 */
fun Modifier.drawBehindKeyed(key: Any, onDraw: DrawScope.() -> Unit): Modifier = this then KeyedDrawElement(key, onDraw)

private class KeyedDrawElement(val key: Any, val onDraw: DrawScope.() -> Unit) : ModifierNodeElement<KeyedDrawNode>() {
    override fun create() = KeyedDrawNode(onDraw)
    override fun update(node: KeyedDrawNode) {
        node.onDraw = onDraw
    }
    override fun equals(other: Any?) = other is KeyedDrawElement && other.key == key
    override fun hashCode() = key.hashCode()
}

private class KeyedDrawNode(var onDraw: DrawScope.() -> Unit) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        onDraw()
        drawContent()
    }
}

/**
 * Newsprint: the paper colour, lighter in the upper middle and darker at the edges, with a fine
 * grain and, optionally, a halftone dot screen in [dots]. The grain and dots are small tiles
 * repeated by the GPU; tiles and the vignette are built once per colour and size, not per frame.
 */
fun Modifier.paper(colors: MukhdaColors, dots: Color? = null): Modifier =
    this then PaperElement(colors.paperGlow, colors.paper, colors.paperEdge, dots)

private data class PaperElement(
    val glow: Color,
    val base: Color,
    val edge: Color,
    val dots: Color?,
) : ModifierNodeElement<PaperNode>() {
    override fun create() = PaperNode(glow, base, edge, dots)
    override fun update(node: PaperNode) {
        node.glow = glow
        node.base = base
        node.edge = edge
        node.dots = dots
        node.vignette = null
        node.halftone = null
    }
}

private class PaperNode(var glow: Color, var base: Color, var edge: Color, var dots: Color?) :
    Modifier.Node(), DrawModifierNode {
    var vignette: Brush? = null
    var halftone: ShaderBrush? = null
    private var vignetteSize = Size.Zero
    private var grain: ShaderBrush? = null

    override fun ContentDrawScope.draw() {
        if (vignette == null || vignetteSize != size) {
            vignetteSize = size
            vignette = Brush.radialGradient(
                0f to glow,
                0.55f to base,
                1f to edge,
                center = Offset(size.width / 2, size.height * 0.35f),
                radius = max(size.width, size.height) * 0.8f,
            )
        }
        drawRect(vignette!!)
        drawRect(grain ?: grainBrush().also { grain = it })
        dots?.let { drawRect(halftone ?: halftoneBrush(it).also { brush -> halftone = brush }) }
        drawContent()
    }
}

/** Brown specks at a few percent alpha, fixed seed so the paper looks the same every launch. */
private fun grainBrush(): ShaderBrush {
    val side = 128
    val random = Random(57)
    val speck = Color(0xFF735433).toArgb() and 0x00FFFFFF
    val pixels = IntArray(side * side) {
        val alpha = if (random.nextInt(9) == 0) random.nextInt(18, 40) else random.nextInt(0, 12)
        (alpha shl 24) or speck
    }
    val tile = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888).asImageBitmap()
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/** One dot per 9 px tile, as on a cheap offset print. */
private fun halftoneBrush(color: Color): ShaderBrush {
    val grid = 9
    val tile = ImageBitmap(grid, grid)
    Canvas(tile).drawCircle(
        center = Offset(grid / 2f, grid / 2f),
        radius = 1.1f,
        paint = Paint().apply { this.color = color },
    )
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

/** False when the player has turned animations off in system settings; all motion stops then. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
