package com.mukhda.app.ui.game

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.Verdict
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the ticket shows. No song or film: the ticket is made to be shared. */
data class TicketUi(
    val showNo: Int,
    /** "MON 19 OCT". */
    val dateLabel: String,
    val tier: Tier,
    val verdict: Verdict,
    val solvedAtStep: Int?,
    val slots: List<TrySlotUi>,
    val streak: Int,
)

/**
 * The ticket over the game screen, with Share. Shared as the image of exactly this ticket, plus a
 * line of text with the result and the coloured squares.
 */
@Composable
fun TicketOverlay(ticket: TicketUi, round: RoundUi, onAction: (GameAction) -> Unit) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val shareText = shareText(ticket, round)
    BackHandler { onAction(GameAction.OnTicketClose) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Flat paper, the same as the shared image's, so the two read as one sheet.
            .background(colors.paper)
            // Swallows taps, so nothing on the game screen underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        CloseButton(onClick = { onAction(GameAction.OnTicketClose) }, modifier = Modifier.align(Alignment.TopEnd))
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.ticket_heading).uppercase(), style = type.label, color = colors.muted)
            Spacer(Modifier.weight(1f))
            // Recorded into [layer] as it draws, so Share sends exactly this, on its own paper.
            Box(
                modifier = Modifier
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    .background(colors.paper)
                    .padding(24.dp),
            ) {
                Ticket(ticket, modifier = Modifier.rotate(TICKET_TILT))
            }
            Spacer(Modifier.weight(1f))
            BarButton(
                text = stringResource(R.string.share),
                enabled = true,
                filled = true,
                onClick = {
                    scope.launch {
                        val image = layer.toImageBitmap().asAndroidBitmap()
                        shareTicket(context, image, shareText)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.ticket_no_spoilers).uppercase(),
                style = type.caption,
                color = colors.faint,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = MukhdaTheme.colors.ink
    val label = stringResource(R.string.cd_close)
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

/** An old cinema ticket: ADMIT ONE band, show and date, the tier as the screen, the verdict, the tries. */
@Composable
fun Ticket(ticket: TicketUi, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Column(
        modifier = modifier
            .width(TICKET_WIDTH)
            .shadow(12.dp, RectangleShape, ambientColor = colors.ink, spotColor = colors.ink)
            .background(colors.ticketPaper),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.red)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.wordmark), style = type.wordmark.copy(fontSize = 30.sp), color = colors.marigold)
            Text(
                text = stringResource(R.string.admit_one).uppercase(),
                style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.marigold,
                modifier = Modifier.border(1.5.dp, colors.marigold).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }

        Box(Modifier.padding(10.dp).border(1.5.dp, colors.ink).padding(3.dp)) {
            Column(
                modifier = Modifier.border(1.dp, colors.ink).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    TicketField(R.string.ticket_show, stringResource(R.string.ticket_show_no, ticket.showNo), Modifier.weight(1f))
                    TicketField(R.string.ticket_date, ticket.dateLabel.substringAfter(' '), Modifier.weight(1f))
                    TicketField(
                        label = R.string.ticket_screen,
                        value = stringResource(ticket.tier.labelRes).uppercase(),
                        modifier = Modifier.weight(1f),
                        valueColor = ticket.tier.color(),
                        alignEnd = true,
                    )
                }
                DiamondRule(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                Text(
                    text = stringResource(R.string.todays_performance).uppercase(),
                    style = type.caption.copy(letterSpacing = type.label.letterSpacing),
                    color = colors.muted,
                )
                BasicText(
                    text = verdictHeadline(ticket.verdict),
                    style = type.verdict.copy(color = colors.red),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 30.sp, maxFontSize = 62.sp),
                )
                Text(
                    text = ticket.solvedAtStep
                        ?.let { stringResource(R.string.ticket_on, stringResource(solvedClipRes(it))).uppercase() }
                        ?: stringResource(R.string.share_out_of_tries).uppercase(),
                    style = type.caption.copy(letterSpacing = type.label.letterSpacing),
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                )
                TrySquares(ticket.slots, size = 26.dp, gap = 6.dp, modifier = Modifier.padding(top = 16.dp))
                if (ticket.streak > 0) {
                    Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(colors.ink))
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Text(
                            text = stringResource(R.string.ticket_streak, ticket.streak).uppercase(),
                            style = type.label.copy(fontSize = 22.sp, letterSpacing = 0.sp),
                            color = colors.ink,
                        )
                        Text(stringResource(R.string.ticket_streak_label).uppercase(), style = type.caption, color = colors.muted)
                    }
                }
            }
        }

        Perforation()

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Barcode(seed = ticket.showNo * 10 + ticket.tier.ordinal)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.ticket_tagline).uppercase(),
                    style = type.caption.copy(fontSize = 9.sp, lineHeight = 14.sp),
                    color = colors.muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(170.dp),
                )
                Text(
                    text = stringResource(R.string.ticket_get_it).uppercase(),
                    style = type.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.red,
                )
            }
        }
    }
}

