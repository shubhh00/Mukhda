package com.mukhda.app.ui.common

import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Hides the soft keyboard right away. Compose's SoftwareKeyboardController hides it a moment
 * later, so a text change from the same tap (picking a suggestion) can restart the keyboard first
 * and make it pop back up for a frame. Going straight to the InputMethodManager avoids that.
 */
@Composable
fun rememberHideKeyboard(): () -> Unit {
    val view = LocalView.current
    return remember(view) {
        {
            view.context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }
}
