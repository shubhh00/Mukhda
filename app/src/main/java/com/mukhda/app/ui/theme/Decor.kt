package com.mukhda.app.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Poster look's solid, unblurred drop shadow, offset down and right. */
fun Modifier.hardShadow(color: Color, offset: Dp = 4.dp, shape: Shape = RectangleShape): Modifier =
    drawBehind {
        val px = offset.toPx()
        translate(px, px) { drawOutline(shape.createOutline(size, layoutDirection, this), color) }
    }

/** A dashed outline, for empty try slots. */
fun Modifier.dashedBorder(color: Color, width: Dp = 2.dp, dash: Dp = 6.dp): Modifier =
    drawBehind {
        val stroke = width.toPx()
        drawRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = size.copy(width = size.width - stroke, height = size.height - stroke),
            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx()))),
        )
    }

/**
 * Paper halftone: small pink dots on a 9 px grid at about 10% alpha. One 9 px tile with a single
 * dot is repeated by the GPU, which is far cheaper than drawing every dot.
 */
fun Modifier.halftone(color: Color): Modifier = drawWithCache {
    val grid = 9
    val tile = ImageBitmap(grid, grid)
    Canvas(tile).drawCircle(
        center = Offset(grid / 2f, grid / 2f),
        radius = 1.1f,
        paint = Paint().apply { this.color = color.copy(alpha = 0.10f) },
    )
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush) }
}

/** False when the player has turned animations off in system settings; all motion stops then. */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
