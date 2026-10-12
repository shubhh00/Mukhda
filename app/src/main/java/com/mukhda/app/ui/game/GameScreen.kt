package com.mukhda.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mukhda.app.R
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.domain.model.Tier

@Composable
fun GameRoot(
    viewModel: GameViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    GameScreen(state = state, onAction = viewModel::onAction)
}

@Composable
fun GameScreen(
    state: GameState,
    onAction: (GameAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MukhdaTheme.colors.paper),
    ) {
        TitleBand()
        TierTabs(selected = state.selectedTier, onTierClick = { onAction(GameAction.OnTierClick(it)) })
    }
}

@Composable
private fun TitleBand() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MukhdaTheme.colors.maroonBand)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wordmark),
            style = MukhdaTheme.typography.wordmark,
            color = MukhdaTheme.colors.saffron,
        )
        Text(
            text = stringResource(R.string.wordmark_devanagari),
            style = MukhdaTheme.typography.devanagariDisplay,
            color = MukhdaTheme.colors.paperLight,
        )
    }
}

@Composable
private fun TierTabs(selected: Tier, onTierClick: (Tier) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Tier.entries.forEach { tier ->
            val colors = tier.colors()
            val isSelected = tier == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    // The selected tab is raised.
                    .graphicsLayer { translationY = if (isSelected) -4.dp.toPx() else 0f }
                    .background(colors.container)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onTierClick(tier) })
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tier.labelRes).uppercase(),
                    style = MukhdaTheme.typography.label,
                    color = colors.content,
                )
            }
        }
    }
}

@Preview
@Composable
private fun GameScreenPreview() {
    MukhdaTheme {
        GameScreen(state = GameState(selectedTier = Tier.HARD), onAction = {})
    }
}
