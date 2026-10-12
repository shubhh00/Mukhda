package com.mukhda.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Font roles.
 * TODO: bundle Rozha One, Yatra One, Oswald and Hind (OFL) under res/font and swap these in.
 */
internal object MukhdaFonts {
    val Display: FontFamily = FontFamily.Serif      // Rozha One
    val Devanagari: FontFamily = FontFamily.Serif   // Yatra One
    val Label: FontFamily = FontFamily.SansSerif    // Oswald 500/700
    val Body: FontFamily = FontFamily.SansSerif     // Hind 400–700
}

@Immutable
data class MukhdaTypography(
    val wordmark: TextStyle = TextStyle(fontFamily = MukhdaFonts.Display, fontSize = 34.sp, letterSpacing = 0.04.em),
    val devanagariDisplay: TextStyle = TextStyle(fontFamily = MukhdaFonts.Devanagari, fontSize = 22.sp),
    val verdict: TextStyle = TextStyle(fontFamily = MukhdaFonts.Display, fontSize = 40.sp),
    val label: TextStyle = TextStyle(
        fontFamily = MukhdaFonts.Label,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.12.em,
    ),
    val body: TextStyle = TextStyle(fontFamily = MukhdaFonts.Body, fontSize = 15.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = MukhdaFonts.Body, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)
