package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.DarkColors
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.LightColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de UI instrumentados (emulador/dispositivo) do [CircularRevealThemeWrapper].
 *
 * Diferente dos testes com Robolectric (que olham a árvore de semântica), aqui o
 * foco é o que é DESENHADO: capturamos a imagem do wrapper no meio da animação e
 * conferimos a cor de pixels específicos. Dentro do círculo deve aparecer o fundo
 * do tema novo; fora dele, o fundo do tema antigo.
 *
 * O wrapper tem tamanho fixo e fica deslocado da origem da tela (padding), para
 * que a conversão de `revealCenter` (coordenadas da raiz → locais) seja exercitada.
 */
@RunWith(AndroidJUnit4::class)
class CircularRevealThemeWrapperUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var darkTheme by mutableStateOf(false)
    private var revealCenter by mutableStateOf(Offset.Unspecified)

    private fun setWrapperContent() {
        composeRule.setContent {
            Box(Modifier.fillMaxSize()) {
                CircularRevealThemeWrapper(
                    modifier = Modifier
                        .padding(WRAPPER_OFFSET)
                        .size(WRAPPER_SIZE)
                        .testTag(WRAPPER_TAG),
                    darkTheme = darkTheme,
                    revealCenter = revealCenter
                ) {
                    // Cada camada pinta só o fundo do seu tema: facilita ler os pixels.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    /** Avança quadro a quadro, deixando as corrotinas de efeito rodarem entre eles. */
    private fun advanceTimeFrameByFrame(millis: Long) {
        val end = composeRule.mainClock.currentTime + millis
        while (composeRule.mainClock.currentTime < end) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    /** Lê a cor do pixel (x, y), em coordenadas locais do wrapper. */
    private fun pixelAt(x: Int, y: Int): Color =
        composeRule.onNodeWithTag(WRAPPER_TAG).captureToImage().toPixelMap()[x, y]

    private fun wrapperSizePx(): Pair<Int, Int> {
        val image = composeRule.onNodeWithTag(WRAPPER_TAG).captureToImage()
        return image.width to image.height
    }

    @Test
    fun semReveal_wrapperDesenhaOFundoDoTemaAtual() {
        setWrapperContent()

        val (w, h) = wrapperSizePx()
        assertEquals(LightColors.background, pixelAt(w / 2, h / 2))
    }

    @Test
    fun semOrigemInformada_circuloNasceNoCentroDoWrapper() {
        setWrapperContent()
        val (w, h) = wrapperSizePx()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { darkTheme = true }
        advanceTimeFrameByFrame(REVEAL_MIDDLE_MS)

        // Centro: já revelado (tema novo). Cantos: ainda o tema antigo.
        assertEquals(DarkColors.background, pixelAt(w / 2, h / 2))
        assertEquals(LightColors.background, pixelAt(EDGE_PX, EDGE_PX))
        assertEquals(LightColors.background, pixelAt(w - EDGE_PX, h - EDGE_PX))
    }

    @Test
    fun comOrigemInformada_circuloNasceNoPontoConvertidoParaCoordenadasLocais() {
        setWrapperContent()
        val (w, h) = wrapperSizePx()
        // Canto superior esquerdo do wrapper, em coordenadas da RAIZ.
        val wrapperTopLeftInRoot = composeRule.onNodeWithTag(WRAPPER_TAG)
            .fetchSemanticsNode().positionInRoot
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle {
            revealCenter = wrapperTopLeftInRoot + Offset(1f, 1f)
            darkTheme = true
        }
        advanceTimeFrameByFrame(REVEAL_MIDDLE_MS)

        // Perto da origem (canto superior esquerdo): tema novo.
        assertEquals(DarkColors.background, pixelAt(EDGE_PX, EDGE_PX))
        // Canto oposto, o mais distante da origem: ainda o tema antigo.
        assertEquals(LightColors.background, pixelAt(w - EDGE_PX, h - EDGE_PX))
    }

    @Test
    fun aoFimDoReveal_todoOWrapperFicaComOTemaNovo() {
        setWrapperContent()
        val (w, h) = wrapperSizePx()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { darkTheme = true }
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS * 2L)

        listOf(
            EDGE_PX to EDGE_PX,
            w - EDGE_PX to EDGE_PX,
            EDGE_PX to h - EDGE_PX,
            w - EDGE_PX to h - EDGE_PX,
            w / 2 to h / 2
        ).forEach { (x, y) ->
            assertEquals("pixel ($x, $y)", DarkColors.background, pixelAt(x, y))
        }
    }

    @Test
    fun cliqueNoBotaoDoConteudo_trocaOTemaComReveal() {
        // Integração com o componente real: o botão informa o próprio centro e o
        // wrapper revela o tema novo a partir dele.
        composeRule.setContent {
            CircularRevealThemeWrapper(
                darkTheme = darkTheme,
                revealCenter = revealCenter
            ) { layerIsDark ->
                StatelessThemeToggleButton(
                    isDarkTheme = layerIsDark,
                    onToggleTheme = { buttonCenter ->
                        revealCenter = buttonCenter
                        darkTheme = !darkTheme
                    }
                )
            }
        }
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText("Toggle Theme Mode").performClick()
        advanceTimeFrameByFrame(REVEAL_MIDDLE_MS)
        // Durante o reveal, as duas camadas existem: cada uma com o seu texto.
        composeRule.onAllNodesWithText("Light Theme").assertCountEquals(1)
        composeRule.onAllNodesWithText("Dark Theme").assertCountEquals(1)

        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS * 2L)
        composeRule.onAllNodesWithText("Light Theme").assertCountEquals(0)
        composeRule.onAllNodesWithText("Dark Theme").assertCountEquals(1)
    }

    private companion object {
        const val WRAPPER_TAG = "reveal-wrapper"
        val WRAPPER_OFFSET = 24.dp
        val WRAPPER_SIZE = 240.dp

        /** Distância (px) das bordas ao ler pixels "de canto", longe do antialiasing. */
        const val EDGE_PX = 3

        /** Instante intermediário: o reveal já começou, mas o círculo ainda não cobre tudo. */
        const val REVEAL_MIDDLE_MS = 250L
    }
}
