package com.mukhda.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mukhda.app.R

/**
 * Font roles. Rozha One and Yatra One are bundled (SIL OFL, licences in assets/licenses).
 * TODO: bundle Oswald (labels) and Hind (body) too; they use the system sans until then.
 */
internal object MukhdaFonts {
    val Display: FontFamily = FontFamily(Font(R.font.rozha_one))
    val Devanagari: FontFamily = FontFamily(Font(R.font.yatra_one))
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
