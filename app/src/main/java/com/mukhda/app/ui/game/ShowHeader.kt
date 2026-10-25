package com.mukhda.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme

/** Wordmark, show line and streak. */
@Composable
fun ShowHeader(
    showNo: Int,
    dateLabel: String,
    streak: Int,
    /** Any try on any day: a player who has never played sees "FIRST SHOW" rather than "0 DAYS". */
    hasPlayed: Boolean,
    onStreakClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            Text(stringResource(R.string.wordmark), style = type.brand, color = colors.red)
            // Once the show is in: a new player gets "FIRST SHOW", so the corner isn't empty.
            if (showNo > 0) StreakBadge(streak, hasPlayed, onClick = onStreakClick)
        }
        Text(
            text = if (showNo > 0) stringResource(R.string.show_line, showNo, dateLabel) else "",
            style = type.caption.copy(fontSize = 11.sp, letterSpacing = MukhdaTheme.typography.label.letterSpacing),
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * "12 DAYS HOUSEFULL" in marigold on the buttons' red, with a ticket stub; "FIRST SHOW" before a
 * player's first try. Opens the calendar.
 */
@Composable
private fun StreakBadge(streak: Int, hasPlayed: Boolean, onClick: () -> Unit) {
    val colors = MukhdaTheme.colors
    val openLabel = stringResource(R.string.cd_open_housefull)
    Row(
        modifier = Modifier
            .background(colors.buttonRed)
            .clickable(onClickLabel = openLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TicketIcon()
        val label = if (hasPlayed) pluralStringResource(R.plurals.days_housefull, streak, streak) else stringResource(R.string.first_show)
        Text(label.uppercase(), style = MukhdaTheme.typography.label, color = colors.marigold)
    }
}

/** A ticket outline with a dashed tear line. */
@Composable
internal fun TicketIcon(iconSize: Dp = 14.dp) {
    val color = MukhdaTheme.colors.marigold
    Canvas(Modifier.size(iconSize)) {
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
