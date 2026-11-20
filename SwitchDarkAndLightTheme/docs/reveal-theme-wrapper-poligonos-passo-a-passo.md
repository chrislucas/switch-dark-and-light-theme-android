# Passo a Passo: `RevealThemeWrapper` com Formas Geométricas (Polígonos Regulares)

Este guia mostra como construir, **do zero e passo a passo**, um componente `RevealThemeWrapper` que troca o tema do app revelando o tema novo dentro de uma **forma geométrica que cresce**. O exemplo usa polígonos regulares (triângulo, quadrado, hexágono…) e mantém o círculo como mais uma forma.

A ideia é estudar dois assuntos ao mesmo tempo:
1. **Efeitos visuais no Compose**: camadas, recorte (`clipPath`), `Path`, animação com `Animatable`.
2. **Como um componente é construído e testado**: separar a matemática (funções puras) da UI (Composable) e testar cada parte no nível certo.

Cada passo termina com um **✅ Verificação**: compile e confira antes de seguir.

> Todo o código usa o pacote `com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component`, o mesmo do `CircularRevealThemeWrapper`. Assim você reaproveita as funções `internal` que já existem lá (`resolveRevealOrigin`, `calculateRevealRadius`) e a constante `THEME_ANIMATION_DURATION_MS`.

---

## 1. Objetivo e Visão Geral

O `CircularRevealThemeWrapper` do projeto já resolve o problema para o círculo. O `RevealThemeWrapper` generaliza: o **wrapper** cuida das camadas e da animação, e a **forma** é um parâmetro.

```
RevealThemeWrapper(darkTheme, revealCenter, shape = RegularPolygonRevealShape(6)) { isDark -> ... }
                                              └── qualquer RevealShape: círculo, polígono, estrela…
```

### O que reaproveitamos do `CircularRevealThemeWrapper`

| Conceito | Por que existe |
|---|---|
| **Duas camadas do mesmo conteúdo** | A base desenha o tema ANTIGO; o topo desenha o tema NOVO recortado pela forma. Só existe topo durante o reveal. |
| **Base com o tema antigo até o fim** | `previousDarkTheme` só muda no `finally`, depois da animação. Se mudasse antes, as duas camadas ficariam iguais e o efeito sumiria. |
| **Tema estático nas camadas** (`StaticAppTheme`) | Quem faz a transição é o recorte. Animar cores ao mesmo tempo "lava" o efeito ou faz o tema antigo piscar no fim. |
| **`movableContentOf` com parâmetro** | O mesmo conteúdo é chamado nas duas camadas, cada uma passando o **seu** tema (`isDarkTheme`). |
| **`revealCenter` em coordenadas da raiz** | Quem clica (ex.: um botão) informa `positionInRoot()`; o wrapper converte para coordenadas locais. |
| **`LaunchedEffect(darkTheme)` + `Animatable` + `try/finally`** | Cada troca de tema dispara uma animação; o `finally` garante que nada fique preso se um novo clique interromper. |
| **`rememberUpdatedState`** | O efeito é chaveado só em `darkTheme`, mas precisa ler o valor mais recente de `revealCenter` (e agora da forma e da animação). |
| **Funções puras extraídas** (`resolveRevealOrigin`, `calculateRevealRadius`) | A matemática fica testável com JUnit puro, sem Compose. |

### Arquivos que você vai criar

```
presentation/component/
├── RevealShape.kt          ← geometria + interface RevealShape + formas (passos 2 a 5)
└── RevealThemeWrapper.kt   ← o Composable (passos 6 e 7)

src/test/.../component/
├── RevealShapeTest.kt          ← testes puros (8.a)
└── RevealThemeWrapperTest.kt   ← Robolectric (8.b)

src/androidTest/.../component/
└── RevealThemeWrapperPixelTest.kt  ← UI instrumentado, pixels (8.c)
```

---

## 2. Geometria do Polígono Regular

Um polígono regular de `n` lados é definido por:
- **centro** `c`;
- **raio circunscrito** `R`: distância do centro até cada **vértice**;
- **rotação** `θ₀`: ângulo do primeiro vértice.

### 2.1 Vértices com seno e cosseno

Os vértices ficam igualmente espaçados num círculo de raio `R`, a cada `2π/n` radianos:

```
θk = θ₀ + 2πk / n          (k = 0, 1, …, n-1)
vk = ( c.x + R·cos θk ,  c.y + R·sin θk )
```

> **Atenção ao eixo y.** No Compose (e no Canvas do Android), **y cresce para baixo**. Por isso:
> - ângulo `0°` aponta para a **direita**;
> - ângulo `90°` aponta para **baixo** (e não para cima, como na matemática da escola);
> - aumentar o ângulo gira no sentido **horário** na tela.
>
> Para um triângulo "apontando para cima", o primeiro vértice precisa estar em `-90°` (`sin(-90°) = -1` → `y = c.y - R`, acima do centro).

### 2.2 Raio circunscrito `R` × apótema `r`

O **apótema** `r` é a distância do centro até o **meio de um lado**: é o raio do maior círculo que cabe dentro do polígono (círculo inscrito).

```
              v1
             ╱ ╲
            ╱   ╲
           ╱  R  ╲            R = centro → vértice   (círculo circunscrito)
          ╱   ↗   ╲           r = centro → meio do lado (círculo inscrito)
     v2  ╱   c     ╲  v0
         ╲    ↓ r  ╱          r = R · cos(π / n)
          ╲───┼───╱
              meio do lado
```

Por que `r = R·cos(π/n)`? O triângulo formado pelo centro, um vértice e o meio do lado adjacente é retângulo no meio do lado. O ângulo no centro vale metade do ângulo entre dois vértices: `(2π/n) / 2 = π/n`. Então `cos(π/n) = r / R`.

| n | forma | cos(π/n) | r / R |
|---|---|---|---|
| 3 | triângulo | 0,5000 | metade |
| 4 | quadrado | 0,7071 | ≈ 71% |
| 6 | hexágono | 0,8660 | ≈ 87% |
| 12 | dodecágono | 0,9659 | ≈ 97% |
| ∞ | círculo | 1 | igual |

### 2.3 Código: funções puras de geometria

