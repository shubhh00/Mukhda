package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.paper
import com.mukhda.domain.telemetry.AppGate

/**
 * The whole screen while the server keeps this copy of the game closed (AGENTS.md §8), laid out
 * like the "No connection" screen: an update needed, with the way to Google Play, or the kill
 * switch, with Try again.
 */
@Composable
fun AppGateScreen(gate: AppGate, onTryAgain: () -> Unit) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val uriHandler = LocalUriHandler.current
    val playStore = stringResource(R.string.play_store_url)
    val update = gate == AppGate.UPDATE_REQUIRED
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paper(colors, dots = colors.red.copy(alpha = 0.10f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.wordmark), style = type.brand, color = colors.red)
        Text(
            text = stringResource(if (update) R.string.gate_update_title else R.string.gate_paused_title).uppercase(),
            style = type.button,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(if (update) R.string.gate_update_body else R.string.gate_paused_body),
            style = type.body,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(if (update) R.string.gate_update_action else R.string.gate_try_again).uppercase(),
            style = type.button,
            color = colors.marigold,
            modifier = Modifier
                .padding(top = 20.dp)
                .background(colors.buttonRed)
                .clickable(role = Role.Button, onClick = { if (update) uriHandler.openUri(playStore) else onTryAgain() })
                .padding(horizontal = 28.dp, vertical = 14.dp),
        )
    }
}

@Preview
@Composable
private fun UpdateRequiredPreview() {
    MukhdaTheme { AppGateScreen(AppGate.UPDATE_REQUIRED, onTryAgain = {}) }
}

@Preview
@Composable
private fun PausedPreview() {
    MukhdaTheme { AppGateScreen(AppGate.PAUSED, onTryAgain = {}) }
}
