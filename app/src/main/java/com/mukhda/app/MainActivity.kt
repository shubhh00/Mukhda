package com.mukhda.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mukhda.app.ui.game.GameRoute
import com.mukhda.app.ui.game.gameGraph
import com.mukhda.app.ui.theme.MukhdaColors
import com.mukhda.app.ui.theme.MukhdaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The poster-wall look is light-only, so the bars always use dark icons, whatever the system theme.
        val paper = MukhdaColors().paper.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(paper, paper),
        )
        setContent {
            MukhdaTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = GameRoute) {
                    gameGraph()
                }
            }
        }
    }
}