Crie `RevealShape.kt` começando só com a geometria:

```kotlin
package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Ponto a [radius] de [center], na direção [angleRadians] (0 = direita, π/2 = baixo). */
internal fun polygonVertex(center: Offset, radius: Float, angleRadians: Double): Offset =
    Offset(
        x = center.x + radius * cos(angleRadians).toFloat(),
        y = center.y + radius * sin(angleRadians).toFloat()
    )

/**
 * Vértices de um polígono regular de [sides] lados, raio circunscrito [radius] e
 * primeiro vértice girado [rotationDegrees] graus (sentido horário na tela).
 * Usado pelos testes e para depuração; o desenho (passo 5) não aloca lista.
 */
internal fun regularPolygonVertices(
    center: Offset,
    radius: Float,
    sides: Int,
    rotationDegrees: Float = 0f
): List<Offset> {
    val start = Math.toRadians(rotationDegrees.toDouble())
    val step = 2 * PI / sides
    return List(sides) { k -> polygonVertex(center, radius, start + k * step) }
}

/** Apótema (raio do círculo inscrito) de um polígono regular: r = R · cos(π / n). */
internal fun apothem(radius: Float, sides: Int): Float =
    radius * cos(PI / sides).toFloat()
```

✅ **Verificação:** o projeto compila. Se quiser, já rode os testes de vértices e apótema da seção **8.a**.

---

## 3. Qual Raio Cobre a Tela Inteira?

No círculo, a resposta já está no projeto (`calculateRevealRadius`): **a distância até o canto mais distante**, `dMax`. Para o polígono, precisamos de um pouco mais de cuidado.

### 3.1 Basta cobrir os 4 cantos

O retângulo da tela e o polígono regular são **convexos**. Se os 4 cantos do retângulo estão dentro do polígono, todos os pontos entre eles também estão. Então: **o polígono cobre a tela ⇔ cobre os 4 cantos.**

### 3.2 Condição suficiente simples: apótema ≥ dMax

O círculo inscrito (raio = apótema `r`) está **inteiro dentro** do polígono. Se esse círculo já cobre os 4 cantos, o polígono também cobre. O círculo inscrito cobre os cantos quando `r ≥ dMax`:

```
r ≥ dMax   ⇒   R · cos(π/n) ≥ dMax   ⇒   R = dMax / cos(π/n)
```

```kotlin
// dMax = calculateRevealRadius(origin, size)  (já existe no projeto)
R = calculateRevealRadius(origin, size) / cos(PI / sides)
```

Esse valor é **conservador**: garante a cobertura para **qualquer rotação** do polígono, mas pode ser um pouco maior que o mínimo. Isso é ótimo quando o polígono **gira durante a animação** (passo 7), porque a rotação não muda o resultado.

O custo: como a animação tem duração fixa, um raio maior que o necessário faz a tela ficar coberta um pouco antes do fim. Na prática, com `n ≥ 4` isso quase não se nota.

### 3.3 Exemplos numéricos

Tela 1080 × 2400 px com origem no centro (540, 1200):

```
dMax = hypot(540, 1200) = √(291 600 + 1 440 000) = √1 731 600 ≈ 1315,9 px
```

| forma | n | 1 / cos(π/n) | R = dMax / cos(π/n) |
|---|---|---|---|
| triângulo | 3 | 2,0000 | ≈ 2631,8 px |
| quadrado | 4 | 1,4142 | ≈ 1861,0 px |
| hexágono | 6 | 1,1547 | ≈ 1519,5 px |
| dodecágono | 12 | 1,0353 | ≈ 1362,3 px |
| "quase círculo" | 360 | 1,00004 | ≈ 1315,95 px |
| círculo | ∞ | 1 | ≈ 1315,9 px |

Quando `n → ∞`, `cos(π/n) → 1`, e o polígono volta a ser o círculo: `R → dMax`.

### 3.4 (Opcional) Raio exato para uma rotação fixa

Se a rotação **não muda** durante a animação, dá para calcular o raio mínimo exato. Um ponto `p` está dentro do polígono quando, para **cada lado**, a projeção de `p - c` na normal externa desse lado é ≤ apótema. As normais ficam no meio entre dois vértices: ângulos `θ₀ + π/n + 2πk/n`.

```
apótema necessário = max( (canto - c) · normal_k )   para os 4 cantos e os n lados
R_exato            = apótema necessário / cos(π/n)
```

Exemplo: quadrado alinhado aos eixos (rotação 45°) no centro da tela 1080 × 2400. As normais apontam para ±x e ±y, então o apótema necessário é `max(540, 1200) = 1200` e `R_exato = 1200 / 0,7071 ≈ 1697,1 px`, contra ≈ 1861,0 px do conservador.

### 3.5 Código

Acrescente em `RevealShape.kt`:

```kotlin
import androidx.compose.ui.geometry.Size

/**
 * Raio circunscrito CONSERVADOR que garante que o polígono cubra o retângulo [size]
 * para QUALQUER rotação: o círculo inscrito (apótema) precisa alcançar o canto mais
 * distante. R = dMax / cos(π / n).
 */
internal fun polygonCoverRadius(origin: Offset, size: Size, sides: Int): Float =
    calculateRevealRadius(origin, size) / cos(PI / sides).toFloat()

/**
 * Raio circunscrito MÍNIMO para uma rotação fixa: o maior valor de projeção dos
 * cantos nas normais dos lados, dividido por cos(π / n).
 */
internal fun exactPolygonCoverRadius(
    origin: Offset,
    size: Size,
    sides: Int,
    rotationDegrees: Float
): Float {
    val corners = listOf(
        Offset(0f, 0f), Offset(size.width, 0f),
        Offset(0f, size.height), Offset(size.width, size.height)
    )
    val firstNormal = Math.toRadians(rotationDegrees.toDouble()) + PI / sides
    val step = 2 * PI / sides
    var requiredApothem = 0f
    for (k in 0 until sides) {
        val angle = firstNormal + k * step
        val nx = cos(angle).toFloat()
        val ny = sin(angle).toFloat()
        for (corner in corners) {
            val projection = (corner.x - origin.x) * nx + (corner.y - origin.y) * ny
            if (projection > requiredApothem) requiredApothem = projection
        }
    }
    return requiredApothem / cos(PI / sides).toFloat()
}
```

