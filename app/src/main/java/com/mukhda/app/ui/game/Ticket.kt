package com.mukhda.app.ui.game

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.paper
import com.mukhda.app.ui.theme.rememberAnimationsEnabled
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.Verdict
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the ticket shows: the whole day so far. No song or film: the ticket is made to be shared. */
data class TicketUi(
    val showNo: Int,
    /** "MON 19 OCT". */
    val dateLabel: String,
    /** One per tier, in tier order. */
    val rows: List<TicketRow>,
    val streak: Int,
) {
    val songsGot: Int get() = rows.count { it.isWon }

    /** The day's best "Top X%", the one worth a brag; null until the switch is on and a round has one. */
    val best: TicketRow? get() = rows.filter { it.topPercent != null }.minByOrNull { it.topPercent!! }
}

/** One tier's line on the ticket: its tries, and how it ended (null while it's playing or unseen). */
data class TicketRow(
    val tier: Tier,
    val slots: List<TrySlotUi>,
    val verdict: Verdict?,
    val isStarted: Boolean,
    val isWon: Boolean,
    /** "Top X% today" for a finished round, once that's switched on; null otherwise. */
    val topPercent: Int? = null,
)

/**
 * The ticket over the game screen, with Share. Shared as the image of the whole ticket, plus a
 * line of text per tier with the coloured squares. Tearing off the stub shares it too: dragged
 * along the tear line, or pulled down, it comes away and the share sheet opens.
 */
