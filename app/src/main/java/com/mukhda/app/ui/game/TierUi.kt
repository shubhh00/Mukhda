package com.mukhda.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.TierColors
import com.mukhda.domain.model.Tier

@Composable
@ReadOnlyComposable
internal fun Tier.colors(): TierColors = with(MukhdaTheme.colors) {
    when (this@colors) {
        Tier.EASY -> tierEasy
        Tier.MEDIUM -> tierMedium
        Tier.HARD -> tierHard
        Tier.EXPERT -> tierExpert
        Tier.LEGEND -> tierLegend
    }
}

/**
 * Colours for the play button and current clip frame. Same as the tier's, except Legend: its
 * maroon would vanish against the dark vinyl and film strip, so it's inverted to saffron.
 */
@Composable
@ReadOnlyComposable
internal fun Tier.accentColors(): TierColors =
    if (this == Tier.LEGEND) with(MukhdaTheme.colors) { TierColors(saffron, maroon) } else colors()

@get:StringRes
internal val Tier.labelRes: Int
    get() = when (this) {
        Tier.EASY -> R.string.tier_easy
        Tier.MEDIUM -> R.string.tier_medium
        Tier.HARD -> R.string.tier_hard
        Tier.EXPERT -> R.string.tier_expert
        Tier.LEGEND -> R.string.tier_legend
    }