✅ **Verificação:** compila; os testes "cobre os 4 cantos" e "n grande ≈ círculo" da seção **8.a** passam.

---

## 4. Abstração da Forma: `RevealShape`

O wrapper não precisa saber **qual** forma está desenhando. Ele precisa de duas respostas:
1. **Como desenhar** a forma com um certo tamanho, num certo ponto.
2. **Qual tamanho** cobre a tela inteira a partir da origem.

```kotlin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * Forma usada pelo [RevealThemeWrapper] para recortar a camada do tema novo.
 */
interface RevealShape {
    /**
     * Escreve a forma em [path] (reaproveitado entre frames), centrada em [center],
     * com "raio" [radius] e uma rotação extra [extraRotationDegrees] (passo 7).
     */
    fun buildPath(path: Path, center: Offset, radius: Float, extraRotationDegrees: Float = 0f)

    /** Menor [radius] que faz a forma, centrada em [origin], cobrir o retângulo [size]. */
    fun coverRadius(origin: Offset, size: Size): Float
}

/** Círculo: o mesmo efeito do CircularRevealThemeWrapper. */
object CircleRevealShape : RevealShape {
    override fun buildPath(path: Path, center: Offset, radius: Float, extraRotationDegrees: Float) {
        path.rewind()
        path.addOval(Rect(center = center, radius = radius))
    }

    override fun coverRadius(origin: Offset, size: Size): Float =
        calculateRevealRadius(origin, size)
}
```

### Por que separar a forma do wrapper?

- **Aberto para extensão, fechado para modificação:** para criar uma estrela, você escreve uma nova `RevealShape`. O wrapper não muda.
- **Testabilidade:** `coverRadius` e a geometria são funções puras, testáveis com JUnit em milissegundos (8.a). O wrapper é testado uma vez (8.b e 8.c) e vale para todas as formas.
- **Responsabilidade única:** o wrapper cuida de **quando** e **em que camada**; a forma cuida de **qual desenho**.

> Repare que a interface declara o valor padrão de `extraRotationDegrees`. Em Kotlin, a implementação (`override`) **não pode** repetir o valor padrão: ela herda o da interface.

✅ **Verificação:** compila. Ainda não há polígono; isso vem no próximo passo.

---

## 5. Construindo o `Path` do Polígono

Um `Path` é uma sequência de comandos de desenho:
- `moveTo(x, y)`: "levanta a caneta" e vai até o ponto (início do contorno);
- `lineTo(x, y)`: traça uma reta até o ponto;
- `close()`: liga o último ponto ao primeiro, fechando a forma.

### Evitando alocação por frame

O `buildPath` roda **a cada frame** da animação (dentro do `drawWithContent`). Por isso:
- o `Path` é criado **uma vez** (`remember { Path() }`) e reaproveitado;
- `path.rewind()` limpa os comandos anteriores mas **mantém a memória interna** (mais barato que `reset()`, que também a libera);
- os vértices são calculados num laço, **sem criar lista** (`Offset` é uma value class: não aloca objeto).

```kotlin
/**
 * Polígono regular de [sides] lados. [rotationDegrees] gira o primeiro vértice
 * (sentido horário na tela). Exemplos:
 * - triângulo apontando para cima: sides = 3, rotationDegrees = -90f
 * - quadrado alinhado aos eixos:   sides = 4, rotationDegrees = 45f
 * - hexágono com lado em cima:     sides = 6, rotationDegrees = 0f
 */
class RegularPolygonRevealShape(
    val sides: Int,
    val rotationDegrees: Float = 0f
) : RevealShape {

    init {
        require(sides >= 3) { "Um polígono precisa de pelo menos 3 lados (recebido: $sides)" }
    }

    private val angleStep = 2 * PI / sides

    override fun buildPath(path: Path, center: Offset, radius: Float, extraRotationDegrees: Float) {
        path.rewind()
        if (radius <= 0f) return // Path vazio: o clip não mostra nada da camada nova.

        val start = Math.toRadians((rotationDegrees + extraRotationDegrees).toDouble())
        for (k in 0 until sides) {
            val vertex = polygonVertex(center, radius, start + k * angleStep)
            if (k == 0) path.moveTo(vertex.x, vertex.y) else path.lineTo(vertex.x, vertex.y)
        }
        path.close()
    }

    // Conservador: vale para qualquer rotação (inclusive a rotação animada do passo 7).
    override fun coverRadius(origin: Offset, size: Size): Float =
        polygonCoverRadius(origin, size, sides)
}
```

✅ **Verificação:** compila. Opcional: desenhe o polígono numa preview simples com `Canvas { val p = Path(); RegularPolygonRevealShape(6).buildPath(p, center, size.minDimension / 2); drawPath(p, Color.Red) }` para "ver" a geometria antes de usar como recorte.

---

## 6. O Composable `RevealThemeWrapper`

Agora juntamos tudo. Crie `RevealThemeWrapper.kt`.

### 6.1 Mudança em relação ao círculo: animar o **progresso** (0 → 1)

No `CircularRevealThemeWrapper`, o `Animatable` anima o **raio** em pixels. Aqui vamos animar o **progresso** de 0 a 1 e calcular o raio no desenho:

```
raio do frame = progresso × raioFinal
```

Vantagens: o mesmo progresso pode guiar outros efeitos (a rotação do passo 7), e o `animationSpec` vira um parâmetro independente do tamanho da tela (útil nos testes de pixel, 8.c).

### 6.2 Código completo

