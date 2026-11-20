package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.DarkColors
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.LightColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Testes de unidade do [CircularRevealThemeWrapper] rodando na JVM com Robolectric.
 *
 * Verificam o COMPORTAMENTO das camadas (quais temas são compostos e quando),
 * usando o relógio de teste do Compose (`mainClock`) para parar a animação no meio.
 *
 * Como funciona a verificação: o conteúdo escreve um texto diferente para cada
 * tema ("light"/"dark"). Durante o reveal existem DUAS camadas, então os dois
 * textos aparecem na árvore de semântica; fora do reveal, só um.
 * O recorte circular é só de desenho e não afeta a semântica; a parte visual é
 * coberta pelos testes instrumentados (CircularRevealThemeWrapperUiTest).
 */
@RunWith(RobolectricTestRunner::class)
class CircularRevealThemeWrapperTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Estados controlados pelo teste, lidos dentro do setContent.
    private var darkTheme by mutableStateOf(false)
    private var revealCenter by mutableStateOf(Offset.Unspecified)

    // Cor de fundo que cada camada recebeu do tema (chave = tema da camada).
    private val backgroundPerLayer = mutableMapOf<Boolean, Color>()

    private fun setWrapperContent(initialDarkTheme: Boolean) {
        darkTheme = initialDarkTheme
        composeRule.setContent {
            CircularRevealThemeWrapper(
                darkTheme = darkTheme,
                revealCenter = revealCenter
            ) { layerIsDark ->
                backgroundPerLayer[layerIsDark] = MaterialTheme.colorScheme.background
                Text(if (layerIsDark) DARK_LABEL else LIGHT_LABEL)
            }
        }
    }

    /**
     * Avança o relógio de teste QUADRO A QUADRO por [millis] milissegundos.
     *
     * Por que não `mainClock.advanceTimeBy(millis)` direto? Com `autoAdvance = false`,
     * as corrotinas de efeito (o LaunchedEffect do wrapper) ficam na fila e só
     * rodam quando o teste sincroniza com a UI (`waitForIdle`). Num salto único de
     * tempo, o efeito roda tarde demais: o que ele muda (ex.: `isRevealing = true`)
     * só seria recomposto num quadro que nunca chega.
     *
     * Por isso, a cada quadro:
     * 1. `advanceTimeByFrame()` avança ~16 ms e produz um quadro (recomposição,
     *    layout, desenho e o próximo passo da animação);
     * 2. `waitForIdle()` executa as corrotinas pendentes, SEM avançar o relógio.
     * Assim efeito, animação e recomposição se intercalam como num dispositivo real.
     */
    private fun advanceTimeFrameByFrame(millis: Long) {
        val end = composeRule.mainClock.currentTime + millis
        while (composeRule.mainClock.currentTime < end) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    /** Troca o tema e avança o relógio até a metade da animação de reveal. */
    private fun toggleAndAdvanceToMiddleOfReveal() {
        composeRule.runOnIdle { darkTheme = !darkTheme }
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS / 2L)
    }

    /** Avança o relógio até depois do fim da animação de reveal. */
    private fun advancePastRevealEnd() {
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS * 2L)
    }

    private fun assertLayers(light: Int, dark: Int) {
        composeRule.onAllNodesWithText(LIGHT_LABEL).assertCountEquals(light)
        composeRule.onAllNodesWithText(DARK_LABEL).assertCountEquals(dark)
    }

    @Test
    fun composicaoInicial_desenhaApenasACamadaBaseComOTemaInicial() {
        setWrapperContent(initialDarkTheme = false)

        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun composicaoInicial_temaEscuro_desenhaApenasACamadaEscura() {
        setWrapperContent(initialDarkTheme = true)

        assertLayers(light = 0, dark = 1)
    }

    @Test
    fun conteudoRecebeAPaletaDoTemaDaPropriaCamada() {
        setWrapperContent(initialDarkTheme = false)
        composeRule.mainClock.autoAdvance = false

        toggleAndAdvanceToMiddleOfReveal()

        assertLayers(light = 1, dark = 1)
        assertEquals(LightColors.background, backgroundPerLayer[false])
        assertEquals(DarkColors.background, backgroundPerLayer[true])
    }

    @Test
    fun trocaDeTema_duranteOReveal_mantemOTemaAntigoNaBaseEONovoNoTopo() {
        setWrapperContent(initialDarkTheme = false)
        composeRule.mainClock.autoAdvance = false

        toggleAndAdvanceToMiddleOfReveal()

        // Base (antigo) + camada revelada (novo) ao mesmo tempo.
        assertLayers(light = 1, dark = 1)
    }

    @Test
    fun trocaDeTema_aoFimDoReveal_ficaApenasOTemaNovo() {
        setWrapperContent(initialDarkTheme = false)
        composeRule.mainClock.autoAdvance = false

        toggleAndAdvanceToMiddleOfReveal()
        advancePastRevealEnd()

        assertLayers(light = 0, dark = 1)
    }

    @Test
    fun trocaDeTema_doEscuroParaOClaro_funcionaNoSentidoInverso() {
        setWrapperContent(initialDarkTheme = true)
        composeRule.mainClock.autoAdvance = false

        toggleAndAdvanceToMiddleOfReveal()
        assertLayers(light = 1, dark = 1)

        advancePastRevealEnd()
        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun novaTrocaNoMeioDoReveal_terminaNoTemaMaisRecente() {
        setWrapperContent(initialDarkTheme = false)
        composeRule.mainClock.autoAdvance = false

        toggleAndAdvanceToMiddleOfReveal() // claro → escuro (interrompido)
        toggleAndAdvanceToMiddleOfReveal() // escuro → claro
        advancePastRevealEnd()

        // Termina no tema da última troca, com uma única camada (nada preso na tela).
        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun duasTrocasCompletas_voltamAoTemaInicial() {
        setWrapperContent(initialDarkTheme = false)
        composeRule.mainClock.autoAdvance = false

        toggleAndAdvanceToMiddleOfReveal()
        advancePastRevealEnd()
        toggleAndAdvanceToMiddleOfReveal()
        advancePastRevealEnd()

        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun recomposicaoSemMudarOTema_naoIniciaReveal() {
        setWrapperContent(initialDarkTheme = false)
        composeRule.mainClock.autoAdvance = false

        // Muda outro parâmetro (a origem do círculo), mas não o tema.
        composeRule.runOnIdle { revealCenter = Offset(10f, 10f) }
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS / 2L)

        assertLayers(light = 1, dark = 0)
    }

    private companion object {
        const val LIGHT_LABEL = "light"
        const val DARK_LABEL = "dark"
    }
}
