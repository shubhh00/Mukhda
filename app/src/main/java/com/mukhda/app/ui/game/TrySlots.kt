package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.dashedBorder

/** Five try slots: used ones show the guess and a stamp; unused ones are dashed and numbered. */
@Composable
fun TrySlots(slots: List<TrySlotUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slots.forEach { slot ->
            when (slot) {
                is TrySlotUi.Used -> UsedSlot(slot)
                is TrySlotUi.Empty -> EmptySlot(slot.number)
            }
        }
    }
}

@Composable
private fun UsedSlot(slot: TrySlotUi.Used) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val background = if (slot.stamp == Stamp.RIGHT_FILM) colors.amberBg else colors.paperLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(background)
            .border(2.dp, colors.maroon)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SlotNumber(slot.number, filled = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.title ?: stringResource(
                    if (slot.stamp == Stamp.SKIPPED) R.string.slot_skipped else R.string.slot_unknown_song
                ),
                style = type.bodyStrong,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            slot.detail?.let {
                Text(it, style = type.body, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        StampLabel(slot.stamp)
    }
}

@Composable
private fun StampLabel(stamp: Stamp) {
    val colors = MukhdaTheme.colors
    val (text, color) = when (stamp) {
        Stamp.WRONG -> R.string.stamp_wrong to colors.pink
        Stamp.RIGHT_FILM -> R.string.stamp_right_film to colors.amberInk
        Stamp.SKIPPED -> R.string.stamp_skipped to colors.muted
        Stamp.CORRECT -> R.string.stamp_correct to colors.tierEasy.container
    }
    Text(
        text = stringResource(text),
        style = MukhdaTheme.typography.label,
        color = color,
        modifier = Modifier
            .rotate(-8f)
            .border(2.dp, color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptySlot(number: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .dashedBorder(MukhdaTheme.colors.maroon.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SlotNumber(number, filled = false)
    }
}

@Composable
private fun SlotNumber(number: Int, filled: Boolean) {
    val colors = MukhdaTheme.colors
    Box(
        modifier = Modifier
            .size(26.dp)
            .background(if (filled) colors.maroon else colors.paperLight)
            .border(2.dp, colors.maroon.copy(alpha = if (filled) 1f else 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = MukhdaTheme.typography.label,
            color = if (filled) colors.saffron else colors.maroon.copy(alpha = 0.6f),
        )
    }
}
