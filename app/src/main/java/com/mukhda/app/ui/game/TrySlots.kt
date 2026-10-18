package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.dashedBorder

/** Every slot is the same height, so the stage above doesn't shrink as tries fill in. */
private val SlotHeight = 40.dp

/** Five try slots, numbered I–V: used ones show the guess and a stamp; unused ones are dashed. */
@Composable
fun TrySlots(slots: List<TrySlotUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
    val background = if (slot.stamp == Stamp.RIGHT_FILM) colors.amberBg else colors.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SlotHeight)
            .background(background)
            .border(1.dp, colors.rule)
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Numeral(slot.number, used = true)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = slot.title ?: stringResource(
                    if (slot.stamp == Stamp.SKIPPED) R.string.slot_skipped else R.string.slot_unknown_song
                ),
                style = type.bodyStrong.copy(fontSize = type.bodySmall.fontSize * 1.17f),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            slot.detail?.let {
                Text(it, style = type.bodySmall, color = colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        StampLabel(slot.stamp)
    }
}

/** A rubber stamp, slightly crooked. */
@Composable
private fun StampLabel(stamp: Stamp) {
    val colors = MukhdaTheme.colors
    val (text, color) = when (stamp) {
        Stamp.WRONG -> R.string.stamp_wrong to colors.red
        Stamp.RIGHT_FILM -> R.string.stamp_right_film to colors.amberInk
        Stamp.SKIPPED -> R.string.stamp_skipped to colors.muted
        Stamp.CORRECT -> R.string.stamp_correct to colors.teal
    }
    Text(
        text = stringResource(text),
        style = MukhdaTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .rotate(-5f)
            .border(1.5.dp, color)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun EmptySlot(number: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SlotHeight)
            .dashedBorder(MukhdaTheme.colors.dashed)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Numeral(number, used = false)
    }
}

@Composable
private fun Numeral(number: Int, used: Boolean) {
    val colors = MukhdaTheme.colors
    Text(
        text = romanNumeral(number),
        style = MukhdaTheme.typography.numeral,
        color = if (used) colors.faint else colors.dashed,
        modifier = Modifier.width(22.dp),
    )
}

private fun romanNumeral(number: Int): String = when (number) {
    1 -> "I"
    2 -> "II"
    3 -> "III"
    4 -> "IV"
    5 -> "V"
    else -> number.toString()
}