@Composable
fun TicketOverlay(ticket: TicketUi, onAction: (GameAction) -> Unit) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val shareText = shareText(ticket)
    val animationsEnabled = rememberAnimationsEnabled()
    val haptics = LocalHapticFeedback.current
    val tear = remember { StubTear() }
    BackHandler { onAction(GameAction.OnTicketClose) }

    // What's shared, taken as soon as the ticket is drawn and before any tearing, so a share is
    // always the whole ticket.
    var snapshot by remember(ticket) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(ticket) {
        withFrameNanos {}
        withFrameNanos {}
        snapshot = layer.toImageBitmap()
    }
    val share: suspend () -> Unit = {
        val image = (snapshot ?: layer.toImageBitmap()).asAndroidBitmap()
        shareTicket(context, image, shareText)
        onAction(GameAction.OnTicketShared)
    }
    // Torn far enough: the stub falls away, the share sheet opens, and the ticket is whole again
    // for next time.
    val finishTear: () -> Unit = {
        scope.launch {
            tear.torn.snapTo(1f)
            if (animationsEnabled) tear.fall.animateTo(1f, tween(STUB_FALL_MS, easing = FastOutLinearInEasing))
            share()
            delay(STUB_BACK_DELAY_MS)
            tear.fall.snapTo(0f)
            tear.torn.snapTo(0f)
        }
    }
    val tearGestures = Modifier.pointerInput(Unit) {
        val pullForFull = STUB_PULL.toPx()
        var start = Offset.Zero
        var ticks = 0
        // The tear starts from the end the finger moves away from, decided by its first move.
        var sideChosen = false
        detectDragGestures(
            onDragStart = {
                start = it
                ticks = 0
                // A fresh tear picks its side again; a pull straight down tears from the left.
                sideChosen = tear.torn.value > 0f
                if (!sideChosen) tear.fromRight = false
            },
            onDragEnd = {
                if (tear.torn.value >= TEAR_ENOUGH) finishTear()
                else scope.launch { tear.torn.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) }
            },
            onDragCancel = { scope.launch { tear.torn.animateTo(0f) } },
        ) { change, _ ->
            change.consume()
            if (tear.fall.value > 0f) return@detectDragGestures
            // Either along the line, from wherever the finger started and whichever way it goes,
            // or straight down.
            val dx = change.position.x - start.x
            if (!sideChosen && abs(dx) > viewConfiguration.touchSlop) {
                sideChosen = true
                tear.fromRight = dx < 0f
            }
            val along = (if (tear.fromRight) -dx else dx).coerceAtLeast(0f) / (size.width * TEAR_ALONG_SHARE)
            val down = (change.position.y - start.y) / pullForFull
            val torn = maxOf(tear.torn.value, maxOf(along, down).coerceIn(0f, 1f))
            scope.launch { tear.torn.snapTo(torn) }
            // A tick for each hole of the perforation the tear passes.
            val holes = (torn * TEAR_HOLES).toInt()
            if (holes > ticks) {
                ticks = holes
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The game's own dotted paper.
            .paper(colors, dots = colors.red.copy(alpha = 0.10f))
            // Swallows taps, so nothing on the game screen underneath reacts.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        CloseButton(onClick = { onAction(GameAction.OnTicketClose) }, modifier = Modifier.align(Alignment.TopEnd))
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 48.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ScaleToFit {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TicketHeading()
                        // Recorded into [layer] as it draws, on flat paper there and over the
                        // dotted page here.
                        Box(
                            modifier = Modifier
                                .drawWithContent {
                                    layer.record {
                                        drawRect(colors.paper)
                                        this@drawWithContent.drawContent()
                                    }
                                    drawContent()
                                }
                                .padding(18.dp),
                        ) {
                            Ticket(ticket, modifier = Modifier.rotate(TICKET_TILT), tear = tear, stubGestures = tearGestures)
                        }
                        Text(
                            text = stringResource(R.string.ticket_tear_hint).uppercase(),
                            style = type.caption.copy(fontSize = 11.sp),
                            color = colors.muted,
                        )
                    }
                }
            }
            BarButton(
                text = stringResource(R.string.share),
                enabled = true,
                filled = true,
                onClick = { scope.launch { share() } },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
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

/** How far the stub is torn off (0 to 1), and once it's off, how far it has fallen (0 to 1). */
@Stable
class StubTear {
    val torn = Animatable(0f)
    val fall = Animatable(0f)
    /** Torn from the right end (the finger went left), hinged at the left; otherwise the reverse. */
    var fromRight by mutableStateOf(false)
}

/** "YOUR TICKET FOR TODAY" as a poster's billing line: ink caps between rules and diamonds. */
@Composable
private fun TicketHeading() {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).rotate(45f).background(colors.marigoldDeep))
        Box(Modifier.width(24.dp).height(1.5.dp).background(colors.ink))
        Text(stringResource(R.string.ticket_heading).uppercase(), style = type.button, color = colors.ink)
        Box(Modifier.width(24.dp).height(1.5.dp).background(colors.ink))
        Box(Modifier.size(6.dp).rotate(45f).background(colors.marigoldDeep))
    }
}

/**
 * Lays [content] out at its own size and, where the screen is too short for it, scales it down
 * (never up) to fit, from the top centre. Touches still land where they're drawn.
 */
@Composable
private fun ScaleToFit(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
        val scale = if (constraints.hasBoundedHeight) minOf(1f, constraints.maxHeight.toFloat() / placeable.height) else 1f
        layout(placeable.width, (placeable.height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        }
    }
}

