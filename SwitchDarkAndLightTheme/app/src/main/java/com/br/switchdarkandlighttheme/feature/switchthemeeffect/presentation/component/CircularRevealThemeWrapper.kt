package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import com.br.switchdarkandlighttheme.feature.MultiDpiPreviews
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.DarkColors
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.LightColors
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.flow.first

/**
 * Troca o tema do [content] com um efeito de "revelação circular": o tema novo
 * aparece dentro de um círculo que cresce a partir de [revealCenter] até cobrir
 * todo o wrapper.
 *
 * Funciona com duas camadas do mesmo conteúdo, uma sobre a outra:
 * - Camada 1 (base): tema ANTIGO, até a animação terminar;
 * - Camada 2 (topo): tema NOVO, recortada pelo círculo. Só existe durante o reveal.
 *
 * @param darkTheme tema que deve ficar visível. Quando muda, dispara o reveal.
 * @param revealCenter ponto de onde o círculo nasce, em coordenadas da RAIZ da
 * janela (ex.: o centro do botão clicado, vindo de `positionInRoot()`). O wrapper
 * converte para as próprias coordenadas. Se for [Offset.Unspecified], o círculo
 * nasce no centro do wrapper.
 * @param content conteúdo a ser desenhado. Recebe o tema da camada em que está
 * sendo desenhado, para que cada camada mostre informações coerentes com as
 * próprias cores (ex.: a base continua mostrando "Light Theme" enquanto o
 * círculo revela "Dark Theme").
 */
@Composable
fun CircularRevealThemeWrapper(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    revealCenter: Offset = Offset.Unspecified,
    content: @Composable (isDarkTheme: Boolean) -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    // Posição do wrapper na raiz, usada para converter `revealCenter` (que vem em
    // coordenadas da raiz) para coordenadas locais do wrapper.
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    val animatedRadius = remember { Animatable(0f) }
    // Armazena o tema que está sendo exibido no fundo (camada base)
    var previousDarkTheme by remember { mutableStateOf(darkTheme) }

    var isRevealing by remember { mutableStateOf(false) }
    // Centro do círculo (coordenadas locais), fixado no início de cada reveal para
    // o círculo não "pular" se `revealCenter` mudar durante a animação.
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }

    // `rememberUpdatedState` permite que o LaunchedEffect (chaveado só em
    // `darkTheme`) leia o valor MAIS RECENTE de `revealCenter` quando o reveal começa.
    val currentRevealCenter by rememberUpdatedState(revealCenter)

    val path = remember { Path() }

    // `movableContentOf` com parâmetro: cada camada chama o mesmo conteúdo passando
    // o seu próprio tema.
    val movableContent = remember(content) {
        movableContentOf { isDark: Boolean -> content(isDark) }
    }

    // Dispara a animação sempre que a prop darkTheme mudar.
    // A chave é só `darkTheme`: o tamanho é aguardado dentro do efeito, então uma
    // mudança de tamanho não deve reiniciar a animação no meio.
    LaunchedEffect(darkTheme) {
        if (darkTheme == previousDarkTheme) {
            // Nada a revelar (primeira composição ou o tema voltou ao que já está
            // na base). Garante que a camada de reveal não fique presa na tela.
            isRevealing = false
            return@LaunchedEffect
        }
        snapshotFlow {
            size
        }.first {
            it != IntSize.Zero
        } // Espera até que o tamanho seja definido

        val wrapperSize = Size(size.width.toFloat(), size.height.toFloat())
        val origin = resolveRevealOrigin(
            revealCenterInRoot = currentRevealCenter,
            wrapperPositionInRoot = positionInRoot,
            wrapperSize = wrapperSize
        )
        val targetRadius = calculateRevealRadius(origin, wrapperSize)

        revealOrigin = origin
        animatedRadius.snapTo(0f)
        isRevealing = true
        try {
            animatedRadius.animateTo(
                targetValue = targetRadius,
                animationSpec = tween(durationMillis = THEME_ANIMATION_DURATION_MS)
            )
        } finally {
            // Só agora a base passa a usar o tema novo: enquanto o círculo cresce,
            // a Camada 1 precisa continuar com o tema ANTIGO, senão as duas camadas
            // ficam iguais e o reveal não aparece.
            // O `finally` também cobre o caso de um novo clique interromper a
            // animação: o tema que estava sendo revelado vira a nova base e o
            // próximo reveal parte dele.
            previousDarkTheme = darkTheme
            isRevealing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                if (it.size != size) size = it.size
                positionInRoot = it.positionInRoot()
            }
    ) {
        // Camada 1: Tema Base (Antigo até a animação acabar).
        // Usa um tema ESTÁTICO (sem animação de cor): quem faz a transição é o
        // círculo. Com AnimatedAppTheme, a base ficaria "desbotando" para o tema
        // novo ao mesmo tempo, ou piscaria o tema antigo ao trocar no fim do reveal.
        StaticAppTheme(darkTheme = previousDarkTheme) {
            movableContent(previousDarkTheme)
        }

        // Camada 2: Tema Revelado (Novo, recortado pelo círculo)
        if (isRevealing) {
            StaticAppTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            path.rewind()
                            path.addOval(
                                Rect(
                                    center = revealOrigin,
                                    radius = animatedRadius.value
                                )
                            )
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        }
                ) {
                    movableContent(darkTheme)
                }
            }
        }
    }
}

