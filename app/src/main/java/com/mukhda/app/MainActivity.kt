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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
        // The system's launch screen is plain velvet; on the first frame it gives way to the
        // launch curtain, which then holds while the first show loads.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        styleSystemBars(onCurtain = savedInstanceState == null)
        setContent {
            MukhdaTheme {
                // Only on a fresh start: not when the screen is rebuilt (say, on rotation).
                var curtainUp by rememberSaveable { mutableStateOf(savedInstanceState == null) }
                var showReady by remember { mutableStateOf(false) }
                LaunchedEffect(curtainUp) { styleSystemBars(onCurtain = curtainUp) }
                Box {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = GameRoute) {
                        gameGraph(onShowReady = { showReady = true })
                    }
                    if (curtainUp) LaunchCurtain(ready = showReady, onOpened = { curtainUp = false })
                }
            }
        }
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
}
