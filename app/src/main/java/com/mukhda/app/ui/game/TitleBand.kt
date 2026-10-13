package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme

/** Title band with a slanted bottom edge: higher on the right. */
private val SlantedBottom = GenericShape { size, _ ->
    val slant = size.height * 0.16f
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - slant)
    lineTo(0f, size.height)
    close()
}

@Composable
fun TitleBand(showNo: Int, dateLabel: String, streak: Int, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.maroonBand, SlantedBottom)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.wordmark), style = type.wordmark, color = colors.saffron)
            Text(
                stringResource(R.string.wordmark_devanagari),
                style = type.devanagariDisplay,
                color = colors.paperLight,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (showNo > 0) stringResource(R.string.show_line, showNo, dateLabel) else "",
                style = type.label,
                color = colors.paperLight,
            )
            if (streak > 0) {
                Text(
                    text = stringResource(R.string.streak_badge, streak),
                    style = type.label,
                    color = colors.maroon,
                    modifier = Modifier
                        .background(colors.saffron)
                        .border(2.dp, colors.maroon)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