@Composable
internal fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = MukhdaTheme.colors.ink) {
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
 * The day's ticket, as a film poster: the valance with the show and date, a curtain drawn back
 * from the wordmark and how many of the day's songs the player got, then the programme, one line
 * per tier, the credits, and a stub to tear off. [tear] moves the stub as it's torn;
 * [stubGestures] goes on the tear line and stub, where the finger tears it.
 */
@Composable
fun Ticket(ticket: TicketUi, modifier: Modifier = Modifier, tear: StubTear? = null, stubGestures: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    // The body and the stub are separate pieces of card, each with its own velvet and frame, so a
    // torn-off stub takes all of itself away and leaves only the page where it was.
    Column(modifier = modifier.widthIn(max = TICKET_MAX_WIDTH).fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RectangleShape, ambientColor = colors.ink, spotColor = colors.ink)
                    .background(colors.velvetDeep)
                    .frame(colors.marigold, openBottom = true),
            ) {
                TicketValance(stringResource(R.string.show_line, ticket.showNo, ticket.dateLabel))
                Box(Modifier.fillMaxWidth()) {
                    Curtain(Modifier.matchParentSize())
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.wordmark), style = type.brand, color = colors.marigold)
                        Text(
                            text = stringResource(R.string.ticket_score, ticket.songsGot, ticket.rows.size).uppercase(),
                            style = type.verdict.copy(fontSize = 64.sp, lineHeight = 68.sp),
                            color = colors.marigold,
                        )
                        Text(
                            text = stringResource(R.string.ticket_songs_got).uppercase(),
                            style = type.label.copy(fontSize = 13.sp),
                            color = colors.ticketPaper,
                        )
                        ticket.best?.let { best ->
                            Text(
                                text = topOn(best).uppercase(),
                                style = type.label.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.ink,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .background(colors.marigoldDeep)
                                    .padding(horizontal = 10.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .background(colors.ticketPaper)
                        .border(1.5.dp, colors.marigoldDeep)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    ticket.rows.forEachIndexed { i, row ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.rule))
                        ProgrammeLine(row)
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val starring = stringResource(R.string.ticket_starring)
                    Text(
                        text = if (ticket.streak > 0) {
                            starring + " · " + pluralStringResource(R.plurals.days_housefull, ticket.streak, ticket.streak)
                        } else {
                            starring
                        }.uppercase(),
                        style = type.caption.copy(fontSize = 12.sp),
                        color = colors.ticketPaper,
                    )
                    Text(
                        text = stringResource(R.string.ticket_now_showing).uppercase(),
                        style = type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.marigold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                }
            }
            TicketCorners(colors.marigold, top = true)
        }

        // The finger tears on this still box; the stub inside it is what moves.
        Box(stubGestures) {
            Box(
                Modifier.graphicsLayer {
                    if (tear == null) return@graphicsLayer
                    val torn = tear.torn.value
                    val fall = tear.fall.value
                    // Hinged at the far end of the tear line, it peels down from the end being
                    // torn; once off, it drops away. Turned about the hinge corner so the torn end
                    // drops: anticlockwise about the right, clockwise about the left.
                    val side = if (tear.fromRight) -1f else 1f
                    transformOrigin = TransformOrigin(if (tear.fromRight) 0f else 1f, 0f)
                    rotationZ = -side * (torn * STUB_PEEL_DEGREES + fall * STUB_FALL_DEGREES)
                    translationY = torn * 6.dp.toPx() + fall * size.height * STUB_FALL_HEIGHTS
                    translationX = side * fall * size.width * 0.12f
                    alpha = 1f - fall
                },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(12.dp, RectangleShape, ambientColor = colors.ink, spotColor = colors.ink)
                        .background(colors.velvetDeep)
                        .frame(colors.marigold, openTop = true),
                ) {
                    Perforation(
                        line = colors.velvetLight,
                        torn = { tear?.torn?.value ?: 0f },
                        fromRight = { tear?.fromRight == true },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Barcode(seed = ticket.showNo, color = colors.ticketPaper)
                        Text(
                            text = stringResource(R.string.ticket_admit_one).uppercase(),
                            style = type.button,
                            color = colors.marigold,
                        )
                    }
                }
                TicketCorners(colors.marigold, top = false)
            }
        }
    }
}

/** A [width] frame in [color] on every side but the open one, where the body and stub meet. */
private fun Modifier.frame(color: Color, width: Dp = 2.dp, openTop: Boolean = false, openBottom: Boolean = false) =
    drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(color, size = Size(w, size.height))
        drawRect(color, topLeft = Offset(size.width - w, 0f), size = Size(w, size.height))
        if (!openTop) drawRect(color, size = Size(size.width, w))
        if (!openBottom) drawRect(color, topLeft = Offset(0f, size.height - w), size = Size(size.width, w))
    }