```kotlin
package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.DarkColors
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.LightColors
import kotlinx.coroutines.flow.first

/**
 * Troca o tema do [content] revelando o tema novo dentro de uma [shape] que cresce
 * a partir de [revealCenter] até cobrir todo o wrapper.
 *
 * @param darkTheme tema que deve ficar visível. Quando muda, dispara o reveal.
 * @param revealCenter origem do efeito em coordenadas da RAIZ; [Offset.Unspecified] = centro.
 * @param shape forma do recorte (círculo, polígono regular, …).
 * @param animationSpec como o progresso vai de 0 a 1 (duração, easing).
 * @param content conteúdo; recebe o tema da camada em que está sendo desenhado.
 */
@Composable
fun RevealThemeWrapper(
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
    revealCenter: Offset = Offset.Unspecified,
    shape: RevealShape = CircleRevealShape,
    animationSpec: AnimationSpec<Float> = tween(THEME_ANIMATION_DURATION_MS),
    content: @Composable (isDarkTheme: Boolean) -> Unit
) {
    // ── Estado de layout ────────────────────────────────────────────────────
    var size by remember { mutableStateOf(IntSize.Zero) }
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    // ── Estado do reveal ────────────────────────────────────────────────────
    var previousDarkTheme by remember { mutableStateOf(darkTheme) } // tema da base
    var isRevealing by remember { mutableStateOf(false) }           // existe camada de topo?
    var revealOrigin by remember { mutableStateOf(Offset.Zero) }    // centro (local), fixo no reveal
    var targetRadius by remember { mutableFloatStateOf(0f) }        // raio que cobre a tela
    val progress = remember { Animatable(0f) }                      // 0 → 1

    // O efeito é chaveado só em `darkTheme`; estes valores podem mudar sem reiniciá-lo,
    // mas o reveal deve usar o valor MAIS RECENTE quando começar.
    val currentRevealCenter by rememberUpdatedState(revealCenter)
    val currentShape by rememberUpdatedState(shape)
    val currentAnimationSpec by rememberUpdatedState(animationSpec)

    val path = remember { Path() } // reaproveitado em todos os frames

    // Mesmo conteúdo nas duas camadas, cada uma passando o próprio tema.
    val movableContent = remember(content) {
        movableContentOf { isDark: Boolean -> content(isDark) }
    }

    LaunchedEffect(darkTheme) {
        if (darkTheme == previousDarkTheme) {
            isRevealing = false
            return@LaunchedEffect
        }
        snapshotFlow { size }.first { it != IntSize.Zero } // espera o primeiro layout

        val wrapperSize = size.toSize()
        val origin = resolveRevealOrigin(currentRevealCenter, positionInRoot, wrapperSize)
        revealOrigin = origin
        targetRadius = currentShape.coverRadius(origin, wrapperSize)

        progress.snapTo(0f)
        isRevealing = true
        try {
            progress.animateTo(1f, currentAnimationSpec)
        } finally {
            // Só agora a base passa para o tema novo (ver tabela da seção 1).
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
        // Camada 1: base, tema ANTIGO até o fim do reveal.
        RevealLayerTheme(darkTheme = previousDarkTheme) {
            movableContent(previousDarkTheme)
        }

        // Camada 2: tema NOVO, recortado pela forma.
        if (isRevealing) {
            RevealLayerTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            // Lido na fase de DESENHO: a cada frame só o draw é refeito,
                            // sem recomposição nem novo layout.
                            currentShape.buildPath(
                                path = path,
                                center = revealOrigin,
                                radius = progress.value * targetRadius
                            )
                            clipPath(path) { this@drawWithContent.drawContent() }
                        }
                ) {
                    movableContent(darkTheme)
                }
            }
        }
    }
}

/** Tema estático (sem animação de cor) para as camadas do reveal. */
@Composable
private fun RevealLayerTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
```

### 6.3 Pontos de atenção

- **Por que ler `progress.value` dentro do `drawWithContent`?** Leituras de estado na fase de desenho invalidam **só o desenho**. Se o raio fosse lido na composição, cada frame causaria recomposição das camadas.
- **`RevealLayerTheme` em vez de `StaticAppTheme`:** o `StaticAppTheme` é `private` no arquivo do círculo. Um novo nome evita confusão (e você pode depois unificar os dois num arquivo comum, como `internal`).
- **Origem fixada no início:** `revealOrigin` e `targetRadius` são calculados uma vez por reveal, então a forma não "pula" se `revealCenter` mudar no meio.

### 6.4 Usando no projeto

Troque o wrapper do `DarkThemeScreen` (ou crie uma preview) e experimente as formas:

```kotlin
RevealThemeWrapper(
    darkTheme = current,
    modifier = modifier,
    revealCenter = revealCenter,
    shape = RegularPolygonRevealShape(sides = 6),
) { layerIsDark ->
    StatelessThemeToggleButton(
        isDarkTheme = layerIsDark,
        onToggleTheme = { buttonCenter ->
            revealCenter = buttonCenter
            viewModel.toggleTheme()
        }
    )
}
```

✅ **Verificação:** rode o app ou a preview interativa; clique no botão com `sides = 3`, `4`, `6` e `CircleRevealShape`. A forma deve nascer no botão e cobrir a tela inteira ao fim da animação.

---

## 7. Extras de Efeito (Opcionais)

### 7.1 Girar o polígono enquanto cresce

Como animamos o progresso (0 → 1), dá para derivar a rotação dele. Adicione um parâmetro ao wrapper:

```kotlin
@Composable
fun RevealThemeWrapper(
    // ...parâmetros anteriores...
    spinDegrees: Float = 0f, // quanto a forma gira durante o reveal (ex.: 90f)
    content: @Composable (isDarkTheme: Boolean) -> Unit
)
```

E no desenho:

```kotlin
.drawWithContent {
    val p = progress.value
    currentShape.buildPath(
        path = path,
        center = revealOrigin,
        radius = p * targetRadius,
        extraRotationDegrees = p * spinDegrees
    )
    clipPath(path) { this@drawWithContent.drawContent() }
}
```

Aqui o raio **conservador** (seção 3.2) é essencial: ele cobre a tela em **qualquer** rotação. Com o raio exato (3.4), que vale só para uma rotação fixa, podiam sobrar cantos descobertos ao fim do giro.

### 7.2 Easing e duração

O `animationSpec` define o "ritmo" do progresso:

```kotlin
animationSpec = tween(durationMillis = 700, easing = LinearEasing)          // constante
animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing)   // rápido → lento (padrão do tween)
animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)          // com "quique"
animationSpec = keyframes {                                                  // pausa no meio
    durationMillis = 900
    0.3f at 300
    0.3f at 500
}
```

