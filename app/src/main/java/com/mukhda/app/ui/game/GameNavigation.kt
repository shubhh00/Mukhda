package com.mukhda.app.ui.game

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object GameRoute

fun NavGraphBuilder.gameGraph() {
    composable<GameRoute> {
        GameRoot()
    }
}
