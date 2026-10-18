package com.mukhda.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mukhda.app.R
import com.mukhda.app.ui.common.rememberHideKeyboard
import com.mukhda.app.ui.theme.MukhdaTheme
import com.mukhda.app.ui.theme.hardShadow

/** Guess input with Skip / Guess. */
@Composable
fun GuessBar(
    guessText: String,
    roundOpen: Boolean,
    canGuess: Boolean,
    canSkip: Boolean,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MukhdaTheme.colors
    val hideKeyboard = rememberHideKeyboard()
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Its own layer: keystrokes and the blinking cursor redraw only the bar, not the screen.
            .graphicsLayer()
            .background(colors.paper)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GuessInput(guessText, roundOpen, canGuess, onAction)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BarButton(
                text = stringResource(R.string.skip),
                enabled = canSkip,
                container = colors.paperLight,
                content = colors.maroon,
                onClick = {
                    hideKeyboard()
                    onAction(GameAction.OnSkipClick)
                },
                modifier = Modifier.weight(1f),
            )
            BarButton(
                text = stringResource(R.string.guess),
                enabled = canGuess,
                container = colors.saffron,
                content = colors.ink,
                onClick = {
                    hideKeyboard()
                    onAction(GameAction.OnGuessClick)
                },
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun GuessInput(guessText: String, enabled: Boolean, canGuess: Boolean, onAction: (GameAction) -> Unit) {
    val colors = MukhdaTheme.colors
    val hideKeyboard = rememberHideKeyboard()
    // The text lives in the ViewModel; only the cursor is kept here. When the text changes from
    // outside (picking a suggestion, clearing after a guess) the cursor goes to the end.
    var field by remember { mutableStateOf(TextFieldValue(guessText, TextRange(guessText.length))) }
    if (field.text != guessText) {
        field = TextFieldValue(guessText, TextRange(guessText.length))
    }
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != guessText) onAction(GameAction.OnGuessTextChange(it.text))
        },
        enabled = enabled,
        singleLine = true,
        textStyle = MukhdaTheme.typography.body.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.pink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            hideKeyboard()
            if (canGuess) onAction(GameAction.OnGuessClick)
        }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onAction(GameAction.OnGuessFieldFocus) }
            .hardShadow(colors.maroon, 3.dp)
            .background(colors.paperLight)
            .border(2.dp, colors.maroon)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { field ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    if (guessText.isEmpty()) {
                        Text(
                            text = stringResource(if (enabled) R.string.guess_hint else R.string.guess_closed),
                            style = MukhdaTheme.typography.body,
                            color = colors.muted,
                        )
                    }
                    field()
                }
                if (enabled && guessText.isNotEmpty()) {
                    ClearButton(onClick = { onAction(GameAction.OnClearGuessClick) })
                }
            }
        },
    )
}

/** ✕ inside the guess box: clears what's typed and keeps the keyboard up. */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    val color = MukhdaTheme.colors.maroon
    val label = stringResource(R.string.cd_clear_guess)
    Box(
        modifier = Modifier
            .padding(start = 8.dp)
            .size(24.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(color, Offset.Zero, Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

/** Up to five matches for the guess box, with the replay control kept in reach while searching. */
@Composable
fun SearchDropdown(
    suggestions: List<SuggestionUi>,
    totalMatches: Int,
    isPlaying: Boolean,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hardShadow(colors.pink, 4.dp)
            .background(colors.paperLight)
            .border(2.dp, colors.maroon),
    ) {
        // Keep the clip within reach while searching.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.maroon)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (totalMatches > suggestions.size) {
                    stringResource(R.string.more_results, suggestions.size, totalMatches)
                } else {
                    stringResource(R.string.pick_a_song)
                },
                style = type.label,
                color = colors.paperLight,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(if (isPlaying) R.string.stop_clip else R.string.replay_clip),
                style = type.label,
                color = colors.saffron,
                modifier = Modifier
                    .clickable(role = Role.Button) { onAction(GameAction.OnPlayClick) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        suggestions.forEachIndexed { i, suggestion ->
            if (i > 0) HorizontalDivider(color = colors.maroon.copy(alpha = 0.2f))
            SuggestionRow(suggestion, onClick = { onAction(GameAction.OnSuggestionClick(suggestion.song.songId)) })
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: SuggestionUi, onClick: () -> Unit) {
    val colors = MukhdaTheme.colors
    val type = MukhdaTheme.typography
    val alpha = if (suggestion.alreadyGuessed) 0.45f else 1f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !suggestion.alreadyGuessed, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            suggestion.song.title,
            style = type.bodyStrong,
            color = colors.ink.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (suggestion.alreadyGuessed) {
                "${suggestion.song.detail} · ${stringResource(R.string.already_guessed)}"
            } else {
                suggestion.song.detail
            },
            style = type.body,
            color = colors.muted.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BarButton(
    text: String,
    enabled: Boolean,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MukhdaTheme.colors
    val alpha = if (enabled) 1f else 0.4f
    Box(
        modifier = modifier
            .height(48.dp)
            .then(if (enabled) Modifier.hardShadow(colors.maroon, 3.dp) else Modifier)
            .background(container.copy(alpha = alpha))
            .border(2.dp, colors.maroon.copy(alpha = alpha))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = MukhdaTheme.typography.label, color = content.copy(alpha = alpha))
    }
}
