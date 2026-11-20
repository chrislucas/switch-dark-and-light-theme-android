package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes de unidade PUROS (JVM, sem Android/Robolectric) da matemática do reveal:
 * [resolveRevealOrigin] (onde o círculo nasce) e [calculateRevealRadius]
 * (até onde ele cresce).
 */
class CircularRevealMathTest {

    private val size = Size(width = 1080f, height = 2400f)

    // ── resolveRevealOrigin ──────────────────────────────────────────────────

    @Test
    fun `origem nao especificada usa o centro do wrapper`() {
        val origin = resolveRevealOrigin(
            revealCenterInRoot = Offset.Unspecified,
            wrapperPositionInRoot = Offset(100f, 200f),
            wrapperSize = size
        )

        assertEquals(Offset(540f, 1200f), origin)
    }

    @Test
    fun `origem e convertida de coordenadas da raiz para coordenadas locais`() {
        val origin = resolveRevealOrigin(
            revealCenterInRoot = Offset(640f, 1700f),
            wrapperPositionInRoot = Offset(100f, 200f),
            wrapperSize = size
        )

        assertEquals(Offset(540f, 1500f), origin)
    }

    @Test
    fun `origem fora do wrapper e limitada a borda mais proxima`() {
        val antesDoInicio = resolveRevealOrigin(
            revealCenterInRoot = Offset(-50f, -10f),
            wrapperPositionInRoot = Offset.Zero,
            wrapperSize = size
        )
        val depoisDoFim = resolveRevealOrigin(
            revealCenterInRoot = Offset(5000f, 9000f),
            wrapperPositionInRoot = Offset.Zero,
            wrapperSize = size
        )

        assertEquals(Offset(0f, 0f), antesDoInicio)
        assertEquals(Offset(1080f, 2400f), depoisDoFim)
    }

    // ── calculateRevealRadius ────────────────────────────────────────────────

    @Test
    fun `origem no centro resulta em meia diagonal`() {
        val radius = calculateRevealRadius(Offset(540f, 1200f), size)

        assertEquals(hypot(540f, 1200f), radius, DELTA)
    }

    @Test
    fun `origem em qualquer canto resulta na diagonal inteira`() {
        val diagonal = hypot(size.width, size.height)
        val cantos = listOf(
            Offset(0f, 0f),
            Offset(size.width, 0f),
            Offset(0f, size.height),
            Offset(size.width, size.height)
        )

        cantos.forEach { canto ->
            assertEquals("canto $canto", diagonal, calculateRevealRadius(canto, size), DELTA)
        }
    }

    @Test
    fun `raio e a distancia ate o canto mais distante`() {
        // Exemplo documentado no código: botão em (540, 1500) numa tela 1080 × 2400.
        // Cantos de cima ≈ 1594,2 (mais distantes); cantos de baixo ≈ 1049,6.
        val radius = calculateRevealRadius(Offset(540f, 1500f), size)

        assertEquals(hypot(540f, 1500f), radius, DELTA)
        assertEquals(1594.24f, radius, 0.01f)
    }

    @Test
    fun `circulo com o raio calculado cobre os quatro cantos`() {
        val origens = listOf(
            Offset(10f, 10f),
            Offset(900f, 300f),
            Offset(200f, 2300f),
            Offset(540f, 1200f)
        )

        origens.forEach { origin ->
            val radius = calculateRevealRadius(origin, size)
            listOf(
                Offset(0f, 0f),
                Offset(size.width, 0f),
                Offset(0f, size.height),
                Offset(size.width, size.height)
            ).forEach { canto ->
                val distancia = (canto - origin).getDistance()
                assertTrue(
                    "canto $canto fora do círculo (origem $origin, raio $radius)",
                    distancia <= radius + DELTA
                )
            }
        }
    }

    private companion object {
        const val DELTA = 0.001f
    }
}
