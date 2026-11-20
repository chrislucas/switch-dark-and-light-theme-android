package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.tooling.preview.Preview

/**
 * Alternador de tema **stateful** (com estado próprio) com efeito de reveal circular.
 *
 * O nome descreve o que o componente faz, e não como ele é desenhado:
 * - `Stateful`: guarda o próprio estado do tema (contraponto de estudo à versão
 *   stateless, que recebe o estado de fora);
 * - `CircularReveal`: troca o tema com o [CircularRevealThemeWrapper];
 * - `ThemeToggle`: é um alternador de tema. Não é só um "Button": também aplica o
 *   tema, anima a troca e ajusta as barras do sistema. O botão em si é desenhado
 *   por [StatelessThemeToggleButton].
 *
 * O componente é dono do seu estado: ele guarda internamente se o tema está
 * "escuro" ou "claro" e altera esse valor sozinho quando o botão é clicado.
 * Quem chama o componente não recebe nenhum aviso (callback) sobre a mudança.
 *
 * Segue o par clássico stateful/stateless do Compose: este componente só cuida
 * do **estado**, e delega o **desenho** para [StatelessThemeToggleButton].
 * Assim a UI fica em um único lugar, e a versão stateless continua reutilizável
 * por quem quiser controlar o estado de fora (ViewModel, DataStore etc.).
 *
 * Troca de tema com reveal circular: o conteúdo é envolvido por um
 * [CircularRevealThemeWrapper] alimentado pelo estado local. O wrapper aplica o
 * tema (sobrepondo qualquer tema definido acima na árvore) e, a cada clique,
 * revela o tema novo num círculo que nasce no centro do botão.
 *
 * Por que o wrapper fica DENTRO deste componente (e fora do stateless)?
 * O wrapper precisa receber o tema atual para saber quando animar. Na versão
 * stateless, quem tem esse estado é a tela, então é ela que envolve o conteúdo.
 * Aqui, o estado só existe dentro do componente: ninguém de fora consegue
 * alimentar o wrapper. Regra geral: o efeito de troca de tema fica junto de
 * quem é DONO do estado do tema.
 *
 * Como é o único que conhece o tema atual, também ajusta os ícones da status bar
 * e da navigation bar via [SystemBarsAppearanceEffect] (o fundo do componente é
 * desenhado atrás delas em telas edge-to-edge).
 *
 * Limitação: como o estado não sai daqui, o restante do app não fica sabendo
 * da troca. Se outras telas precisarem reagir ao tema, use
 * [StatelessThemeToggleButton] e eleve o estado (state hoisting).
 *
 * @param isDarkTheme valor inicial do estado. Como ele é usado como chave do
 * `rememberSaveable`, se quem chama passar um valor diferente depois, o estado
 * interno é reiniciado com esse novo valor.
 * @param modifier [Modifier] aplicado ao [CircularRevealThemeWrapper] (a raiz
 * do componente).
 */
@Composable
fun StatefulCircularRevealThemeToggle(
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    // Estado interno do componente.
    // - mutableStateOf: cria um estado observável; quando o valor muda, o Compose
    //   recompõe as funções que o leem (aqui, o wrapper e o componente stateless).
    // - rememberSaveable: mantém o valor entre recomposições E sobrevive a mudanças
    //   de configuração (rotação de tela, troca de idioma) e à morte do processo,
    //   pois salva o valor no Bundle do saved instance state.
    // - chave `isDarkTheme`: se o valor vindo de fora mudar, o estado salvo é
    //   descartado e recriado com o novo valor, mantendo a sincronia com o chamador.
    // - `by` (delegação): permite ler/escrever `toggleDarkTheme` como um Boolean
    //   comum, sem precisar acessar `.value`.
    var toggleDarkTheme by rememberSaveable(isDarkTheme) { mutableStateOf(isDarkTheme) }

    // Origem do reveal: centro do botão no último clique (coordenadas da raiz).
    // Usa `remember` (e não `rememberSaveable`) porque só importa durante a
    // animação; depois de uma rotação, a posição antiga nem faria sentido.
    var revealCenter by remember { mutableStateOf(Offset.Unspecified) }

    // Ícones das barras do sistema acompanham o tema deste componente.
    SystemBarsAppearanceEffect(isDarkTheme = toggleDarkTheme)

    // O wrapper aplica o tema do estado local. Quando `toggleDarkTheme` muda, ele
    // revela o tema novo num círculo que cresce a partir de `revealCenter`.
    CircularRevealThemeWrapper(
        modifier = modifier,
        darkTheme = toggleDarkTheme,
        revealCenter = revealCenter
    ) { layerIsDark ->
        // O desenho fica com a versão stateless. Cada camada do reveal passa o
        // SEU tema (`layerIsDark`), para o texto bater com as cores da camada.
        // Este componente só fornece a lógica do clique: guardar a origem do
        // círculo e inverter o booleano local.
        StatelessThemeToggleButton(
            isDarkTheme = layerIsDark,
            onToggleTheme = { buttonCenter ->
                revealCenter = buttonCenter
                toggleDarkTheme = !toggleDarkTheme
            }
        )
    }
}

// Previews: renderizam o componente no Android Studio, uma começando no tema
// claro e outra no escuro. No modo interativo, o clique troca o tema com o efeito
// de reveal, pois o componente controla o tema a partir do estado interno.

@Preview(showBackground = true, name = "Light Theme")
@Composable
fun StatefulCircularRevealThemeTogglePreviewLight() {
    StatefulCircularRevealThemeToggle(isDarkTheme = false)
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
fun StatefulCircularRevealThemeTogglePreviewDark() {
    StatefulCircularRevealThemeToggle(isDarkTheme = true)
}