@Composable
private fun TicketField(
    label: Int,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MukhdaTheme.colors.ink,
    alignEnd: Boolean = false,
) {
    val type = MukhdaTheme.typography
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(stringResource(label).uppercase(), style = type.caption.copy(fontSize = 9.sp), color = MukhdaTheme.colors.muted)
        Text(value, style = type.label.copy(fontSize = 17.sp, letterSpacing = 1.sp), color = valueColor)
    }
}

/** A full-width ink rule broken by a small red diamond. */
@Composable
private fun DiamondRule(modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.ink))
        Box(Modifier.size(6.dp).rotate(45f).background(colors.red))
        Box(Modifier.weight(1f).height(1.dp).background(colors.ink))
    }
}

/** The tear line: notches cut from both edges and a dashed line between. */
@Composable
private fun Perforation() {
    val colors = MukhdaTheme.colors
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val r = size.height / 2
        drawCircle(colors.paper, radius = r, center = Offset(0f, r))
        drawCircle(colors.paper, radius = r, center = Offset(size.width, r))
        drawLine(
            colors.dashed,
            Offset(r + 6.dp.toPx(), r),
            Offset(size.width - r - 6.dp.toPx(), r),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/** A decorative barcode; the same show and tier always draw the same bars. */
@Composable
private fun Barcode(seed: Int) {
    val ink = MukhdaTheme.colors.ink
    val widths = remember(seed) { Random(seed).let { r -> List(BARS) { 1 + r.nextInt(4) } } }
    Canvas(Modifier.size(width = 104.dp, height = 40.dp)) {
        val unit = size.width / widths.sumOf { it + 1 }
        var x = 0f
        widths.forEach { w ->
            drawRect(ink, topLeft = Offset(x, 0f), size = Size(w * unit, size.height))
            x += (w + 1) * unit
        }
    }
}

/** "Mukhda · Show No. 1 · Easy · HIT (1 sec)", the squares, and the Play Store link. */
@Composable
private fun shareText(ticket: TicketUi, round: RoundUi): String {
    val squares = ticket.slots.joinToString("") { slot ->
        when (slot) {
            is TrySlotUi.Empty, is TrySlotUi.Sending -> "⬜"
            is TrySlotUi.Used -> when (slot.stamp) {
                Stamp.WRONG -> "🟥"
                Stamp.RIGHT_FILM -> "🟨"
                Stamp.CORRECT -> "🟩"
                Stamp.SKIPPED -> "⬛"
            }
        }
    }
    return stringResource(
        R.string.share_text,
        ticket.showNo,
        stringResource(ticket.tier.labelRes),
        stringResource(ticket.verdict.nameRes).uppercase(),
        stringResource(shareClipRes(round)),
        squares,
        stringResource(R.string.play_store_url),
    )
}

/** Saves the ticket to the app's cache and opens the share sheet with it and the text. */
private suspend fun shareTicket(context: Context, image: Bitmap, text: String) {
    val file = withContext(Dispatchers.IO) {
        File(context.cacheDir, SHARE_DIR).apply { mkdirs() }.resolve(SHARE_FILE).also { file ->
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

private val TICKET_WIDTH = 318.dp
private const val TICKET_TILT = -1.4f
private const val BARS = 26
private const val SHARE_DIR = "shared"
private const val SHARE_FILE = "mukhda-ticket.png"
