package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.data.preference.ThemePreferenceManager
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.StatefulCircularRevealThemeToggle
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen.ui.theme.SwitchDarkAndLightThemeTheme
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.viewmodel.ThemeViewModel

class StatefulToggleDarkThemeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwitchDarkAndLightThemeTheme {
                // Sem Scaffold: a tela ocupa a janela inteira (edge-to-edge) e o
                // componente aplica `safeDrawingPadding` só ao conteúdo. Com o
                // `innerPadding` do Scaffold, as margens se somavam e sobravam
                // faixas nas bordas pintadas pelo tema externo (que segue o sistema).
                StatefulToggleDarkThemeScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun StatefulToggleDarkThemeScreen(
    modifier: Modifier = Modifier,
) {
    // O componente stateful já traz o próprio CircularRevealThemeWrapper (ele é o
    // dono do estado do tema). Envolvê-lo com outro wrapper aqui não animaria nada:
    // a tela não conhece o tema atual para passar ao `darkTheme` do wrapper externo.
    StatefulCircularRevealThemeToggle(
        isDarkTheme = true,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun StatefulToggleDarkThemeScreenPreview() {
    SwitchDarkAndLightThemeTheme {
        StatefulToggleDarkThemeScreen(modifier = Modifier.fillMaxWidth())
    }
}