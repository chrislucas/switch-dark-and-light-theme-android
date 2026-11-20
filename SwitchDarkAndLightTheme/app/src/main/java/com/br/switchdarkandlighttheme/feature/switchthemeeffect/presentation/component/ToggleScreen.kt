package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen


import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.br.switchdarkandlighttheme.feature.MultiDpiPreviews
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.AnimatedAppTheme
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.StatefulCircularRevealThemeToggle

/*
    https://share.google/aimode/AAxlgL2WkqGOz7pSb
 */


/**
 * A screen that provides a user interface to toggle between light and dark themes.
 *
 * This composable manages the [isDarkTheme] state using [rememberSaveable], allowing the
 * preference to persist across configuration changes. It utilizes the [AnimatedAppTheme]
 * wrapper to apply theme transitions to its content, which includes a display label
 * indicating the current mode and a button to switch between them.
 */

@Composable
fun ToggleThemeScreen(
    isDarkThemeOn: Boolean,
    content: @Composable (isDarkThemeOn: Boolean) -> Unit = {}
) {
    content(isDarkThemeOn)
}


@Preview
@MultiDpiPreviews
@Composable
fun StatefulToggleThemeButtonScreenPreview() {
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    ToggleThemeScreen(isDarkTheme) { isDark ->
        AnimatedAppTheme(darkTheme = isDark) {
            StatefulCircularRevealThemeToggle(isDarkTheme = isDark)
        }
    }
}


