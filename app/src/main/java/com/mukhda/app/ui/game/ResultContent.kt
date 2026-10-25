package com.mukhda.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.model.ClipLadder
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.Verdict
import kotlinx.coroutines.delay

@get:StringRes
internal val Verdict.nameRes: Int
    get() = when (this) {
        Verdict.BLOCKBUSTER -> R.string.verdict_blockbuster
        Verdict.SUPERHIT -> R.string.verdict_superhit
        Verdict.HIT -> R.string.verdict_hit
        Verdict.AVERAGE -> R.string.verdict_average
        Verdict.FLOP -> R.string.verdict_flop
        Verdict.DISASTER -> R.string.verdict_disaster
    }

/** The three good verdicts get an exclamation mark, like a poster's tagline. */
internal val Verdict.isCelebrated: Boolean get() = this <= Verdict.HIT

@StringRes
internal fun solvedClipRes(step: Int): Int = when (step) {
    0 -> R.string.solved_clip_0
    1 -> R.string.solved_clip_1
    2 -> R.string.solved_clip_2
    3 -> R.string.solved_clip_3
    else -> R.string.solved_clip_4
}

/** "HIT!" in caps, from the verdict's name. */
@Composable
internal fun verdictHeadline(verdict: Verdict): String =
    stringResource(verdict.nameRes).uppercase() + if (verdict.isCelebrated) "!" else ""

/**
 * One square per try, in the ticket's colours and marked: ✗ for a miss (red, or marigold for the
 * right film), ✓ for the song, – for a skip, an empty outline for a try not used.
 */
@Composable
internal fun TrySquares(slots: List<TrySlotUi>, size: Dp, gap: Dp, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        slots.forEach { slot ->
            when (slot) {
                // Only a finished round has squares, and nothing is waiting to send in one.
                is TrySlotUi.Empty, is TrySlotUi.Sending -> Box(Modifier.size(size).border(1.5.dp, colors.dashed))
                is TrySlotUi.Used -> {
                    val (fill, mark) = when (slot.stamp) {
                        Stamp.WRONG -> colors.ticketWrong to Mark.CROSS
                        Stamp.RIGHT_FILM -> colors.ticketRightFilm to Mark.CROSS
                        Stamp.CORRECT -> colors.win to Mark.TICK
                        Stamp.SKIPPED -> colors.ladderPlayed to Mark.DASH
                    }
                    val ink = if (mark == Mark.TICK) colors.marigold else colors.ticketPaper
                    Box(Modifier.size(size).background(fill)) {
                        Canvas(Modifier.fillMaxSize().padding(size * 0.26f)) { drawMark(mark, ink) }
                    }
                }
            }
        }
    }
}

private enum class Mark { CROSS, TICK, DASH }

private fun DrawScope.drawMark(mark: Mark, color: Color) {
    val stroke = (size.minDimension * 0.2f).coerceAtLeast(1.5f)
    val w = size.width
    val h = size.height
    when (mark) {
        Mark.CROSS -> {
            drawLine(color, Offset(0f, 0f), Offset(w, h), stroke, StrokeCap.Round)
            drawLine(color, Offset(w, 0f), Offset(0f, h), stroke, StrokeCap.Round)
        }
        Mark.TICK -> {
            drawLine(color, Offset(0f, h * 0.55f), Offset(w * 0.38f, h * 0.9f), stroke, StrokeCap.Round)
            drawLine(color, Offset(w * 0.38f, h * 0.9f), Offset(w, h * 0.1f), stroke, StrokeCap.Round)
        }
        Mark.DASH -> drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), stroke, StrokeCap.Round)
    }
}

/**
 * Under the open curtain, in the order a player looks for things: the song first, ways to hear
 * it, then how they did.
 */
