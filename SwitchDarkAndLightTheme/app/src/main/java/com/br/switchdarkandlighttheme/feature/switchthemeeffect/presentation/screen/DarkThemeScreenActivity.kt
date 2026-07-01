package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.screen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.data.preference.ThemePreferenceManager
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.ui.theme.SwitchDarkAndLightThemeTheme
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.viewmodel.ThemeViewModel

class DarkThemeScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwitchDarkAndLightThemeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DarkThemeScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun DarkThemeScreen(
    modifier: Modifier = Modifier,

    ) {
    val ctx = LocalContext.current
    val themeManager = remember(ctx) {
        ThemePreferenceManager(
            ctx
        )
    }
    val factory = remember(themeManager) {
        ThemeViewModel.provideFactory(
            themeManager
        )
    }
    val viewModel: ThemeViewModel = viewModel(factory = factory)

    val isDarkThemeOn by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    /*
        / ─────────────────────────────────────────────────────────────────────────────

        // ESTRATÉGIA ESCOLHIDA: Opção C — extração do conteúdo como @Composable próprio
        // (`ThemeToggleContent`), em vez da Opção B (`rememberUpdatedState` + lambda
        // estabilizada via `remember { { ... } }`).
        //
        // Por que C é a melhor estratégia aqui:
        //
        // 1. ESTABILIDADE GARANTIDA PELO COMPILER, SEM TRUQUES MANUAIS
        //    O Compose Compiler trata @Composable functions como "restartable groups"
        //    estáveis quando todos os parâmetros são estáveis. `Boolean` é estável e
        //    `ThemePreferenceManager` (classe simples) também é tratada como estável
        //    (ou pode ser anotada com @Stable). Não precisamos de `rememberUpdatedState`
        //    nem de `remember { { ... } }` para "enganar" o sistema — o Compose
        //    naturalmente pula a recomposição quando os parâmetros não mudam.
        //
        // 2. ZERO CAPTURA STALE / ZERO RISCO DE BUG
        //    Na Opção B, a lambda capturava `isDarkThemeOn` no closure, e precisávamos
        //    de `rememberUpdatedState` só para evitar o valor stale da primeira
        //    composição. Com C, os valores são passados por PARÂMETRO — sempre frescos,
        //    sem necessidade de "wrappers de leitura tardia". O bug do toggle
        //    dessincronizado simplesmente não pode acontecer.
        //
        // 3. LEGIBILIDADE E TESTABILIDADE
        //    `ThemeToggleContent` é uma função autocontida, com assinatura explícita,
        //    fácil de ler, mover, reusar e testar isoladamente em previews. A Opção B
        //    deixa uma lambda inline acoplada à tela hospedeira, com `rememberUpdatedState`
        //    para cada valor capturado — verboso e cognitivamente caro.
        //
        // 4. `movableContentOf` CONTINUA FUNCIONANDO
        //    Dentro de `CircularRevealThemeWrapper` o slot `content` recebe a referência
        //    da @Composable lambda que o compilador gera para o trailing-lambda do
        //    call-site. Essa referência é estável entre recomposições enquanto os
        //    parâmetros capturados (`isDarkThemeOn`, `themeManager`) forem estáveis —
        //    exatamente o caso aqui. Logo o `remember(content) { movableContentOf { ... } }`
        //    NÃO é recriado a cada toggle, preservando o estado interno entre Camada 1
        //    e Camada 2 durante a animação de reveal.
        //
        // 5. MENOS ALOCAÇÕES E MENOS ESTADO
        //    C elimina: dois `rememberUpdatedState`, um `remember { { ... } }` e o
        //    comentário longo de "cuidado com captura stale". Menos código, menos
        //    slots na slot-table, menos pontos de manutenção.
        //
        // Quando a Opção B seria preferível?
        //  - Se precisássemos manter a MESMA instância de lambda mesmo quando os
        //    parâmetros são INSTÁVEIS (ex.: capturam tipos não-estáveis que invalidariam
        //    a skippability do `@Composable`). Não é o nosso caso.

        ─────────────────────────────────────────────────────────────────────────────
     */

    val current = isDarkThemeOn ?: return   // ou um placeholder/splash

    _root_ide_package_.com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.CircularRevealThemeWrapper(
        modifier,
        darkTheme = current,
    ) {
        ThemeToggleContent(
            isDarkThemeOn = current,
            viewModel = viewModel
        )
    }
}

@Composable
private fun ThemeToggleContent(
    isDarkThemeOn: Boolean,
    viewModel: ThemeViewModel
) {
    _root_ide_package_.com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component.StatelessToggleDarkThemeManagerButton(
        isDarkTheme = isDarkThemeOn,
        onToggleTheme = { viewModel.toggleTheme() }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DarkThemeScreenPreview() {
    SwitchDarkAndLightThemeTheme {
        DarkThemeScreen()
    }
}