> Com `spring` o progresso pode passar de 1 por um instante (overshoot). Aqui isso é inofensivo: a forma só fica maior que a tela.

---

## 8. Testes em Três Níveis

| Nível | Onde | Runner | Testa | Velocidade |
|---|---|---|---|---|
| **a. Unidade pura** | `src/test` | JUnit | geometria, `coverRadius` | milissegundos |
| **b. Comportamento** | `src/test` | `RobolectricTestRunner` | camadas, troca, interrupção | segundos, sem emulador |
| **c. UI instrumentado** | `src/androidTest` | `AndroidJUnit4` | pixels reais do recorte | precisa de emulador/dispositivo |

A regra: **teste cada coisa no nível mais barato que consegue verificá-la.** A matemática não precisa de Android; o recorte (pixels) precisa de renderização real.

### 8.a Testes de unidade puros

`app/src/test/java/com/br/switchdarkandlighttheme/feature/switchthemeeffect/presentation/component/RevealShapeTest.kt`

```kotlin
package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevealShapeTest {

    private val screen = Size(1080f, 2400f)
    private val corners = listOf(
        Offset(0f, 0f), Offset(screen.width, 0f),
        Offset(0f, screen.height), Offset(screen.width, screen.height)
    )

    // ── Vértices ─────────────────────────────────────────────────────────────

    @Test
    fun `quadrado com rotacao de 45 graus tem vertices nas diagonais`() {
        // R = √2 → cada vértice fica a 1 unidade do centro em x e em y.
        val vertices = regularPolygonVertices(Offset.Zero, sqrt(2f), sides = 4, rotationDegrees = 45f)

        val expected = listOf(Offset(1f, 1f), Offset(-1f, 1f), Offset(-1f, -1f), Offset(1f, -1f))
        expected.zip(vertices).forEach { (e, v) -> assertOffsetEquals(e, v) }
    }

    @Test
    fun `triangulo com rotacao de -90 graus aponta para cima`() {
        val vertices = regularPolygonVertices(Offset(100f, 100f), 50f, sides = 3, rotationDegrees = -90f)

        // y cresce para baixo: o vértice de cima tem y MENOR que o centro.
        assertOffsetEquals(Offset(100f, 50f), vertices.first())
    }

    @Test
    fun `todos os vertices ficam a distancia R do centro`() {
        val center = Offset(300f, 400f)
        regularPolygonVertices(center, 120f, sides = 7, rotationDegrees = 13f).forEach { v ->
            assertEquals(120f, (v - center).getDistance(), 0.001f)
        }
    }

    // ── Apótema ──────────────────────────────────────────────────────────────

    @Test
    fun `apotema e a distancia do centro ao meio de um lado`() {
        listOf(3, 4, 6, 12).forEach { n ->
            val v = regularPolygonVertices(Offset.Zero, 100f, sides = n)
            val midEdge = (v[0] + v[1]) / 2f
            assertEquals("n=$n", midEdge.getDistance(), apothem(100f, n), 0.001f)
        }
    }

    @Test
    fun `apotema segue a tabela de r sobre R`() {
        assertEquals(50f, apothem(100f, 3), 0.001f)
        assertEquals(70.7107f, apothem(100f, 4), 0.001f)
        assertEquals(86.6025f, apothem(100f, 6), 0.001f)
    }

    // ── coverRadius ──────────────────────────────────────────────────────────

    @Test
    fun `raio conservador segue os exemplos da documentacao`() {
        val center = Offset(540f, 1200f)
        assertEquals(2631.8f, polygonCoverRadius(center, screen, 3), 0.1f)
        assertEquals(1861.0f, polygonCoverRadius(center, screen, 4), 0.1f)
        assertEquals(1519.5f, polygonCoverRadius(center, screen, 6), 0.1f)
    }

    @Test
    fun `poligono com o raio conservador cobre os 4 cantos em qualquer rotacao`() {
        val origins = listOf(Offset(540f, 1200f), Offset(10f, 10f), Offset(1000f, 2300f), Offset(540f, 1500f))
        for (sides in listOf(3, 4, 5, 6, 8)) for (rotation in listOf(0f, 17f, 45f, 90f, 133f)) {
            for (origin in origins) {
                val shape = RegularPolygonRevealShape(sides, rotation)
                val vertices = regularPolygonVertices(origin, shape.coverRadius(origin, screen), sides, rotation)
                corners.forEach { corner ->
                    assertTrue(
                        "n=$sides rot=$rotation origem=$origin canto=$corner",
                        isInsideConvexPolygon(corner, vertices)
                    )
                }
            }
        }
    }

    @Test
    fun `raio exato cobre os cantos e nunca passa do conservador`() {
        val origin = Offset(540f, 1200f)
        for (sides in listOf(3, 4, 6)) for (rotation in listOf(0f, 30f, 45f)) {
            val exact = exactPolygonCoverRadius(origin, screen, sides, rotation)
            assertTrue(exact <= polygonCoverRadius(origin, screen, sides) + 0.01f)

            // +0,01% de folga: no raio exato algum canto fica EXATAMENTE sobre a borda.
            val vertices = regularPolygonVertices(origin, exact * 1.0001f, sides, rotation)
            corners.forEach { assertTrue("n=$sides rot=$rotation", isInsideConvexPolygon(it, vertices)) }
        }
    }

    @Test
    fun `quadrado alinhado aos eixos no centro tem raio exato de 1697`() {
        val exact = exactPolygonCoverRadius(Offset(540f, 1200f), screen, sides = 4, rotationDegrees = 45f)
        assertEquals(1697.06f, exact, 0.1f)
    }

    @Test
    fun `poligono com muitos lados se aproxima do circulo`() {
        val origin = Offset(200f, 700f)
        val circle = CircleRevealShape.coverRadius(origin, screen)
        val polygon = RegularPolygonRevealShape(sides = 360).coverRadius(origin, screen)

        // 1 / cos(π/360) ≈ 1,000038 → diferença relativa < 0,01%.
        assertEquals(1.0, (polygon / circle).toDouble(), 1e-4)
        assertEquals(1.0 / cos(PI / 360), (polygon / circle).toDouble(), 1e-5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `poligono com menos de 3 lados e invalido`() {
        RegularPolygonRevealShape(sides = 2)
    }

    // ── Auxiliares ───────────────────────────────────────────────────────────

    /**
     * Ponto dentro de polígono CONVEXO: andando pelas arestas em ordem, o ponto fica
     * sempre do mesmo lado. O sinal do produto vetorial (b - a) × (p - a) diz o lado.
     */
    private fun isInsideConvexPolygon(p: Offset, vertices: List<Offset>): Boolean {
        var sign = 0
        for (i in vertices.indices) {
            val a = vertices[i]
            val b = vertices[(i + 1) % vertices.size]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            if (abs(cross) < 1e-3f) continue // sobre a reta da aresta: conta como dentro
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    private fun assertOffsetEquals(expected: Offset, actual: Offset, delta: Float = 0.001f) {
        assertEquals("x", expected.x, actual.x, delta)
        assertEquals("y", expected.y, actual.y, delta)
    }
}
```

