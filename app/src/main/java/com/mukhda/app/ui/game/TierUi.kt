package com.mukhda.app.ui.game

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.model.Tier

/** The tier's poster ink: its tab dot and underline. */
@Composable
@ReadOnlyComposable
internal fun Tier.color(): Color = with(MukhdaTheme.colors) {
    when (this@color) {
        Tier.EASY -> tierEasy
        Tier.MEDIUM -> tierMedium
        Tier.HARD -> tierHard
        Tier.EXPERT -> tierExpert
        Tier.LEGEND -> tierLegend
    }
}

@get:StringRes
internal val Tier.labelRes: Int
    get() = when (this) {
        Tier.EASY -> R.string.tier_easy
        Tier.MEDIUM -> R.string.tier_medium
        Tier.HARD -> R.string.tier_hard
        Tier.EXPERT -> R.string.tier_expert
        Tier.LEGEND -> R.string.tier_legend
    }
