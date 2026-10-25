package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.dashedBorder
import com.mukhda.app.ui.theme.drawBehindKeyed

/** Every slot is the same height, so the stage above doesn't shrink as tries fill in. */
private val SlotHeight = 40.dp
/** The numeral's stub on the left of each slot, ruled off from the rest like a ticket's. */
private val StubWidth = 40.dp

/** Five try slots, numbered I–V: used ones show the guess and a stamp; unused ones are dashed. */
@Composable
fun TrySlots(slots: List<TrySlotUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        slots.forEach { slot ->
            when (slot) {
                is TrySlotUi.Used -> UsedSlot(slot)
                is TrySlotUi.Empty -> EmptySlot(slot.number)
                is TrySlotUi.Sending -> SendingSlot(slot)
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
            .stubLine(colors.rule, dashed = false)
            .padding(end = 12.dp, top = 3.dp, bottom = 3.dp),
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

/** A try waiting for the connection: what was tried, and a spinner where the stamp will go. */
@Composable
private fun SendingSlot(slot: TrySlotUi.Sending) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SlotHeight)
            .background(colors.card)
            .dashedBorder(colors.rule)
            .stubLine(colors.rule, dashed = true)
            .padding(end = 12.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Numeral(slot.number, used = true)
        Column(modifier = Modifier.weight(1f)) {
            // A skip has no song to name: just "Skipping…".
            Text(
                text = slot.title ?: stringResource(R.string.slot_skipping),
                style = type.bodyStrong.copy(fontSize = type.bodySmall.fontSize * 1.17f),
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (slot.title != null) {
                Text(stringResource(R.string.slot_sending), style = type.bodySmall, color = colors.muted, maxLines = 1)
            }
        }
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.muted, strokeWidth = 2.dp)
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
        Stamp.CORRECT -> R.string.stamp_correct to colors.win
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
            .stubLine(MukhdaTheme.colors.dashed, dashed = true),
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
        textAlign = TextAlign.Center,
        modifier = Modifier.width(StubWidth),
    )
}

/** The rule between the numeral's stub and the rest of the slot, full height. */
private fun Modifier.stubLine(color: Color, dashed: Boolean): Modifier =
    drawBehindKeyed(color to dashed) {
        val x = StubWidth.toPx()
        val dash = 5.dp.toPx()
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(dash, dash)) else null,
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
