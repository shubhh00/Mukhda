package com.mukhda.app.ui.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaColors
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.paper
import com.mukhda.app.ui.theme.rememberAnimationsEnabled
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val CellHeight = 40.dp
private val SeatSize = 34.dp
private val HeadlineTuck = (-12).dp

/**
 * The housefull calendar, from the streak badge, dressed like the stage: the ink pelmet, the
 * streak as the headline, the best run and shows seen in a framed box, and a month of show days
 * with the days played lit marigold, runs of them joined into one strip. Months go back to the
 * first show. Everything comes from the rounds kept on the phone and the server's show date.
 */
@Composable
fun HousefullSheet(
    streak: Int,
    best: Int,
    playedDays: Set<LocalDate>,
    today: LocalDate,
    showNo: Int,
    /** When today's show closes (from the server), for "Next show in". */
    endsAtEpochMs: Long,
    onClose: () -> Unit,
) {
    val colors = MukhdaTheme.colors
    BackHandler(onBack = onClose)
    val animationsEnabled = rememberAnimationsEnabled()
    val shown = remember { MutableTransitionState(!animationsEnabled).apply { targetState = true } }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = shown, enter = fadeIn()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.ink.copy(alpha = 0.55f))
                    .clickable(interactionSource = null, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            visibleState = shown,
            enter = slideInVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .paper(colors, dots = colors.red.copy(alpha = 0.10f))
                    // Taps inside the sheet don't reach the scrim.
                    .clickable(interactionSource = null, indication = null) {}
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            ) {
                Pelmet(onClose)
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Headline(streak, Modifier.fillMaxWidth().padding(top = 14.dp))
                    Figures(best, shows = playedDays.size, Modifier.padding(top = 2.dp))
                    // Show No. 1's date: the days before it had no show.
                    val firstShow = today.minusDays((showNo - 1).coerceAtLeast(0).toLong())
                    MonthCalendar(playedDays, today, firstShow, Modifier.padding(top = 14.dp))
                    Text(
                        text = stringResource(if (streak > 0) R.string.housefull_hint else R.string.housefull_start).uppercase(),
                        style = MukhdaTheme.typography.caption,
                        color = colors.faint,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                        Countdown(endsAtEpochMs)
                    }
                }
            }
        }
    }
}

/** The ink pelmet with "HOUSEFULL" in marigold over a fringe, and the close button. */
@Composable
private fun Pelmet(onClose: () -> Unit) {
    val colors = MukhdaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(colors.ink), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TicketIcon(iconSize = 16.dp)
                Text(
                    text = stringResource(R.string.housefull_title).uppercase(),
                    style = MukhdaTheme.typography.label.copy(fontSize = 14.sp),
                    color = colors.marigold,
                )
            }
            CloseButton(onClick = onClose, color = colors.marigold, modifier = Modifier.align(Alignment.CenterEnd))
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val edge = 2.dp.toPx()
            drawRect(colors.marigoldDeep, size = Size(size.width, edge))
            val tick = 2.dp.toPx()
            val gap = 9.dp.toPx()
            var x = gap / 2
            while (x < size.width) {
                drawRect(colors.marigoldDeep, topLeft = Offset(x, edge), size = Size(tick, size.height - edge))
                x += gap
            }
        }
    }
}

/** The streak, billed like a poster's big number: "12" over "DAYS HOUSEFULL". */
@Composable
private fun Headline(streak: Int, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = streak.toString(),
            // The display face's tall line box trimmed to the numeral, so the line under it sits close.
            style = MukhdaTheme.typography.verdict.copy(
                fontSize = 60.sp,
                lineHeight = 60.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            color = colors.red,
        )
        Text(
            text = pluralStringResource(R.plurals.housefull_unit, streak).uppercase(),
            style = MukhdaTheme.typography.label.copy(fontSize = 13.sp),
            color = colors.ink,
            // Up into the numeral's empty descender space, so the two read as one billing.
            modifier = Modifier.offset(y = HeadlineTuck),
        )
    }
}