@Composable
internal fun RevealDetails(
    round: RoundUi,
    reveal: RevealUi,
    isPlaying: Boolean,
    onAction: (GameAction) -> Unit,
    /** "Top X% today" under the stamp, once switched on. */
    showTopPercent: Boolean = false,
) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val verdict = round.verdict ?: Verdict.DISASTER
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = reveal.title,
            style = type.wordmark.copy(fontSize = 30.sp, letterSpacing = 0.sp, lineHeight = 34.sp),
            color = colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(reveal.detail, style = type.body, color = colors.muted, textAlign = TextAlign.Center)

        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(
                text = stringResource(if (isPlaying) R.string.stop_clip else R.string.play_the_song),
                color = colors.red,
                onClick = { onAction(GameAction.OnPlayClick) },
            )
            reveal.appleMusicUrl?.let { url ->
                val uriHandler = LocalUriHandler.current
                TextAction(
                    text = stringResource(R.string.listen_on_apple_music).uppercase() + " ↗",
                    color = colors.amberInk,
                    onClick = {
                        onAction(GameAction.OnAppleMusicClick)
                        uriHandler.openUri(url)
                    },
                )
            }
        }

        // How it went: the stamp in one half, the tries and the clip it was got on in the other,
        // each centred in its half.
        Row(
            modifier = Modifier
                .padding(top = 6.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(colors.card)
                .border(1.dp, colors.rule)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                VerdictStamp(verdict)
                reveal.topPercent?.takeIf { showTopPercent }?.let { top ->
                    Text(
                        text = stringResource(R.string.top_percent, top).uppercase(),
                        style = type.caption.copy(letterSpacing = type.label.letterSpacing),
                        color = colors.red,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(colors.rule))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrySquares(round.slots, size = 18.dp, gap = 4.dp)
                Text(
                    text = round.solvedAtStep
                        ?.let { stringResource(R.string.solved_on, stringResource(solvedClipRes(it))) }
                        ?: stringResource(R.string.out_of_tries),
                    style = type.bodySmall,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Today's performance" over the verdict, stamped like a box-office report. */
@Composable
private fun VerdictStamp(verdict: Verdict) {
    val colors = MukhdaTheme.colors
    val color = if (verdict.isCelebrated) colors.win else colors.red
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.todays_performance).uppercase(),
            style = MukhdaTheme.typography.caption,
            color = colors.muted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = verdictHeadline(verdict),
            style = MukhdaTheme.typography.label.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
            color = color,
            modifier = Modifier
                .rotate(-4f)
                .border(2.dp, color)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun TextAction(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MukhdaTheme.typography.label.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

/** In place of the guess bar once a round is over: share, move on, and when the next show starts. */
@Composable
fun ResultBar(nextTier: Tier?, endsAtEpochMs: Long, clockOffsetMs: Long, onAction: (GameAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BarButton(
                text = stringResource(R.string.share_ticket),
                enabled = true,
                filled = true,
                onClick = { onAction(GameAction.OnShareTicketClick) },
                modifier = Modifier.weight(1f),
            )
            if (nextTier != null) {
                BarButton(
                    text = stringResource(R.string.next_tier, stringResource(nextTier.labelRes)),
                    enabled = true,
                    filled = false,
                    onClick = { onAction(GameAction.OnNextTierClick) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Countdown(endsAtEpochMs, clockOffsetMs)
            Text("·", style = MukhdaTheme.typography.caption, color = colors.faint)
            Text(stringResource(R.string.apple_music_credit), style = MukhdaTheme.typography.caption, color = colors.faint)
        }
    }
}

/**
 * "NEXT SHOW IN 06:49:12", ticking each second. The end comes from the server; the phone's clock
 * only measures the time left, for display.
 */
@Composable
internal fun Countdown(endsAtEpochMs: Long, clockOffsetMs: Long, modifier: Modifier = Modifier) {
    val left = rememberSecondsLeft(endsAtEpochMs, clockOffsetMs)
    val time = "%02d:%02d:%02d".format(left / 3600, left / 60 % 60, left % 60)
    Text(
        text = stringResource(R.string.next_show_in, time).uppercase(),
        style = MukhdaTheme.typography.caption,
        color = MukhdaTheme.colors.faint,
        modifier = modifier,
    )
}

private const val TICK_MS = 1_000L

/**
 * Whole seconds until [endsAtEpochMs], on the server's clock (the phone's plus [clockOffsetMs]),
 * ticking each second. Never below zero.
 */
@Composable
internal fun rememberSecondsLeft(endsAtEpochMs: Long, clockOffsetMs: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() + clockOffsetMs) }
    LaunchedEffect(endsAtEpochMs, clockOffsetMs) {
        while (true) {
            now = System.currentTimeMillis() + clockOffsetMs
            delay(TICK_MS - now % TICK_MS)
        }
    }
    return ((endsAtEpochMs - now) / TICK_MS).coerceAtLeast(0)
}

/** The clip label for share text: "1 sec", or out of tries. */
@StringRes
internal fun shareClipRes(round: RoundUi): Int =
    round.solvedAtStep?.let { clipLengthRes(it.coerceIn(0, ClipLadder.LAST_STEP)) } ?: R.string.share_out_of_tries
