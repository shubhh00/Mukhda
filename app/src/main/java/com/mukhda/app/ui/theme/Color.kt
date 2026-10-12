package com.mukhda.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TierColors(val container: Color, val content: Color)

/** Design tokens. Read through [MukhdaTheme.colors]. */
@Immutable
data class MukhdaColors(
    val paper: Color = Color(0xFFF3E3C3),
    val paperLight: Color = Color(0xFFFFF3DC),
    val maroon: Color = Color(0xFF4A0B18),
    val maroonBand: Color = Color(0xFF7A1027),
    val ink: Color = Color(0xFF3A0812),
    val saffron: Color = Color(0xFFF6B21A),
    val pink: Color = Color(0xFFC2185B),
    val amberBg: Color = Color(0xFFFCE3A6),
    val amberInk: Color = Color(0xFF8A5A00),
    val muted: Color = Color(0xFF6B4A3E),

    val tierEasy: TierColors = TierColors(Color(0xFF1F7A4A), Color.White),
    val tierMedium: TierColors = TierColors(Color(0xFF136F80), Color.White),
    val tierHard: TierColors = TierColors(Color(0xFFF6B21A), Color(0xFF3A0812)),
    val tierExpert: TierColors = TierColors(Color(0xFFC2185B), Color.White),
    val tierLegend: TierColors = TierColors(Color(0xFF4A0B18), Color(0xFFF6B21A)),

    // Try squares on the shared ticket.
    val ticketWrong: Color = Color(0xFFE8336B),
    val ticketRightFilm: Color = Color(0xFFF6B21A),
    val ticketCorrect: Color = Color(0xFF39B37A),
)