/**
 * Converte o centro do reveal de coordenadas da RAIZ para coordenadas locais do
 * wrapper e o mantém dentro dele.
 *
 * - [revealCenterInRoot] não especificado ([Offset.Unspecified]): usa o centro do wrapper.
 * - Ponto fora do wrapper: é "puxado" para a borda mais próxima (coerceIn), para o
 *   círculo sempre nascer numa parte visível.
 */
internal fun resolveRevealOrigin(
    revealCenterInRoot: Offset,
    wrapperPositionInRoot: Offset,
    wrapperSize: Size
): Offset {
    if (!revealCenterInRoot.isSpecified) {
        return Offset(wrapperSize.width / 2f, wrapperSize.height / 2f)
    }
    val local = revealCenterInRoot - wrapperPositionInRoot
    return Offset(
        x = local.x.coerceIn(0f, wrapperSize.width),
        y = local.y.coerceIn(0f, wrapperSize.height)
    )
}

/**
 * Menor raio que faz um círculo centrado em [origin] cobrir todo o retângulo de
 * tamanho [size]: a distância de [origin] até o canto mais distante.
 *
 * Espera [origin] dentro do retângulo (ver [resolveRevealOrigin]).
 */
internal fun calculateRevealRadius(origin: Offset, size: Size): Float {
    val width = size.width
    val height = size.height
    // ── Raio final do círculo ────────────────────────────────────────────────
    //
    // Objetivo: achar o MENOR raio que faz o círculo, centrado em `origin`,
    // cobrir o retângulo inteiro do wrapper (largura `size.width`, altura `size.height`).
    //
    // 1) Basta cobrir os 4 cantos.
    //    O retângulo é convexo e o círculo também: se os 4 cantos estão dentro
    //    do círculo, todos os pontos entre eles (o retângulo inteiro) também
    //    estão. E o ponto do retângulo mais longe do centro é sempre um canto.
    //    Logo, o raio necessário = distância até o canto MAIS DISTANTE.
    //
    // 2) Distância até cada canto = teorema de Pitágoras.
    //    Para um canto (cx, cy): dx = |cx - origin.x|, dy = |cy - origin.y| e
    //    distância = √(dx² + dy²). `hypot(dx, dy)` calcula exatamente isso, sem
    //    risco de overflow/perda de precisão ao elevar ao quadrado.
    //
    //        (0,0) ●───────────────────● (width, 0)
    //              │  ↖ hypot(x, y)    │
    //              │        ● origin = (x, y)
    //              │                 ↘ │
    //   (0,height) ●───────────────────● (width, height)
    //
    //    Canto              dx               dy
    //    (0, 0)             origin.x         origin.y
    //    (width, 0)         width - x        origin.y
    //    (0, height)        origin.x         height - y
    //    (width, height)    width - x        height - y
    //
    //    Como `origin` foi limitado (coerceIn) ao interior do wrapper, todas as
    //    diferenças acima são ≥ 0, então não é preciso usar valor absoluto.
    //
    // 3) `max(max(a, b), max(c, d))` escolhe a maior das 4 distâncias.
    //
    // Casos de referência:
    //  - origem no centro: os 4 cantos ficam à mesma distância, igual a meia
    //    diagonal: hypot(width / 2, height / 2).
    //  - origem num canto: o canto oposto fica a uma diagonal inteira:
    //    hypot(width, height). É o maior raio possível.
    //  - exemplo numa tela 1080 × 2400 com o botão em (540, 1500):
    //      cantos de cima:  hypot(540, 1500) ≈ 1594,2  ← maior
    //      cantos de baixo: hypot(540,  900) ≈ 1049,6
    //    → targetRadius ≈ 1594,2 px.
    //
    // Por que não usar sempre a diagonal inteira? Funcionaria (cobre tudo), mas
    // a animação dura sempre THEME_ANIMATION_DURATION_MS: com um raio maior que o
    // necessário, a tela fica toda coberta antes do fim e o restante do tempo
    // passa "parado". Com o raio exato, o círculo termina de cobrir a tela
    // exatamente no fim da animação.
    return max(
        max(hypot(origin.x, origin.y), hypot(width - origin.x, origin.y)),
        max(hypot(origin.x, height - origin.y), hypot(width - origin.x, height - origin.y))
    )
}

/**
 * Aplica as paletas [DarkColors]/[LightColors] sem animação de cor. Usado pelas
 * camadas do reveal, onde a transição visual é feita pelo recorte circular.
 */
@Composable
private fun StaticAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

@MultiDpiPreviews
@Preview(showBackground = true)
@Composable
fun CircularRevealThemeButtonWrapperPreviewSimpleStateless() {
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    var revealCenter by remember { mutableStateOf(Offset.Unspecified) }
    Scaffold(
        floatingActionButton = {
            // Sem centro informado: o círculo nasce no meio da tela.
            FloatingActionButton(
                onClick = {
                    revealCenter = Offset.Unspecified
                    isDarkTheme = !isDarkTheme
                }
            ) {
                Text("Toggle")
            }
        }
    ) { paddingValues ->
        CircularRevealThemeWrapper(
            modifier = Modifier.padding(paddingValues),
            darkTheme = isDarkTheme,
            revealCenter = revealCenter
        ) { layerIsDark ->
            // O botão do componente informa o próprio centro: o círculo nasce nele.
            StatelessThemeToggleButton(
                isDarkTheme = layerIsDark,
                onToggleTheme = { buttonCenter ->
                    revealCenter = buttonCenter
                    isDarkTheme = !isDarkTheme
                }
            )
        }
    }
}
