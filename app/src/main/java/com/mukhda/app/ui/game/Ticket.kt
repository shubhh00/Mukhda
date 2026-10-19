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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.pluralStringResource
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

/**
 * The day's ticket, dressed like the stage: the ink valance with the show and date, a frame in
 * the tier's colour with diamond corners, the wordmark and tier, the verdict and the tries.
 */
@Composable
fun Ticket(ticket: TicketUi, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val tierColor = ticket.tier.color()
    Box(modifier = modifier.width(TICKET_WIDTH)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(12.dp, RectangleShape, ambientColor = colors.ink, spotColor = colors.ink)
                .background(colors.ticketPaper)
                .border(2.dp, tierColor),
        ) {
            TicketValance(stringResource(R.string.show_line, ticket.showNo, ticket.dateLabel))
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.wordmark), style = type.wordmark.copy(fontSize = 34.sp), color = colors.red)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).background(tierColor, CircleShape))
                    Text(stringResource(ticket.tier.labelRes).uppercase(), style = type.label, color = colors.ink)
                }
                DiamondRule(color = tierColor, modifier = Modifier.padding(top = 14.dp, bottom = 10.dp))
                Text(
                    text = stringResource(R.string.todays_performance).uppercase(),
                    style = type.caption.copy(letterSpacing = type.label.letterSpacing),
                    color = colors.muted,
                )
                BasicText(
                    text = verdictHeadline(ticket.verdict),
                    style = type.verdict.copy(color = if (ticket.verdict.isCelebrated) colors.teal else colors.red),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 30.sp, maxFontSize = 58.sp),
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
                    Text(
                        text = pluralStringResource(R.plurals.days_housefull, ticket.streak, ticket.streak).uppercase(),
                        style = type.label,
                        color = colors.ink,
                        modifier = Modifier.padding(top = 14.dp),
                    )
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
                    )
                    Text(
                        text = stringResource(R.string.ticket_get_it).uppercase(),
                        style = type.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.red,
                    )
                }
            }
        }
        TicketCorners(tierColor)
    }
}

/** The stage's pelmet across the top: ink, with the show and date in marigold over a fringe. */
@Composable
private fun TicketValance(text: String) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = type.caption.copy(letterSpacing = type.label.letterSpacing),
            color = colors.marigold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().background(colors.ink).padding(vertical = 7.dp),
        )
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

/** Diamonds on the ticket's corners, as on the stage frame. */
@Composable
private fun BoxScope.TicketCorners(color: Color) {
    val corner = Modifier.size(8.dp).rotate(45f).background(color)
    Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).then(corner))
    Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).then(corner))
    Box(Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp).then(corner))
    Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).then(corner))
}

/** A full-width ink rule broken by a small diamond in [color]. */
@Composable
private fun DiamondRule(color: Color, modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(colors.ink))
        Box(Modifier.size(6.dp).rotate(45f).background(color))
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
    Canvas(Modifier.size(width = 88.dp, height = 40.dp)) {
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
