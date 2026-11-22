package com.br.switchdarkandlighttheme.core.ui

/**
 * source https://github.com/android/snippets/blob/9bff578bd3145a9df8b5990647db8666a9757ed8/compose/recomposehighlighter/src/main/java/com/example/android/compose/recomposehighlighter/RecomposeHighlighter.kt
 *
 * Adaptações locais:
 * - hashCode() corrigido (o original usava Objects.hash(this), que entra em recursão infinita);
 * - lógica de contagem/timer extraída para [RecompositionCounterNode], compartilhada por
 *   [recomposeHighlighter] (borda) e [recomposeBackgroundHighlighter] (cor de fundo).
 */

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [Modifier] de DEPURAÇÃO que desenha uma borda em volta do elemento sempre que ele é
 * recomposto, para tornar visíveis recomposições excessivas.
 *
 * Escala visual (contagem de recomposições dentro de uma janela de 3 s sem novas recomposições):
 * - 1      → borda azul de 1 px
 * - 2      → borda verde de 2 dp ("provavelmente ok")
 * - 3 ou + → cor interpolada de amarelo para vermelho (vermelho pleno em ~101) e borda de N dp,
 *            ou seja, a borda engrossa a cada recomposição até preencher o elemento inteiro.
 *
 * Após 3 s sem recompor, a contagem volta a 0 e a borda some.
 *
 * Como funciona, em resumo:
 * 1. A cada recomposição da função que aplica o modifier, um novo [RecomposeHighlighterElement]
 *    é criado.
 * 2. O Compose compara o elemento novo com o anterior via `equals`. Como `equals` sempre retorna
 *    `false`, o Compose chama `update(node)` todas as vezes, e é ali que a contagem é incrementada.
 * 3. O Node (que sobrevive entre recomposições) guarda a contagem, pede apenas um redesenho
 *    (sem relayout nem nova recomposição) e reinicia o timer. Ver [RecompositionCounterNode].
 *
 * Uso: `Modifier.recomposeHighlighter()`. Não deixar em produção: o próprio modifier força
 * trabalho extra de update/draw em toda recomposição.
 *
 * @see recomposeBackgroundHighlighter variante que pinta o fundo em vez de desenhar borda.
 */
@Stable
fun Modifier.recomposeHighlighter(): Modifier = this.then(RecomposeHighlighterElement())

/**
 * [Modifier] de DEPURAÇÃO que, em vez de desenhar uma borda, troca a COR DE FUNDO do elemento
 * conforme ele é recomposto. A cor é pintada atrás do conteúdo, então não cobre o conteúdo nem
 * altera medidas/posição de nada (assim como a borda, é só desenho; nenhum espaço é reservado).
 *
 * Escala visual (mesma janela de 3 s do [recomposeHighlighter]):
 * - 1      → azul translúcido (alpha 0.15)
 * - 2      → verde translúcido (alpha 0.25)
 * - 3 ou + → interpolação de amarelo (alpha 0.30) para vermelho (alpha 0.60), vermelho pleno
 *            em ~101 recomposições.
 *
 * Após 3 s sem recompor, o fundo volta a ficar transparente.
 *
 * Atenção à ordem na cadeia de modifiers: o realce é desenhado atrás do conteúdo que vem DEPOIS
 * dele na cadeia. Portanto:
 * - coloque-o DEPOIS de um `Modifier.background(...)` do próprio elemento
 *   (ex.: `Modifier.background(White).recomposeBackgroundHighlighter()`), senão o fundo opaco o
 *   cobre;
 * - filhos com fundo opaco (Surface, Card etc.) também o escondem; nesses casos aplique o
 *   modifier no próprio filho ou use [recomposeHighlighter].
 */
@Stable
fun Modifier.recomposeBackgroundHighlighter(): Modifier =
    this.then(RecomposeBackgroundHighlighterElement())

/**
 * "Element": objeto leve e descartável, recriado a cada recomposição. Funciona como a
 * "receita" que o Compose usa para criar ([create]) ou atualizar ([update]) o Node persistente.
 */
