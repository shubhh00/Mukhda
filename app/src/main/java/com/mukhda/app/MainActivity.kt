package com.mukhda.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mukhda.app.ui.game.GameRoute
import com.mukhda.app.ui.game.LaunchCurtain
import com.mukhda.app.ui.game.gameGraph
import com.mukhda.app.ui.theme.MukhdaColors
import com.mukhda.app.ui.theme.MukhdaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The system's launch screen is plain velvet; the launch curtain is drawn over it on the
        // first frame, and holds while the first show loads.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Whenever Android shows its launch screen: a fresh start, or a return to an app it closed
        // in the background (a new process). Never when the screen is only rebuilt, say on rotation.
        val freshStart = savedInstanceState == null || !curtainShown
        curtainShown = true
        styleSystemBars(onCurtain = freshStart)
        showNavigationBar(!freshStart)
        setContent {
            MukhdaTheme {
                // Not saveable: a restored app would bring back "already opened" from its last run.
                var curtainUp by remember { mutableStateOf(freshStart) }
                var showReady by remember { mutableStateOf(false) }
                LaunchedEffect(curtainUp) { styleSystemBars(onCurtain = curtainUp) }
                Box {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = GameRoute) {
                        gameGraph(onShowReady = { showReady = true })
                    }
                    if (curtainUp) {
                        LaunchCurtain(
                            ready = showReady,
                            // Back as the curtain starts to part, so the game settles under it.
                            onParting = { showNavigationBar(true) },
                            onOpened = { curtainUp = false },
                        )
                    }
                }
            }
        }
    }

    /** The navigation bar is hidden while the launch curtain is down; a swipe still brings it up. */
    private fun showNavigationBar(show: Boolean) {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) bars.show(WindowInsetsCompat.Type.navigationBars()) else bars.hide(WindowInsetsCompat.Type.navigationBars())
    }

    /**
     * Light icons over the launch curtain's velvet; after it, dark ones, whatever the system theme,
     * as the poster-wall look is light-only.
     */
    private fun styleSystemBars(onCurtain: Boolean) {
        if (onCurtain) {
            // auto(), always dark: light icons with no scrim, so the velvet runs behind both bars.
            val overVelvet = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { true }
            enableEdgeToEdge(statusBarStyle = overVelvet, navigationBarStyle = overVelvet)
        } else {
            val paper = MukhdaColors().paper.toArgb()
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(paper, paper),
            )
        }
    }

    private companion object {
        /** Set once the curtain has been up in this run of the app. */
        var curtainShown = false
    }
}