✅ **Verificação:** `./gradlew :app:testDebugUnitTest --tests '*RevealShapeTest*'`.

### 8.b Testes de comportamento com Robolectric

Aqui o Composable roda de verdade (composição, layout, efeitos), na JVM, sem emulador. Usamos `@RunWith(RobolectricTestRunner::class)`.

**Como verificar as camadas sem olhar pixels:** o conteúdo escreve `"light"` ou `"dark"` conforme o tema **da camada**. Durante o reveal existem as duas camadas, então os dois textos estão na árvore de semântica. O recorte é só de desenho e não remove nós da semântica.

### Controlando o relógio da animação nos testes

Por padrão (`mainClock.autoAdvance = true`), o `ComposeTestRule` **avança o relógio até tudo ficar ocioso**: qualquer animação termina antes da próxima verificação. Para verificar o **meio** do reveal, desligue o avanço automático e avance o tempo manualmente.

O detalhe que pega: com `autoAdvance = false`, as **corrotinas de efeito** (o `LaunchedEffect(darkTheme)` do wrapper) ficam numa fila e só rodam quando o teste sincroniza com a UI (`waitForIdle()`). Por isso **este padrão NÃO funciona**:

```kotlin
// ❌ O reveal nunca começa
composeRule.mainClock.autoAdvance = false
composeRule.runOnIdle { darkTheme = true }
composeRule.mainClock.advanceTimeBy(250)   // salta 250 ms de uma vez
```

O `advanceTimeBy` produz os quadros de uma vez, mas o efeito relançado só executa **depois** que ele retorna. O que o efeito muda (`isRevealing = true`) precisaria de mais um quadro para ser recomposto, e esse quadro nunca chega. Resultado: a camada nova não aparece nem no "meio" nem no "fim".

O padrão que funciona (validado nos testes do `CircularRevealThemeWrapper` deste projeto) avança **quadro a quadro**, sincronizando entre eles:

```kotlin
// ✅ Efeito, animação e recomposição se intercalam como num dispositivo real
private fun advanceTimeFrameByFrame(millis: Long) {
    val end = composeRule.mainClock.currentTime + millis
    while (composeRule.mainClock.currentTime < end) {
        composeRule.mainClock.advanceTimeByFrame() // ~16 ms: recompõe, faz layout, desenha, anima
        composeRule.waitForIdle()                  // roda as corrotinas pendentes, SEM avançar o relógio
    }
}
```

Uso:

```kotlin
composeRule.mainClock.autoAdvance = false            // 1. congela o relógio
composeRule.runOnIdle { darkTheme = true }           // 2. muda o estado no thread da UI
advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS / 2L) // 3. leva a animação até o meio
```

**Como descobri isso (técnica de depuração):** imprimir o que existe na semântica a cada quadro.

```kotlin
repeat(40) { frame ->
    composeRule.mainClock.advanceTimeByFrame()
    val light = composeRule.onAllNodesWithText("light").fetchSemanticsNodes().size
    val dark = composeRule.onAllNodesWithText("dark").fetchSemanticsNodes().size
    println("frame=$frame t=${composeRule.mainClock.currentTime} light=$light dark=$dark")
}
```

Esse laço "funcionava" mesmo sem `waitForIdle()` explícito, porque `fetchSemanticsNodes()` sincroniza com a UI por dentro. Foi essa diferença entre o laço de depuração e o teste que revelou a causa.

> **Atraso no início do reveal.** Entre a troca do estado e o primeiro quadro da animação passam alguns quadros: recomposição → relançar o efeito → `snapshotFlow` → `isRevealing = true` → recomposição. Medido neste projeto (Robolectric + `advanceTimeFrameByFrame`): a camada nova aparece **2 quadros (~32 ms)** depois da troca, e o reveal de 500 ms termina em ~560 ms. Leve esse atraso em conta quando o teste depende do tamanho exato da forma (8.c).

#### Código

`app/src/test/java/com/br/switchdarkandlighttheme/feature/switchthemeeffect/presentation/component/RevealThemeWrapperTest.kt`