/** A tier's line in the programme: its name, its tries as squares, and how it ended. */
@Composable
private fun ProgrammeLine(row: TicketRow) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val small = type.caption.copy(fontSize = 12.sp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(row.tier.color(), CircleShape))
        Text(
            text = stringResource(row.tier.labelRes).uppercase(),
            style = small,
            color = colors.ink,
            maxLines = 1,
            modifier = Modifier.width(72.dp).padding(start = 6.dp),
        )
        // The squares keep their size; the verdict takes what's left.
        TrySquares(row.slots, size = 16.dp, gap = 3.dp)
        val verdict = row.verdict
        Text(
            text = when {
                verdict != null -> stringResource(verdict.nameRes)
                row.isStarted -> stringResource(R.string.ticket_playing)
                else -> stringResource(R.string.ticket_not_seen)
            }.uppercase(),
            style = small.copy(fontWeight = FontWeight.SemiBold),
            color = when {
                verdict != null -> if (verdict.isCelebrated) colors.win else colors.red
                row.isStarted -> colors.muted
                else -> colors.faint
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

/**
 * Velvet drapes drawn back to either side, as on the stage: each a swag of folds tied back low,
 * leaving the middle open for the headline.
 */
@Composable
private fun Curtain(modifier: Modifier = Modifier) {
    val colors = MukhdaTheme.colors
    Canvas(modifier) {
        val drape = size.width * 0.2f
        for (left in listOf(true, false)) {
            // Outer edge at the side; the inner edge sweeps in at the top and back out to the
            // side at the bottom, where it's tied. The right drape mirrors the left.
            fun x(v: Float) = if (left) v else size.width - v
            val shape = Path().apply {
                moveTo(x(0f), 0f)
                lineTo(x(drape), 0f)
                quadraticTo(x(drape * 0.3f), size.height * 0.6f, x(drape * 0.12f), size.height)
                lineTo(x(0f), size.height)
                close()
            }
            drawPath(shape, colors.velvetMid)
            // Folds: darker lines following the drape's sweep.
            for (k in 1..3) {
                val f = k / 4f
                val fold = Path().apply {
                    moveTo(x(drape * f), 0f)
                    quadraticTo(x(drape * f * 0.3f), size.height * 0.6f, x(drape * f * 0.12f), size.height)
                }
                drawPath(fold, colors.velvetDeep, style = Stroke(width = 2.dp.toPx()))
            }
            drawPath(shape, colors.velvetLight, style = Stroke(width = 1.dp.toPx()))
        }
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
private fun BoxScope.TicketCorners(color: Color, top: Boolean) {
    val corner = Modifier.size(8.dp).rotate(45f).background(color)
    if (top) {
        Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-4).dp).then(corner))
        Box(Modifier.align(Alignment.TopEnd).offset(4.dp, (-4).dp).then(corner))
    } else {
        Box(Modifier.align(Alignment.BottomStart).offset((-4).dp, 4.dp).then(corner))
        Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).then(corner))
    }
}

/**
 * The tear line: notches cut from both edges and a dashed [line] between. As it's [torn] (0 to 1,
 * read only as it draws), a jagged gap opens along it from the left, or [fromRight].
 */
@Composable
private fun Perforation(line: Color, torn: () -> Float = { 0f }, fromRight: () -> Boolean = { false }) {
    val colors = MukhdaTheme.colors
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val r = size.height / 2
        drawCircle(colors.paper, radius = r, center = Offset(0f, r))
        drawCircle(colors.paper, radius = r, center = Offset(size.width, r))
        drawLine(
            line,
            Offset(r + 6.dp.toPx(), r),
            Offset(size.width - r - 6.dp.toPx(), r),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
        val gap = size.width * torn()
        if (gap > 0f) {
            val step = 5.dp.toPx()
            val tooth = 4.dp.toPx()
            // The page shows down to a ragged edge: everything above the teeth is torn away.
            // Measured from the end being torn.
            val mirrored = fromRight()
            fun at(x: Float) = if (mirrored) size.width - x else x
            val edge = Path().apply {
                moveTo(at(0f), 0f)
                lineTo(at(0f), r)
                var x = 0f
                var up = true
                while (x < gap) {
                    x = minOf(gap, x + step)
                    lineTo(at(x), if (up) r - tooth else r + tooth)
                    up = !up
                }
                lineTo(at(gap), 0f)
                close()
            }
            drawPath(edge, colors.paper)
        }
    }
}

