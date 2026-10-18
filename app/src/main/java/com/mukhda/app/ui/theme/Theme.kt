package com.mukhda.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
private data class MukhdaTokens(
    val colors: MukhdaColors = MukhdaColors(),
    val typography: MukhdaTypography = MukhdaTypography(),
)

// Mukhda's design tokens. Keep this the only custom CompositionLocal in the app.
private val LocalMukhdaTokens = staticCompositionLocalOf { MukhdaTokens() }

object MukhdaTheme {
    val colors: MukhdaColors
        @Composable @ReadOnlyComposable get() = LocalMukhdaTokens.current.colors

    val typography: MukhdaTypography
        @Composable @ReadOnlyComposable get() = LocalMukhdaTokens.current.typography
}

@Composable
fun MukhdaTheme(content: @Composable () -> Unit) {
    val tokens = MukhdaTokens()
    val colors = tokens.colors
    // The poster-wall look is light-only in v1; the Material scheme keeps stock components on-palette.
    val materialColors = lightColorScheme(
        primary = colors.buttonRed,
        onPrimary = colors.marigold,
        secondary = colors.marigoldDeep,
        onSecondary = colors.ink,
        background = colors.paper,
        onBackground = colors.ink,
        surface = colors.card,
        onSurface = colors.ink,
        onSurfaceVariant = colors.muted,
        inverseSurface = colors.ink,
        inverseOnSurface = colors.paper,
        outline = colors.ink,
        error = colors.red,
    )
    CompositionLocalProvider(LocalMukhdaTokens provides tokens) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