private class RecomposeHighlighterElement : ModifierNodeElement<RecomposeHighlighterModifier>() {

    // Nome exibido no Layout Inspector do Android Studio.
    override fun InspectorInfo.inspectableProperties() {
        debugInspectorInfo { name = "recomposeHighlighter" }
    }

    // Chamado só na primeira composição (ou quando o Node não pode ser reaproveitado).
    // Note que create() NÃO incrementa a contagem: a composição inicial não é uma recomposição.
    override fun create(): RecomposeHighlighterModifier = RecomposeHighlighterModifier()

    // Chamado em cada recomposição em que o elemento novo for "diferente" do anterior,
    // o que, por causa do equals abaixo, é sempre.
    override fun update(node: RecomposeHighlighterModifier) {
        node.incrementCompositions()
    }

    // Truque central: nunca é igual, então toda recomposição dispara update().
    // It's never equal, so that every recomposition triggers the update function.
    override fun equals(other: Any?): Boolean = false

    // Hash por identidade: coerente com o equals acima (nenhuma instância é igual a outra)
    // e sem a recursão infinita do Objects.hash(this) original.
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Element da variante de fundo. Mesma mecânica do [RecomposeHighlighterElement]
 * (equals sempre false → update() em toda recomposição); muda apenas o Node criado.
 */
private class RecomposeBackgroundHighlighterElement :
    ModifierNodeElement<RecomposeBackgroundHighlighterModifier>() {

    override fun InspectorInfo.inspectableProperties() {
        debugInspectorInfo { name = "recomposeBackgroundHighlighter" }
    }

    override fun create(): RecomposeBackgroundHighlighterModifier =
        RecomposeBackgroundHighlighterModifier()

    override fun update(node: RecomposeBackgroundHighlighterModifier) {
        node.incrementCompositions()
    }

    override fun equals(other: Any?): Boolean = false

    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Base dos Nodes: instância persistente, presa ao LayoutNode enquanto ele estiver na árvore.
 * Guarda o estado (contagem + timer); as subclasses só decidem COMO desenhar a partir de
 * [totalCompositions], implementando [DrawModifierNode.draw].
 */
private abstract class RecompositionCounterNode : Modifier.Node(), DrawModifierNode {

    // Coroutine que zera a contagem após 3 s de inatividade.
    private var timerJob: Job? = null

    /**
     * Número de recomposições desde o último reset/timeout.
     *
     * O setter centraliza os efeitos colaterais:
     * - ignora atribuições sem mudança;
     * - se o novo valor é > 0, reinicia o timer (a janela de 3 s conta a partir da ÚLTIMA
     *   recomposição);
     * - chama invalidateDraw() para pedir só a fase de desenho, sem relayout nem recomposição
     *   (o que causaria um loop e distorceria a medição).
     *
     * Leitura liberada para as subclasses; escrita só por aqui.
     */
    protected var totalCompositions: Long = 0
        private set(value) {
            if (field == value) return
            if (value > 0) restartTimer()
            field = value
            invalidateDraw()
        }

    fun incrementCompositions() {
        totalCompositions++
    }

    // Node acabou de entrar na árvore: coroutineScope passa a estar disponível.
    override fun onAttach() {
        super.onAttach()
        restartTimer()
    }

    // Desliga a invalidação automática que o Compose faria a cada update(); o redesenho é
    // controlado manualmente via invalidateDraw() no setter acima.
    override val shouldAutoInvalidate: Boolean = false

    // Chamado quando o Node é reciclado para outro conteúdo (ex.: itens de LazyColumn).
    // Zera o estado para não herdar a contagem do item anterior.
    override fun onReset() {
        totalCompositions = 0
        timerJob?.cancel()
    }

    // Node saiu da árvore: cancela o timer (o coroutineScope do Node também é cancelado aqui).
    override fun onDetach() {
        timerJob?.cancel()
    }

    /**
     * Reinicia a janela de 3 s. Ao final, zera a contagem; o setter então chama
     * invalidateDraw() e o realce desaparece.
     * Start the timeout, and reset everytime there's a recomposition.
     */
    private fun restartTimer() {
        // coroutineScope só pode ser usado com o Node anexado.
        if (!isAttached) return

        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            delay(3000)
            totalCompositions = 0
        }
    }
}

/** Node da variante com borda: desenha o realce POR CIMA do conteúdo. */
private class RecomposeHighlighterModifier : RecompositionCounterNode() {

