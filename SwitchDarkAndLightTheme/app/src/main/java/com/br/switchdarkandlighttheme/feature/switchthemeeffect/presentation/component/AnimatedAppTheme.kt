package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.DarkColors
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.LightColors
import com.br.switchdarkandlighttheme.ui.theme.SwitchDarkAndLightThemeTheme

/*
    https://share.google/aimode/AAxlgL2WkqGOz7pSb
 */

/** Duração (ms) da animação de transição de cores entre temas. */
const val THEME_ANIMATION_DURATION_MS = 500

/**
 * Tema customizado que aplica uma animação de transição suave entre as cores do tema claro e escuro.
 *
 * @param darkTheme Define se o tema escuro deve ser aplicado. Quando alterado, dispara a animação de cores.
 * @param content O conteúdo da aplicação que será renderizado dentro deste tema.
 */
@Composable
fun AnimatedAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    // 1. Seleciona a paleta estática alvo (DarkColors ou LightColors).
    //    Como DarkColors/LightColors são singletons de nível de arquivo, esta linha
    //    não aloca nada — apenas escolhe a referência.
    val targetColors = if (darkTheme) DarkColors else LightColors

    // 2. Define a especificação da animação.
    //    OTIMIZAÇÃO: memoizada via `remember` para evitar alocar um novo TweenSpec
    //    a cada recomposição (e são MUITAS recomposições — uma por frame de animação,
    //    para cada uma das 7 cores animadas). Sem o `remember`, criaríamos ~7 specs
    //    por frame durante 500ms = centenas de allocations descartáveis.
    val animationSpec = remember { tween<Color>(durationMillis = THEME_ANIMATION_DURATION_MS) }

    // 3. Aplica o .copy() animando os tokens de cor necessários
    val animatedColorScheme = targetColors.copy(
        // primary: Cor principal utilizada para os elementos de maior destaque na UI.
        // O efeito da animação proporciona uma transição suave ao alternar entre os temas.
        primary = animateColorAsState(
            targetColors.primary,
            animationSpec,
            label = "primary"
        ).value,

        // onPrimary: Cor aplicada a textos e ícones que aparecem sobre a cor primary.
        // Garante que o contraste seja mantido de forma fluida durante a mudança de tema.
        onPrimary = animateColorAsState(
            targetColors.onPrimary,
            animationSpec,
            label = "onPrimary"
        ).value,

        // background: Cor de fundo principal das telas do aplicativo.
        // A animação suaviza a mudança drástica entre tons claros e escuros no fundo.
        background = animateColorAsState(
            targetColors.background,
            animationSpec,
            label = "background"
        ).value,

        // onBackground: Cor para textos e elementos que ficam sobre o background.
        // Mantém a legibilidade do conteúdo principal com uma transição harmônica.
        onBackground = animateColorAsState(
            targetColors.onBackground,
            animationSpec,
            label = "onBackground"
        ).value,

        // surface: Cor de superfícies como cards, menus e folhas (sheets).
        // Proporciona uma percepção de profundidade que se ajusta suavemente ao novo tema.
        surface = animateColorAsState(
            targetColors.surface,
            animationSpec,
            label = "surface"
        ).value,

        // onSurface: Cor para textos e ícones posicionados sobre superfícies (surface).
        // Assegura que elementos secundários de texto não "pisquem" na troca de tema.
        onSurface = animateColorAsState(
            targetColors.onSurface,
            animationSpec,
            label = "onSurface"
        ).value,

        // primaryContainer: Cor para recipientes que usam a cor primária com menos ênfase.
        // Utilizada para destacar áreas específicas sem a intensidade da cor primary.
        primaryContainer = animateColorAsState(
            targetColors.primaryContainer,
            animationSpec,
            label = "primaryContainer"
        ).value,
    )

    MaterialTheme(
        colorScheme = animatedColorScheme,
        content = content
    )
}

@Preview(showBackground = true, name = "Animated App Theme - Light")
@Composable
fun AnimatedAppThemeButtonLightPreviewState() {
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    SwitchDarkAndLightThemeTheme(darkTheme = isDarkTheme) {
        AnimatedAppTheme(darkTheme = isDarkTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                StatelessToggleDarkThemeManagerButton(
                    isDarkTheme = isDarkTheme,
                    { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Animated App Theme - Dark")
@Composable
fun AnimatedAppThemeButtonDarkPreviewState() {
    var isDarkTheme by rememberSaveable { mutableStateOf(true) }
    SwitchDarkAndLightThemeTheme(darkTheme = isDarkTheme) {
        AnimatedAppTheme(darkTheme = isDarkTheme) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                StatelessToggleDarkThemeManagerButton(
                    isDarkTheme = isDarkTheme,
                    { isDarkTheme = !isDarkTheme }
                )
            }
        }
    }
}


