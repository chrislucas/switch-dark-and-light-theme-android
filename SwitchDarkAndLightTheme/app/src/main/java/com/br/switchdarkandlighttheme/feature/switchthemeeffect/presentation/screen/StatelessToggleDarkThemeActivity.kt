package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.data.preference.ThemePreferenceManager
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.CircularRevealThemeWrapper
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.StatelessThemeToggleButton
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.SystemBarsAppearanceEffect
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.ui.theme.SwitchDarkAndLightThemeTheme
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.viewmodel.ThemeViewModel

class DarkThemeScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwitchDarkAndLightThemeTheme {
                // Sem Scaffold: a tela ocupa a janela inteira (edge-to-edge) e o
                // próprio componente aplica `safeDrawingPadding` ao conteúdo. Com o
                // `innerPadding` do Scaffold, as margens se somavam e sobravam
                // faixas nas bordas pintadas pelo tema externo (que segue o sistema),
                // fora do alcance do reveal.
                DarkThemeScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
fun DarkThemeScreen(
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = viewModel(
        factory = ThemeViewModel.provideFactory(
            ThemePreferenceManager(LocalContext.current)
        )
    )
) {
    val isDarkThemeOn by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    // Origem do reveal: o centro do botão no último clique. Fica guardado aqui,
    // na tela, porque é ela que liga o botão (quem clica) ao wrapper (quem anima).
    var revealCenter by remember { mutableStateOf(Offset.Unspecified) }

    val current = isDarkThemeOn ?: return   // ou um placeholder/splash

    // Como o fundo é desenhado atrás da status bar e da navigation bar, os ícones
    // dessas barras precisam seguir o tema do APP (e não o do sistema).
    SystemBarsAppearanceEffect(isDarkTheme = current)

    // Conteúdo do reveal:
    // - O tema chega por PARÂMETRO (`layerIsDark`), não por captura: cada camada do
    //   CircularRevealThemeWrapper desenha o botão com o seu próprio tema, e não há
    //   risco de ler um valor desatualizado.
    // - A lambda captura só referências que não mudam entre recomposições
    //   (`viewModel` e o MutableState de `revealCenter`).
    // - O compilador do Compose guarda lambdas @Composable (rememberComposableLambda):
    //   a instância é a mesma entre recomposições. Por isso o `remember(content)` do
    //   wrapper não recria o `movableContentOf` a cada troca de tema.
    // - Não é preciso extrair outro @Composable: o conteúdo já é uma única chamada
    //   ao StatelessThemeToggleButton.
    CircularRevealThemeWrapper(
        modifier,
        darkTheme = current,
        revealCenter = revealCenter,
    ) { layerIsDark ->
        // Cada camada do reveal desenha o botão com o SEU tema: a base mostra o
        // tema antigo e o círculo mostra o novo, cada um com o texto correto.
        StatelessThemeToggleButton(
            isDarkTheme = layerIsDark,
            onToggleTheme = { buttonCenter ->
                revealCenter = buttonCenter
                viewModel.toggleTheme()
            }
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DarkThemeScreenPreview() {
    SwitchDarkAndLightThemeTheme {
        DarkThemeScreen()
    }
}