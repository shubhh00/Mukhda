package com.mukhda.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mukhda.app.R

/**
 * Font roles. All three are bundled (SIL OFL, licences in assets/licenses; Yatra One's is there
 * too, for the app icon's lettering).
 */
internal object MukhdaFonts {
    val Display: FontFamily = FontFamily(Font(R.font.rozha_one))

    // Oswald ships as one variable font; each weight is a point on its weight axis.
    val Label: FontFamily = FontFamily(
        oswald(FontWeight.Normal),
        oswald(FontWeight.Medium),
        oswald(FontWeight.SemiBold),
    )
    val Body: FontFamily = FontFamily(
        Font(R.font.hind_regular, FontWeight.Normal),
        Font(R.font.hind_medium, FontWeight.Medium),
        Font(R.font.hind_semibold, FontWeight.SemiBold),
    )

    // Variation settings on a resource font are still marked experimental; minSdk 26 supports them.
    @OptIn(ExperimentalTextApi::class)
    private fun oswald(weight: FontWeight) = Font(
        resId = R.font.oswald,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

@Immutable
data class MukhdaTypography(
    val wordmark: TextStyle = TextStyle(fontFamily = MukhdaFonts.Display, fontSize = 34.sp, letterSpacing = 0.03.em),
    val verdict: TextStyle = TextStyle(fontFamily = MukhdaFonts.Display, fontSize = 40.sp),
    /** Roman numerals on the try slots. */
    val numeral: TextStyle = TextStyle(fontFamily = MukhdaFonts.Display, fontSize = 15.sp),
    /** Credit-line caps: show line, tabs, ladder, stamps. */
    val label: TextStyle = TextStyle(
        fontFamily = MukhdaFonts.Label,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.16.em,
    ),
    /** Small print: Apple Music credit, ladder steps. */
    val caption: TextStyle = TextStyle(
        fontFamily = MukhdaFonts.Label,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.16.em,
    ),
    /** Button text. */
    val button: TextStyle = TextStyle(
        fontFamily = MukhdaFonts.Label,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.22.em,
    ),
    val body: TextStyle = TextStyle(fontFamily = MukhdaFonts.Body, fontSize = 15.sp),
    val bodySmall: TextStyle = TextStyle(fontFamily = MukhdaFonts.Body, fontSize = 12.sp),
    val bodyStrong: TextStyle = TextStyle(fontFamily = MukhdaFonts.Body, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)
