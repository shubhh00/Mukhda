package com.mukhda.app.ui.game

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object GameRoute

/**
 * [onShowReady] is called once the first show has loaded or failed, or the server has closed the
 * game, for the launch screen.
 */
fun NavGraphBuilder.gameGraph(onShowReady: () -> Unit = {}) {
    composable<GameRoute> {
        GameRoot(onShowReady = onShowReady)
    }
}