```kotlin
package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RevealThemeWrapperTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var darkTheme by mutableStateOf(false)
    private var revealCenter by mutableStateOf(Offset.Unspecified)

    private fun setWrapper(initialDarkTheme: Boolean, shape: RevealShape = RegularPolygonRevealShape(6)) {
        darkTheme = initialDarkTheme
        composeRule.setContent {
            RevealThemeWrapper(
                darkTheme = darkTheme,
                revealCenter = revealCenter,
                shape = shape
            ) { layerIsDark ->
                Text(if (layerIsDark) DARK else LIGHT)
            }
        }
        composeRule.mainClock.autoAdvance = false
    }

    // Ver "Controlando o relógio da animação nos testes".
    private fun advanceTimeFrameByFrame(millis: Long) {
        val end = composeRule.mainClock.currentTime + millis
        while (composeRule.mainClock.currentTime < end) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun toggleAndGoToMiddle() {
        composeRule.runOnIdle { darkTheme = !darkTheme }
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS / 2L)
    }

    private fun goPastTheEnd() {
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS * 2L)
    }

    private fun assertLayers(light: Int, dark: Int) {
        composeRule.onAllNodesWithText(LIGHT).assertCountEquals(light)
        composeRule.onAllNodesWithText(DARK).assertCountEquals(dark)
    }

    @Test
    fun inicio_apenasACamadaBase() {
        setWrapper(initialDarkTheme = false)
        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun meioDoReveal_duasCamadas() {
        setWrapper(initialDarkTheme = false)
        toggleAndGoToMiddle()
        assertLayers(light = 1, dark = 1)
    }

    @Test
    fun fimDoReveal_apenasOTemaNovo() {
        setWrapper(initialDarkTheme = false)
        toggleAndGoToMiddle()
        goPastTheEnd()
        assertLayers(light = 0, dark = 1)
    }

    @Test
    fun interrupcao_terminaNoTemaMaisRecente() {
        setWrapper(initialDarkTheme = false)
        toggleAndGoToMiddle() // claro → escuro (interrompido)
        toggleAndGoToMiddle() // escuro → claro
        goPastTheEnd()
        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun recomposicaoSemTrocarOTema_naoIniciaReveal() {
        setWrapper(initialDarkTheme = false)
        composeRule.runOnIdle { revealCenter = Offset(10f, 10f) }
        advanceTimeFrameByFrame(THEME_ANIMATION_DURATION_MS.toLong())
        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun comportamentoIgualParaQualquerForma() {
        // O wrapper não depende da forma: o mesmo roteiro vale para todas.
        setWrapper(initialDarkTheme = false, shape = CircleRevealShape)
        toggleAndGoToMiddle()
        assertLayers(light = 1, dark = 1)
        goPastTheEnd()
        assertLayers(light = 0, dark = 1)
    }

    private companion object {
        const val LIGHT = "light"
        const val DARK = "dark"
    }
}
```

> Uma regra só aceita **um** `setContent` por teste. Por isso o teste "qualquer forma" usa uma forma diferente num teste próprio. Para cobrir várias formas, use um teste parametrizado (`@RunWith(ParameterizedRobolectricTestRunner::class)`) como exercício.

✅ **Verificação:** `./gradlew :app:testDebugUnitTest --tests '*RevealThemeWrapperTest*'`.

### 8.c Testes de UI instrumentados (pixels)

Só aqui conseguimos provar que o **recorte** acontece e que a forma é um polígono, não um círculo. `captureToImage()` tira um "print" do nó, e `toPixelMap()` permite ler cada pixel.

#### Montando um cenário com números fáceis

- Wrapper **quadrado**: `Modifier.size(300.dp)`, largura `w` = altura.
- Forma: **quadrado alinhado aos eixos** (`sides = 4`, `rotationDegrees = 45f`), origem no **centro**.
- Animação **linear** de 1000 ms (`LinearEasing`), para o raio no meio ser previsível.

Raio final: `dMax = hypot(w/2, w/2) = (w/2)·√2` e `R = dMax / cos(45°) = (w/2)·√2·√2 = w`. Em `t = 500 ms`, o raio é `ρ ≈ 0,5·w`, e o meio-lado do quadrado (apótema) é `ρ·0,7071 ≈ 0,354·w`.

#### O ponto que prova "não é um círculo"

Escolha dois pontos à **mesma distância** `0,425·w` do centro:

```
           ┌─────────────── w ───────────────┐
           │                                 │
           │         ┌───────────┐           │
           │         │  quadrado │           │
           │         │   ● c     │   ✕ A     │   A: no eixo x (0,925w ; 0,5w)
           │         │       ● B │           │      fora do quadrado → tema ANTIGO
           │         └───────────┘           │   B: na diagonal (0,8005w ; 0,8005w)
           │                                 │      dentro do quadrado → tema NOVO
           └─────────────────────────────────┘
```

- **A** (direção de uma aresta): `0,425·w` > meio-lado `0,354·w` → **fora** do quadrado → cor do tema **antigo**.
- **B** (direção de um vértice, `0,425·w/√2 ≈ 0,3005·w` em x e em y): `0,3005·w` < `0,354·w` → **dentro** → cor do tema **novo**.

Um círculo de raio `ρ = 0,5·w` conteria **os dois**. Cores diferentes à mesma distância provam que o recorte não é circular.

**Margem para o relógio:** o reveal não começa no mesmo quadro da troca (veja a nota de "atraso" em 8.b), então o teste precisa de folga. Qual é a janela válida para o raio `ρ`?

```
A fora:   meio-lado < 0,425·w   ⇒  0,7071·ρ < 0,425·w   ⇒  ρ < 0,601·w
B dentro: meio-lado > 0,3005·w  ⇒  0,7071·ρ > 0,3005·w  ⇒  ρ > 0,425·w
```

Com animação linear de 1000 ms e `R = w`, o raio é `ρ = (tempo decorrido / 1000)·w`, então a janela é **425–601 ms de animação**. O teste lê os pixels **550 ms depois da troca**: com o atraso medido de ~32 ms, a animação terá andado ~518 ms (ρ ≈ 0,52·w), bem no meio da janela. Mesmo um atraso de até ~125 ms ainda ficaria dentro dela.

#### Código

`app/src/androidTest/java/com/br/switchdarkandlighttheme/feature/switchthemeeffect/presentation/component/RevealThemeWrapperPixelTest.kt`

