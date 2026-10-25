package com.mukhda.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the poster-wall look: poster inks on newsprint. Read through
 * [MukhdaTheme.colors].
 */
@Immutable
data class MukhdaColors(
    // Paper
    val paper: Color = Color(0xFFEFE2C4),
    /** Lighter centre of the paper and the darker edge of its vignette. */
    val paperGlow: Color = Color(0xFFF3E7CC),
    val paperEdge: Color = Color(0xFFE2D0A8),
    /** Cards: used try slots, the suggestion list. */
    val card: Color = Color(0xFFF7EDD6),
    val input: Color = Color(0xFFFFF8E6),

    // Ink
    val ink: Color = Color(0xFF1B1A17),
    val muted: Color = Color(0xFF6E5E4A),
    /** Hints, inactive tabs, small print. */
    val faint: Color = Color(0xFF8A7A66),
    val rule: Color = Color(0xFFCDB892),
    val dashed: Color = Color(0xFFBFAA84),

    // Poster inks
    val red: Color = Color(0xFFC8261E),
    /** Deeper red for filled buttons, so marigold text on it stays readable. */
    val buttonRed: Color = Color(0xFFA0190F),
    val marigold: Color = Color(0xFFF5C02A),
    val marigoldDeep: Color = Color(0xFFE09A12),
    val amberBg: Color = Color(0xFFF6E2AE),
    val amberInk: Color = Color(0xFF8A5A00),
    /** A right answer: the tick, the CORRECT and verdict stamps. The curtain's deep velvet, not another red. */
    val win: Color = Color(0xFF5A0C10),

    // Disabled button
    val disabled: Color = Color(0xFFD9C9A6),
    val onDisabled: Color = Color(0xFF7A6A56),

    // Clip ladder
    val ladderPlayed: Color = Color(0xFF8A7458),
    val ladderEmpty: Color = Color(0xFFD9C9A6),

    // Velvet curtain, darkest fold to highlight
    val velvetDeep: Color = Color(0xFF3A0608),
    val velvetDark: Color = Color(0xFF5A0C10),
    val velvetMid: Color = Color(0xFF861A1C),
    val velvetLight: Color = Color(0xFFB33A34),

    // Tiers, light to dark as they get harder, in the poster's own inks: marigold, saffron,
    // red, the curtain's maroon, deep velvet.
    val tierEasy: Color = Color(0xFFE9B11E),
    val tierMedium: Color = Color(0xFFE0731F),
    val tierHard: Color = Color(0xFFC8261E),
    val tierExpert: Color = Color(0xFF861A1C),
    val tierLegend: Color = Color(0xFF5E1016),

    /** The ticket's own paper, a shade lighter than the page. */
    val ticketPaper: Color = Color(0xFFFBF2DD),

    // Try squares on the shared ticket; a right one is [win], with a marigold tick.
    val ticketWrong: Color = Color(0xFFC8261E),
    val ticketRightFilm: Color = Color(0xFFE09A12),
)
