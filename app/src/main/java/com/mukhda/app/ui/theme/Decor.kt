package com.mukhda.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Poster look's solid, unblurred drop shadow, offset down and right. */
fun Modifier.hardShadow(color: Color, offset: Dp = 4.dp, shape: Shape = RectangleShape): Modifier =
    drawBehindKeyed(Triple(color, offset, shape)) {
        val px = offset.toPx()
        translate(px, px) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
    }

/** A dashed outline, for empty try slots. */
fun Modifier.dashedBorder(color: Color, width: Dp = 2.dp, dash: Dp = 6.dp): Modifier =
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
 * Paper halftone: small pink dots on a 9 px grid at about 10% alpha. One 9 px tile with a single
 * dot is repeated by the GPU, which is far cheaper than drawing every dot. The tile is built once
 * per colour, not on every recomposition.
 */
fun Modifier.halftone(color: Color): Modifier = this then HalftoneElement(color)

private data class HalftoneElement(val color: Color) : ModifierNodeElement<HalftoneNode>() {
    override fun create() = HalftoneNode(color)
    override fun update(node: HalftoneNode) {
        node.color = color
        node.brush = null
    }
}

private class HalftoneNode(var color: Color) : Modifier.Node(), DrawModifierNode {
    var brush: ShaderBrush? = null

    override fun ContentDrawScope.draw() {
        drawRect(brush ?: halftoneBrush(color).also { brush = it })
        drawContent()
    }
}

private fun halftoneBrush(color: Color): ShaderBrush {
    val grid = 9
    val tile = ImageBitmap(grid, grid)
    Canvas(tile).drawCircle(
        center = Offset(grid / 2f, grid / 2f),
        radius = 1.1f,
        paint = Paint().apply { this.color = color.copy(alpha = 0.10f) },
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
