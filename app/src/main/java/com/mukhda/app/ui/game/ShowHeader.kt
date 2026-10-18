package com.mukhda.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme

/** Wordmark, show line and streak. */
@Composable
fun ShowHeader(showNo: Int, dateLabel: String, streak: Int, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.wordmark), style = type.wordmark, color = colors.red)
            if (streak > 0) StreakBadge(streak)
        }
        Text(
            text = if (showNo > 0) stringResource(R.string.show_line, showNo, dateLabel) else "",
            style = type.caption.copy(letterSpacing = MukhdaTheme.typography.label.letterSpacing),
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** "12 HOUSEFULL" in marigold on an ink banner, with a ticket stub. */
@Composable
private fun StreakBadge(streak: Int) {
    val colors = MukhdaTheme.colors
    Row(
        modifier = Modifier
            .background(colors.ink)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TicketIcon()
        Text(stringResource(R.string.streak_badge, streak), style = MukhdaTheme.typography.label, color = colors.marigold)
    }
}

/** A ticket outline with a dashed tear line. */
@Composable
private fun TicketIcon() {
    val color = MukhdaTheme.colors.marigold
    Canvas(Modifier.size(14.dp)) {
        val s = size.width / 24f
        drawRect(color, topLeft = Offset(3 * s, 5 * s), size = Size(18 * s, 12 * s), style = Stroke(width = 1.6f * s))
        drawLine(
            color,
            Offset(9 * s, 5 * s),
            Offset(9 * s, 17 * s),
            strokeWidth = 1.6f * s,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2 * s, 2 * s)),
        )
    }
}