/** A decorative barcode in [color]; the same show always draws the same bars. */
@Composable
private fun Barcode(seed: Int, color: Color) {
    val widths = remember(seed) { Random(seed).let { r -> List(BARS) { 1 + r.nextInt(4) } } }
    Canvas(Modifier.size(width = 88.dp, height = 40.dp)) {
        val unit = size.width / widths.sumOf { it + 1 }
        var x = 0f
        widths.forEach { w ->
            drawRect(color, topLeft = Offset(x, 0f), size = Size(w * unit, size.height))
            x += (w + 1) * unit
        }
    }
}

/**
 * "Mukhda · Show No. 6 · 3 of 5", a line per tier with its squares and verdict ("–" for one not
 * seen yet), and the Play Store link.
 */
@Composable
private fun shareText(ticket: TicketUi): String {
    val lines = ticket.rows.map { row ->
        val squares = row.slots.filterIsInstance<TrySlotUi.Used>().joinToString("") { slot ->
            when (slot.stamp) {
                Stamp.WRONG -> "🟥"
                Stamp.RIGHT_FILM -> "🟨"
                Stamp.CORRECT -> "🟩"
                Stamp.SKIPPED -> "⬛"
            }
        }
        val tier = stringResource(row.tier.labelRes)
        val verdict = row.verdict
        when {
            verdict != null -> "$tier $squares ${stringResource(verdict.nameRes).uppercase()}"
            row.isStarted -> "$tier $squares"
            else -> "$tier –"
        }
    }
    return stringResource(
        R.string.share_text,
        ticket.showNo,
        ticket.songsGot,
        ticket.rows.size,
        lines.joinToString("\n"),
        stringResource(R.string.play_store_url),
        ticket.best?.let { " · " + topOn(it) }.orEmpty(),
    )
}

/** "Top 4% on Easy". */
@Composable
private fun topOn(row: TicketRow): String =
    stringResource(R.string.ticket_top_on, row.topPercent ?: 0, stringResource(row.tier.labelRes))

/** Saves the ticket to the app's cache and opens the share sheet with it and the text. */
private suspend fun shareTicket(context: Context, image: Bitmap, text: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // A new file per share: an app given yesterday's ticket keeps reading access to that file
        // while it's open, so it must never become today's. Shares older than a day are cleared.
        val now = System.currentTimeMillis()
        dir.listFiles()?.filter { now - it.lastModified() > SHARE_KEEP_MS }?.forEach { it.delete() }
        dir.resolve("mukhda-ticket-$now.png").also { file ->
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

private val TICKET_MAX_WIDTH = 380.dp
private const val TICKET_TILT = -1.4f
/** Torn this far (of the whole line) and let go, the stub comes off; less, and it springs back. */
private const val TEAR_ENOUGH = 0.7f
/** Dragging along this share of the ticket's width tears the whole line. */
private const val TEAR_ALONG_SHARE = 0.75f
/** Pulling the stub down this far tears it off. */
private val STUB_PULL = 120.dp
/** Holes in the tear line, one haptic tick each. */
private const val TEAR_HOLES = 18
private const val STUB_PEEL_DEGREES = 9f
private const val STUB_FALL_DEGREES = 12f
/** How far the stub drops once off, in its own heights: well off the bottom of the screen. */
private const val STUB_FALL_HEIGHTS = 12f
private const val STUB_FALL_MS = 650
/** The ticket is whole again behind the share sheet, for when the player comes back. */
private const val STUB_BACK_DELAY_MS = 600L
private const val BARS = 26
private const val SHARE_DIR = "shared"
/** How long a shared ticket's file is kept for the app it was shared with. */
private const val SHARE_KEEP_MS = 24 * 60 * 60 * 1000L
