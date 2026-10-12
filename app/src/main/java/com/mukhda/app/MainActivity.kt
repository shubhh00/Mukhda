package com.mukhda.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mukhda.app.ui.game.GameRoute
import com.mukhda.app.ui.game.gameGraph
import com.mukhda.app.ui.theme.MukhdaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