```kotlin
package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.DarkColors
import com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui.LightColors
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26) // captureToImage exige API 26+
class RevealThemeWrapperPixelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var darkTheme by mutableStateOf(false)
    private val oldColor = LightColors.background
    private val newColor = DarkColors.background

    private fun setWrapper(shape: RevealShape, revealCenter: Offset = Offset.Unspecified) {
        composeRule.setContent {
            RevealThemeWrapper(
                darkTheme = darkTheme,
                modifier = Modifier.size(300.dp).testTag(TAG),
                revealCenter = revealCenter,
                shape = shape,
                animationSpec = tween(durationMillis = DURATION, easing = LinearEasing)
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
        }
        composeRule.mainClock.autoAdvance = false
    }

    private fun advanceTimeFrameByFrame(millis: Long) {
        val end = composeRule.mainClock.currentTime + millis
        while (composeRule.mainClock.currentTime < end) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    /** Troca o tema e avança até o instante de amostragem (ver "Margem para o relógio"). */
    private fun toggleAndGoToMiddle() {
        composeRule.runOnIdle { darkTheme = true }
        advanceTimeFrameByFrame(SAMPLE_MS)
    }

    /** Lê a cor em frações da largura/altura do wrapper (0..1). */
    private fun colorAt(xFraction: Float, yFraction: Float): Color {
        val pixels = composeRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        val x = (xFraction * (pixels.width - 1)).roundToInt()
        val y = (yFraction * (pixels.height - 1)).roundToInt()
        return pixels[x, y]
    }

    private fun assertColor(expected: Color, actual: Color, label: String) {
        val close = abs(expected.red - actual.red) < TOLERANCE &&
            abs(expected.green - actual.green) < TOLERANCE &&
            abs(expected.blue - actual.blue) < TOLERANCE
        assertTrue("$label: esperado $expected, obtido $actual", close)
    }

    @Test
    fun meioDoReveal_centroNovo_cantoAntigo() {
        setWrapper(RegularPolygonRevealShape(sides = 4, rotationDegrees = 45f))
        toggleAndGoToMiddle()

        assertColor(newColor, colorAt(0.5f, 0.5f), "centro")
        assertColor(oldColor, colorAt(0.02f, 0.02f), "canto superior esquerdo")
    }

    @Test
    fun meioDoReveal_formaEPoligonoENaoCirculo() {
        setWrapper(RegularPolygonRevealShape(sides = 4, rotationDegrees = 45f))
        toggleAndGoToMiddle()

        // Mesma distância do centro (0,425·w), cores diferentes.
        assertColor(oldColor, colorAt(0.925f, 0.5f), "ponto A (direção da aresta)")
        assertColor(newColor, colorAt(0.8005f, 0.8005f), "ponto B (direção do vértice)")
    }

    @Test
    fun fimDoReveal_telaInteiraNoTemaNovo() {
        setWrapper(RegularPolygonRevealShape(sides = 3, rotationDegrees = -90f))
        toggleAndGoToMiddle()
        advanceTimeFrameByFrame(DURATION * 2L)

        listOf(0.02f to 0.02f, 0.98f to 0.02f, 0.02f to 0.98f, 0.98f to 0.98f).forEach { (x, y) ->
            assertColor(newColor, colorAt(x, y), "canto ($x, $y)")
        }
    }

    @Test
    fun revealCenter_noCanto_cresceAPartirDele() {
        // O wrapper está no canto superior esquerdo da raiz: (1, 1) na raiz ≈ (1, 1) local.
        setWrapper(CircleRevealShape, revealCenter = Offset(1f, 1f))
        toggleAndGoToMiddle()

        assertColor(newColor, colorAt(0.05f, 0.05f), "perto da origem")
        assertColor(oldColor, colorAt(0.98f, 0.98f), "canto oposto")
    }

    private companion object {
        const val TAG = "reveal"
        const val DURATION = 1000

        /**
         * Instante (desde a troca) em que os pixels são lidos. O reveal começa ~2
         * quadros depois da troca (~32 ms), então a animação terá andado ~518 ms:
         * dentro da janela válida (425–601 ms).
         */
        const val SAMPLE_MS = 550L
        const val TOLERANCE = 0.02f // pequenas diferenças de conversão de cor no dispositivo
    }
}
```

✅ **Verificação:** com um emulador ligado, `./gradlew :app:connectedDebugAndroidTest --tests '*RevealThemeWrapperPixelTest*'`.

> Quer rodar os testes de pixel **sem emulador**? O Robolectric consegue renderizar de verdade com `@GraphicsMode(GraphicsMode.Mode.NATIVE)` na classe de teste (em `src/test`, com `RobolectricTestRunner`). É um bom exercício comparar os dois.

---

## 9. Dependências de Teste

O projeto já tem tudo o que este guia usa (`gradle/libs.versions.toml` e `app/build.gradle.kts`):

```kotlin
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric enxerga recursos e manifest
        }
    }
}

dependencies {
    // src/test — JUnit puro e Robolectric
    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4) // createComposeRule, onNode…, mainClock
    testImplementation(libs.robolectric)                     // 4.18.1 — RobolectricTestRunner
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core.ktx)

    // src/androidTest — instrumentados
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4) // captureToImage
    androidTestImplementation(libs.androidx.espresso.core)          // traz androidx.test:runner (SdkSuppress)
    androidTestImplementation(libs.androidx.junit)                  // AndroidJUnit4

    // ComponentActivity vazia usada pelo createComposeRule (instrumentado e Robolectric)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

`toPixelMap()` vem de `androidx.compose.ui:ui-graphics`, que já é dependência do app.

---

## 10. Exercícios para Continuar

1. **Estrela de `n` pontas:** alterne vértices em dois raios (externo `R` e interno `R·k`). Atenção: a estrela **não é convexa**. Como calcular o `coverRadius`? Dica: o círculo inscrito da estrela tem raio igual à distância do centro até as arestas, que é menor que `R·k`.
2. **Polígono com cantos arredondados:** troque os `lineTo` nos vértices por `quadraticTo` (ou `arcTo`). O `coverRadius` do polígono reto continua servindo?
3. **Origem animada:** faça a origem "viajar" do botão até o centro da tela durante o reveal. O que precisa mudar no cálculo do raio para garantir a cobertura no fim?
4. **Forma via `androidx.graphics.shapes`:** a biblioteca `graphics-shapes` cria `RoundedPolygon` e faz *morphing* entre formas. Implemente uma `RevealShape` que transforma um triângulo num círculo enquanto cresce.
5. **Reveal reverso:** em vez de o tema novo crescer, faça o tema antigo **encolher** até sumir (inverta as camadas e o progresso).
6. **Teste parametrizado:** com `ParameterizedRobolectricTestRunner`, rode o roteiro da seção 8.b para triângulo, quadrado, hexágono e círculo.
7. **Comparar o raio conservador com o exato:** meça (com testes de pixel) em que momento a tela fica totalmente coberta usando cada um. Quanto tempo "parado" o conservador gera para `n = 3`?