    override fun ContentDrawScope.draw() {
        // Desenha primeiro o conteúdo real do elemento; a borda vai por cima.
        drawContent()

        // Below is to draw the highlight, if necessary. A lot of the logic is copied from Modifier.border

        // Nada a desenhar se o elemento não tem área ou se não houve recomposição na janela atual.
        val hasValidBorderParams = size.minDimension > 0f
        if (!hasValidBorderParams || totalCompositions <= 0) {
            return
        }

        // Escolhe cor e espessura conforme a contagem.
        val (color, strokeWidthPx) =
            when (totalCompositions) {
                // We need at least one composition to draw, so draw the smallest border
                // color in blue.
                1L -> Color.Blue to 1f
                // 2 compositions is _probably_ okay.
                2L -> Color.Green to 2.dp.toPx()
                // 3 or more compositions before timeout may indicate an issue. lerp the
                // color from yellow to red, and continually increase the border size.
                // A fração (N-1)/100 chega a 1 (vermelho pleno) em ~101 recomposições;
                // a espessura é N dp e cresce sem limite.
                else -> {
                    lerp(
                        Color.Yellow.copy(alpha = 0.8f),
                        Color.Red.copy(alpha = 0.5f),
                        min(1f, (totalCompositions - 1).toFloat() / 100f),
                    ) to totalCompositions.toInt().dp.toPx()
                }
            }

        // Stroke é desenhado centralizado na linha do retângulo; recuar meia espessura mantém a
        // borda inteira dentro dos limites do elemento.
        val halfStroke = strokeWidthPx / 2
        val topLeft = Offset(halfStroke, halfStroke)
        val borderSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)

        // Se a borda (dos dois lados) for maior que a menor dimensão, as bordas se sobreporiam:
        // nesse caso preenche o elemento inteiro (Fill) em vez de desenhar contorno (Stroke).
        val fillArea = (strokeWidthPx * 2) > size.minDimension
        val rectTopLeft = if (fillArea) Offset.Zero else topLeft
        // Obs.: este `size` local sombreia o DrawScope.size a partir daqui.
        val size = if (fillArea) size else borderSize
        val style = if (fillArea) Fill else Stroke(strokeWidthPx)

        drawRect(
            brush = SolidColor(color),
            topLeft = rectTopLeft,
            size = size,
            style = style,
        )
    }
}

/**
 * Node da variante de fundo: pinta a área inteira do elemento ATRÁS do conteúdo.
 * Só a cor varia com a contagem; nada muda de tamanho, então o layout fica intacto e o
 * conteúdo continua legível (cores translúcidas).
 */
private class RecomposeBackgroundHighlighterModifier : RecompositionCounterNode() {

    override fun ContentDrawScope.draw() {
        // Sem recomposição na janela atual (ou sem área): só desenha o conteúdo normalmente.
        if (size.minDimension <= 0f || totalCompositions <= 0) {
            drawContent()
            return
        }

        val color =
            when (totalCompositions) {
                1L -> Color.Blue.copy(alpha = 0.15f)
                2L -> Color.Green.copy(alpha = 0.25f)
                // Mesma progressão da borda: de amarelo a vermelho, saturando em ~101.
                else -> lerp(
                    Color.Yellow.copy(alpha = 0.30f),
                    Color.Red.copy(alpha = 0.60f),
                    min(1f, (totalCompositions - 1).toFloat() / 100f),
                )
            }

        // Ordem invertida em relação à borda: primeiro o fundo, depois o conteúdo por cima.
        // drawRect sem topLeft/size cobre exatamente os limites do elemento.
        drawRect(color = color)
        drawContent()
    }
}
