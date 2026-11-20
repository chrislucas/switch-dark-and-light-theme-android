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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.br.switchdarkandlighttheme.ui.theme.SwitchDarkAndLightThemeTheme

/**
 * Versão **stateless** (sem estado próprio) do botão de alternância de tema.
 *
 * Aplica o padrão de *state hoisting* (elevação de estado) do Compose:
 * - o estado desce como parâmetro ([isDarkTheme]);
 * - os eventos sobem como callback ([onToggleTheme]).
 *
 * O componente apenas desenha o que recebe e avisa quando o usuário clica; quem
 * decide o que fazer (inverter o booleano, salvar em DataStore, atualizar um
 * ViewModel etc.) é o chamador. Por isso este componente consegue trocar o tema
 * do app de verdade: o mesmo estado que é passado aqui pode ser passado para o
 * `SwitchDarkAndLightThemeTheme` que envolve a tela.
 *
 * Vantagens em relação a [StatefulCircularRevealThemeToggle]:
 * - fonte única da verdade (single source of truth) para o tema;
 * - fácil de testar e de pré-visualizar, pois é uma função pura dos parâmetros;
 * - reutilizável em contextos diferentes, já que não impõe como o estado é guardado.
 *
 * @param isDarkTheme estado atual do tema, controlado por quem chama.
 * @param onToggleTheme callback chamado quando o usuário clica no botão. Recebe o
 * centro do botão em coordenadas da raiz da janela, para quem quiser animar a
 * troca a partir dele (ex.: o `revealCenter` do [CircularRevealThemeWrapper]).
 * Quem não precisa da posição pode simplesmente ignorar o parâmetro.
 * @param modifier [Modifier] aplicado ao [Surface] raiz do componente.
 */
@Composable
fun StatelessThemeToggleButton(
    isDarkTheme: Boolean,
    onToggleTheme: (buttonCenterInRoot: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    // Modifiers memorizados com `remember` para não serem recriados a cada
    // recomposição. O do Surface usa `modifier` como chave: se quem chama passar
    // outro Modifier, ele é recalculado.
    // - fillMaxSize: ocupa toda a área disponível.
    // - safeDrawingPadding: evita que o CONTEÚDO fique embaixo das barras do
    //   sistema, do notch/câmera e do teclado (importante com edge-to-edge).
    //
    // O padding fica na Column, e não no Surface, de propósito: assim o fundo do
    // Surface é pintado na tela inteira (inclusive atrás da status bar e da
    // navigation bar), e só o texto e o botão respeitam as áreas seguras. Se o
    // padding ficasse no Surface, sobrariam faixas sem a cor do tema nas bordas,
    // que também ficariam de fora do efeito de reveal.
    val surfaceModifier = remember(modifier) {
        modifier.fillMaxSize()
    }
    val columnModifier = remember { Modifier.fillMaxSize().safeDrawingPadding() }
    val spacerModifier = remember { Modifier.height(16.dp) }

    // Centro do botão em coordenadas da raiz, atualizado a cada layout.
    // `remember { ... }` + MutableState sem leitores na composição: escrever aqui
    // não causa recomposição; o valor só é lido dentro do onClick.
    val buttonCenterInRoot = remember { mutableStateOf(Offset.Unspecified) }
    val buttonModifier = remember {
        Modifier.onGloballyPositioned { coordinates ->
            buttonCenterInRoot.value = coordinates.positionInRoot() + Offset(
                coordinates.size.width / 2f,
                coordinates.size.height / 2f
            )
        }
    }

    // Surface pinta o fundo com a cor `background` do tema atual. Quando o chamador
    // troca o tema (a partir do callback), MaterialTheme.colorScheme muda e esta
    // cor é atualizada automaticamente na recomposição.
    Surface(
        modifier = surfaceModifier,
        color = MaterialTheme.colorScheme.background
    ) {
        // Coluna que centraliza o conteúdo na horizontal e na vertical.
        Column(
            modifier = columnModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Título derivado diretamente do parâmetro: não há estado local,
            // então o texto sempre reflete o valor que veio de fora.
            Text(
                text = if (isDarkTheme) "Dark Theme" else "Light Theme",
                // onBackground: cor de conteúdo pensada para contrastar com `background`.
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge
            )

            // Espaço vertical fixo entre o título e o botão.
            Spacer(modifier = spacerModifier)

            // O clique NÃO altera nada aqui dentro: apenas notifica o chamador,
            // enviando junto o centro do botão (origem do efeito de reveal).
            // A UI só muda se o chamador atualizar o estado e passar um novo
            // `isDarkTheme`, o que provoca a recomposição deste componente.
            Button(
                onClick = {
                    onToggleTheme(buttonCenterInRoot.value)
                },
                modifier = buttonModifier,
                shape = RectangleShape // botão com cantos retos (sem arredondamento)
            ) {
                Text(
                    text = "Toggle Theme Mode",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

// Previews: renderizam o componente no Android Studio dentro do tema do app.
//
// Nas duas primeiras, o estado é criado na própria preview, que faz o papel de
// "dono" do estado (state hoisting na prática). O mesmo valor alimenta o tema e o
// componente, e o callback inverte o estado. Assim, no modo interativo, o clique
// troca o tema de verdade.

@Preview(showBackground = true)
@Composable
fun SimpleStatelessToggleDarkThemeButtonLightPreview() {
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    SwitchDarkAndLightThemeTheme(isDarkTheme) {
        StatelessThemeToggleButton(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { isDarkTheme = !isDarkTheme }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SimpleStatelessToggleDarkThemeButtonDarkPreview() {
    var isDarkTheme by rememberSaveable { mutableStateOf(true) }
    SwitchDarkAndLightThemeTheme(isDarkTheme) {
        StatelessThemeToggleButton(
            isDarkTheme = isDarkTheme,
            onToggleTheme = { isDarkTheme = !isDarkTheme }
        )
    }
}

// As duas previews abaixo usam valores fixos: mostram o componente estático no
// tema claro e no tema escuro, sem nenhum estado envolvido.

@Preview(showBackground = true)
@Composable
fun ContentThemeManagerManagerComponentPreviewStateless() {
    SwitchDarkAndLightThemeTheme(darkTheme = false) {
        StatelessThemeToggleButton(
            isDarkTheme = false,
            onToggleTheme = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ContentThemeManagerComponentDarkPreviewManagerStateless() {
    SwitchDarkAndLightThemeTheme(darkTheme = true) {
        StatelessThemeToggleButton(
            isDarkTheme = true,
            onToggleTheme = {}
        )
    }
}
