package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.br.switchdarkandlighttheme.ui.theme.SwitchDarkAndLightThemeTheme

@Composable
fun StateToggleDarkThemeManagerButton(
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    var toggleDarkTheme by rememberSaveable { mutableStateOf(isDarkTheme) }
    val surfaceModifier = remember(modifier) {
        modifier
            .safeDrawingPadding()
            .fillMaxSize()
    }
    val columnModifier = remember { Modifier.fillMaxSize() }
    val spacerModifier = remember { Modifier.height(16.dp) }
    Surface(
        modifier = surfaceModifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = columnModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (toggleDarkTheme) "Dark Theme" else "Light Theme",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = spacerModifier)

            Button(
                onClick = { toggleDarkTheme = !toggleDarkTheme },
                shape = RectangleShape
            ) {
                Text(
                    text = "Toggle Theme Mode",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun StateToggleDarkThemeManagerButtonPreviewLight() {
    SwitchDarkAndLightThemeTheme(darkTheme = false) {
        StateToggleDarkThemeManagerButton(isDarkTheme = false)
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun StateToggleDarkThemeManagerButtonPreviewDark() {
    SwitchDarkAndLightThemeTheme(darkTheme = true) {
        StateToggleDarkThemeManagerButton(isDarkTheme = true)
    }
}