/** Best run and shows seen, in a framed box like the result's "Today's performance". */
@Composable
private fun Figures(best: Int, shows: Int, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(colors.card)
            .border(1.dp, colors.rule)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Figure(stringResource(R.string.housefull_best), pluralStringResource(R.plurals.days, best, best), Modifier.weight(1f))
        Box(Modifier.fillMaxHeight().width(1.dp).background(colors.rule))
        Figure(stringResource(R.string.housefull_shows), shows.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), style = MukhdaTheme.typography.caption, color = MukhdaTheme.colors.muted)
        Text(value, style = MukhdaTheme.typography.verdict.copy(fontSize = 22.sp, lineHeight = 28.sp), color = MukhdaTheme.colors.ink)
    }
}

/**
 * One month, Monday first. Played days are marigold, and days played back to back join into one
 * strip, so a run reads as a run. Today is ringed in ink; days with no show (before the first, or
 * still to come) are faint. The arrows go back to the first show's month, and no further than
 * this one.
 */
@Composable
private fun MonthCalendar(playedDays: Set<LocalDate>, today: LocalDate, firstShow: LocalDate, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val last = YearMonth.from(today)
    val first = YearMonth.from(minOf(firstShow, today))
    var month by rememberSaveable { mutableStateOf(last) }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthArrow(back = true, enabled = month > first) { month = month.minusMonths(1) }
            Text(
                text = "${month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${month.year}".uppercase(Locale.ENGLISH),
                style = type.label.copy(fontSize = 14.sp),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            MonthArrow(back = false, enabled = month < last) { month = month.plusMonths(1) }
        }
        Row(Modifier.padding(bottom = 2.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.ENGLISH),
                    style = type.caption,
                    color = colors.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Blank cells before the 1st, so it lands under its weekday.
        val lead = month.atDay(1).dayOfWeek.value - 1
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map(month::atDay)
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { day ->
                    Box(Modifier.weight(1f).height(CellHeight), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            DayCell(
                                day = day,
                                played = day in playedDays,
                                // Joined to the day before and after when those were played too,
                                // within this week's row and this month.
                                joinsBefore = day.dayOfWeek != DayOfWeek.MONDAY && day.dayOfMonth > 1 && day.minusDays(1) in playedDays,
                                joinsAfter = day.dayOfWeek != DayOfWeek.SUNDAY && day.dayOfMonth < month.lengthOfMonth() && day.plusDays(1) in playedDays,
                                isToday = day == today,
                                noShow = day.isAfter(today) || day.isBefore(firstShow),
                                colors = colors,
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    played: Boolean,
    joinsBefore: Boolean,
    joinsAfter: Boolean,
    isToday: Boolean,
    noShow: Boolean,
    colors: MukhdaColors,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                if (!played) return@drawBehind
                val seat = SeatSize.toPx()
                val top = (size.height - seat) / 2
                // The strip to a played neighbour, under the seat.
                if (joinsBefore) drawRect(colors.marigold, Offset(0f, top), Size(size.width / 2, seat))
                if (joinsAfter) drawRect(colors.marigold, Offset(size.width / 2, top), Size(size.width / 2, seat))
                drawCircle(colors.marigold, seat / 2)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(SeatSize)
                .then(if (isToday) Modifier.border(1.5.dp, colors.ink, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = if (played || isToday) MukhdaTheme.typography.bodyStrong else MukhdaTheme.typography.body,
                color = when {
                    played || isToday -> colors.ink
                    noShow -> colors.faint.copy(alpha = 0.45f)
                    else -> colors.muted
                },
            )
        }
    }
}

/** ‹ or ›, faded when there's no month that way. */
@Composable
private fun MonthArrow(back: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = MukhdaTheme.colors.ink
    val label = stringResource(if (back) R.string.cd_previous_month else R.string.cd_next_month)
    Box(
        modifier = Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width = 8.dp, height = 14.dp)) {
            val stroke = 2.dp.toPx()
            val tip = if (back) 0f else size.width
            val tail = size.width - tip
            drawLine(color, Offset(tail, 0f), Offset(tip, size.height / 2), stroke, StrokeCap.Round)
            drawLine(color, Offset(tip, size.height / 2), Offset(tail, size.height), stroke, StrokeCap.Round)
        }
    }
}
