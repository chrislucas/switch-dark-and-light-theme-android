# Passo a passo: transformando a troca de tema com reveal numa biblioteca reutilizável

Este guia mostra como tirar o `CircularRevealThemeWrapper` (e tudo o que gira em volta
dele) de dentro do app `SwitchDarkAndLightTheme` e transformá-lo num **módulo de
biblioteca** — `:themereveal` — que pode ser usado em qualquer outro projeto Android
com Compose.

A ideia é seguir os passos na ordem, construindo e compilando a cada etapa. Cada passo
explica **o que** muda e **por que** muda: o objetivo é aprender a pensar em API de
componente, e não só copiar código.

> **Como este guia foi validado:** todo o código e a configuração Gradle abaixo foram
> montados num módulo real (cópia do projeto), com AGP 9.5.2, Kotlin 2.4.21, Compose BOM
> 2026.11.01 e `compileSdk` 37.1. O módulo compilou, o app compilou consumindo-o, os 12
> testes de unidade passaram e a publicação Maven gerou `.aar`, `.pom`, `.module` e
> `-sources.jar`. O que **não** foi executado está marcado como tal no texto.

---

## Sumário

0. [Onde queremos chegar](#0-onde-queremos-chegar)
1. [O que impede o reuso hoje](#1-o-que-impede-o-reuso-hoje)
2. [Passo 1 — Criar o módulo de biblioteca](#passo-1--criar-o-módulo-de-biblioteca)
3. [Passo 2 — Desenhar a API pública antes do código](#passo-2--desenhar-a-api-pública-antes-do-código)
4. [Passo 3 — Tirar a paleta do app: o slot `theme`](#passo-3--tirar-a-paleta-do-app-o-slot-theme)
5. [Passo 4 — Tirar a duração fixa: `animationSpec`](#passo-4--tirar-a-duração-fixa-animationspec)
6. [Passo 5 — Mover a geometria (código `internal`)](#passo-5--mover-a-geometria-código-internal)
7. [Passo 6 — O `ThemeRevealHost` completo](#passo-6--o-themerevealhost-completo)
8. [Passo 7 — A origem do círculo: `Modifier.onCenterInRootChanged`](#passo-7--a-origem-do-círculo-modifieroncenterinrootchanged)
9. [Passo 8 — Um "state holder" opcional: `ThemeRevealState`](#passo-8--um-state-holder-opcional-themerevealstate)
10. [Passo 9 — Extras: barras do sistema e valores padrão](#passo-9--extras-barras-do-sistema-e-valores-padrão)
11. [Passo 10 — Persistência fica FORA da biblioteca](#passo-10--persistência-fica-fora-da-biblioteca)
12. [Passo 11 — Levar os testes para a biblioteca](#passo-11--levar-os-testes-para-a-biblioteca)
13. [Passo 12 — Migrar o app para usar o módulo](#passo-12--migrar-o-app-para-usar-o-módulo)
14. [Passo 13 — Usar em OUTRO projeto (3 caminhos)](#passo-13--usar-em-outro-projeto-3-caminhos)
15. [Passo 14 — Checklist de qualidade de biblioteca](#passo-14--checklist-de-qualidade-de-biblioteca)
16. [Apêndice A — Problemas encontrados na validação](#apêndice-a--problemas-encontrados-na-validação)
17. [Exercícios](#exercícios)

---

## 0. Onde queremos chegar

```
SwitchDarkAndLightTheme/
├── settings.gradle.kts          ← include(":themereveal")
├── app/                         ← o app vira CONSUMIDOR da biblioteca
└── themereveal/                 ← módulo novo, sem nada específico do app
    ├── build.gradle.kts
    └── src/
        ├── main/java/com/br/themereveal/
        │   ├── ThemeRevealHost.kt     ← o componente (stateless)
        │   ├── ThemeRevealState.kt    ← state holder opcional
        │   ├── ThemeRevealExtras.kt   ← defaults, modifier da origem, barras do sistema
        │   └── RevealGeometry.kt      ← matemática (internal)
        └── test/java/com/br/themereveal/
            ├── RevealGeometryTest.kt
            ├── ThemeRevealHostTest.kt
            └── ThemeRevealStateTest.kt
```

E o uso, em qualquer app, fica assim:

```kotlin
ThemeRevealHost(
    isDarkTheme = isDark,                                   // vem de onde o APP quiser
    theme = { dark, content -> MeuAppTheme(darkTheme = dark, content = content) },
    revealOrigin = centroDoBotao,
) { layerIsDark ->
    MinhaTela(isDarkTheme = layerIsDark, onToggle = { ... })
}
```

---

## 1. O que impede o reuso hoje

Um componente é reutilizável quando **não conhece nada do app** em que está. Hoje, o
efeito está espalhado por vários arquivos e amarrado ao projeto em vários pontos:

| Onde está hoje | Acoplamento | O que vira na biblioteca |
|---|---|---|
| `StaticAppTheme` (dentro do `CircularRevealThemeWrapper.kt`) | usa `LightColors`/`DarkColors` do app e força `MaterialTheme` | **slot** `theme`: o app diz como aplicar o próprio tema (Passo 3) |
| `THEME_ANIMATION_DURATION_MS` (em `AnimatedAppTheme.kt`) | duração fixa, definida em outro arquivo do app | parâmetro `animationSpec` (Passo 4) |
| `@MultiDpiPreviews` e a preview com `Scaffold` + `StatelessThemeToggleButton` | anotação e componente do app no mesmo arquivo do efeito | previews ficam no app (ou num módulo `:sample`) |
| `StatelessThemeToggleButton` | textos fixos ("Toggle Theme Mode"), layout de demonstração | **fica no app**. O que é genérico nele — medir o centro do botão — vira `Modifier.onCenterInRootChanged` (Passo 7) |
| `StatefulCircularRevealThemeToggle` | estado + botão do app + barras do sistema, tudo junto | `ThemeRevealState` + `rememberThemeRevealState()` (Passo 8) |
| `SystemBarsAppearanceEffect` | genérico, mas é uma decisão de tela | `ThemeRevealSystemBarsEffect`, **opcional** (Passo 9) |
| `ThemeViewModel` / `ThemePreferenceManager` (DataStore) | decisão de persistência | **fica no app** (Passo 10) |
| pacote `com.br.switchdarkandlighttheme.feature...` | nome do app no pacote | `com.br.themereveal` |

A regra que guia tudo:

> **Tudo o que é decisão do app entra por parâmetro. A biblioteca só sabe fazer o efeito.**

---

## Passo 1 — Criar o módulo de biblioteca

### 1.1 Registrar o módulo e o plugin

`settings.gradle.kts`:

```kotlin
rootProject.name = "SwitchDarkAndLightTheme"
include(":app")
include(":themereveal")
```

`gradle/libs.versions.toml` — o plugin de **biblioteca** e duas bibliotecas do Compose
que o app ainda não declarava:

```toml
[libraries]
# ...
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-animation-core = { group = "androidx.compose.animation", name = "animation-core" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`build.gradle.kts` da raiz — declara o plugin sem aplicar:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

### 1.2 O `build.gradle.kts` do módulo

`themereveal/build.gradle.kts` (a parte de publicação, `maven-publish`, entra no Passo 13):

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.br.themereveal"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(libs.androidx.junit)
}
```

### 1.3 O que cada decisão significa

**`com.android.library` em vez de `com.android.application`.** Gera um `.aar` (código +
manifest + recursos) em vez de um APK. Não tem `applicationId`, `versionCode` nem
`targetSdk`: quem decide isso é o app que consome.

**Sem `kotlin-android`.** No AGP 9 o suporte a Kotlin é embutido; basta o plugin do
compilador do Compose (`kotlin-compose`), igual ao `:app`.

**`kotlin { explicitApi() }`.** Obriga a escrever `public` ou `internal` em tudo. Em Kotlin
o padrão é `public`, então sem isso é fácil "vazar" uma função auxiliar para a API da
biblioteca e ficar preso a ela para sempre. Teste: remova o `public` de uma função e o
build falha com *"Visibility must be specified in explicit API mode"*.

**`api` × `implementation`** — a decisão mais importante do arquivo:

| Dependência | Tipo | Por quê |
|---|---|---|
| `compose.ui` | `api` | `Modifier` e `Offset` aparecem nas assinaturas públicas |
| `animation-core` | `api` | `AnimationSpec` aparece na assinatura do host |
| `compose-bom` | `api(platform(...))` | ver a armadilha abaixo |
| `foundation` | `implementation` | `Box` e `isSystemInDarkTheme` são usados só por dentro |
| `activity-compose` | `implementation` | `LocalActivity`/`enableEdgeToEdge` só por dentro |

Regra: **se um tipo da dependência aparece numa assinatura `public`, ela é `api`**. Se
não, `implementation` — assim o consumidor não passa a depender dela sem saber.

> **Armadilha: o BOM precisa ser `api`.** Com `implementation(platform(bom))`, a
> biblioteca publicada lista `ui` e `animation-core` **sem versão** para quem compila
> contra ela. Detalhes, diagnóstico e correção no
> [Apêndice A.1](#a1-dependências-api-publicadas-sem-versão-o-bom-precisa-ser-api).

**Por que não `material3`?** A biblioteca não precisa dele. Quem usa Material passa o
`MaterialTheme` pelo slot `theme` (Passo 3); quem tem um design system próprio passa o
dele. Uma dependência a menos é uma decisão a menos imposta ao consumidor.

**`ui-test-manifest` em `testImplementation`/`androidTestImplementation`**, e não em
`debugImplementation` como no app. Ele registra a `ComponentActivity` vazia usada pelo
`createComposeRule()`. Numa biblioteca, `debugImplementation` faz essa Activity
(exportada!) aparecer no build debug do app que consome. Detalhes no
[Apêndice A.2](#a2-a-activity-de-teste-vaza-para-o-app-que-consome-a-biblioteca).

Rode `./gradlew :themereveal:assembleDebug` para confirmar que o módulo vazio compila.

---

## Passo 2 — Desenhar a API pública antes do código

Antes de mover código, escreva **como você quer que o consumidor use**. A API é a parte
difícil de mudar depois; o código interno pode ser reescrito à vontade.

```kotlin
@Composable
public fun ThemeRevealHost(
    isDarkTheme: Boolean,                    // 1. obrigatórios primeiro
    theme: RevealLayerTheme,
    modifier: Modifier = Modifier,           // 2. o primeiro opcional é o modifier
    revealOrigin: Offset = Offset.Unspecified,  // 3. demais opcionais
    animationSpec: AnimationSpec<Float> = ThemeRevealDefaults.AnimationSpec,
    content: @Composable (isDarkTheme: Boolean) -> Unit,  // 4. conteúdo no fim (trailing lambda)
)
```

A ordem segue as diretrizes de API do Compose (as mesmas dos componentes do Material):

1. parâmetros obrigatórios;
2. `modifier: Modifier = Modifier` como **primeiro** parâmetro opcional;
3. os demais opcionais, com valores padrão num objeto `...Defaults`;
4. o `content` por último, para permitir a sintaxe `ThemeRevealHost(...) { }`.

Comparando com o componente atual:

| `CircularRevealThemeWrapper` (app) | `ThemeRevealHost` (biblioteca) | Por quê |
|---|---|---|
| `darkTheme: Boolean = false` | `isDarkTheme: Boolean` (obrigatório) | um valor padrão escondia esquecimentos; o tema é sempre decisão do app |
| `revealCenter` | `revealOrigin` | "origem" descreve melhor o papel do ponto |
| (paleta fixa) | `theme` | Passo 3 |
| (duração fixa) | `animationSpec` | Passo 4 |
| `modifier` como 1º parâmetro | `modifier` como 1º **opcional** | convenção do Compose |

**Sobre o nome.** "Wrapper" diz como foi implementado; "Host" diz o papel: o componente
**hospeda** o conteúdo e controla como o tema dele troca. O prefixo `ThemeReveal` em tudo
(`ThemeRevealHost`, `ThemeRevealState`, `ThemeRevealDefaults`) agrupa a API no
autocompletar.

---

## Passo 3 — Tirar a paleta do app: o slot `theme`

Hoje cada camada usa:

```kotlin
@Composable
private fun StaticAppTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
```

`DarkColors`/`LightColors` são do app. A primeira ideia seria receber dois `ColorScheme`
por parâmetro — mas isso ainda prende a biblioteca ao Material 3 e deixa de fora
tipografia, formas, cores dinâmicas (Android 12+) e design systems próprios.

A solução é um **slot**: a biblioteca pergunta ao app "como eu aplico o seu tema?".

```kotlin
public typealias RevealLayerTheme =
    @Composable (isDarkTheme: Boolean, content: @Composable () -> Unit) -> Unit
```

O app responde com o tema que já tem:

```kotlin
private val appRevealTheme: RevealLayerTheme = { isDark, content ->
    SwitchDarkAndLightThemeTheme(darkTheme = isDark, content = content)
}
```

Funciona com qualquer coisa — inclusive um tema que não é Material. Nos testes da
biblioteca (Passo 11) o "tema" é só um `CompositionLocal`, justamente para provar isso.

### O contrato do slot (documente no KDoc!)

Um slot dá liberdade ao consumidor, mas o efeito só funciona se ele respeitar duas
regras — aprendidas na prática neste projeto:

1. **O tema tem de ser estático.** Sem `animateColorAsState` dentro dele. Quem faz a
   transição é o recorte circular. Com um tema que anima cores (como o
   `AnimatedAppTheme`), a camada de baixo "desbota" junto e o reveal some.
2. **O conteúdo tem de ser opaco** (pintar o próprio fundo, ex.: `Surface`). A camada de
   cima é desenhada sobre a de baixo; se for transparente, o tema antigo aparece através
   dela e o círculo não é visto.

Essas regras não são verificáveis pelo compilador, então vão para o KDoc do
`RevealLayerTheme` e do parâmetro `content`.

---

## Passo 4 — Tirar a duração fixa: `animationSpec`

Hoje o raio é animado em **pixels**, com `tween(THEME_ANIMATION_DURATION_MS)`. Para
receber um `AnimationSpec` de fora, há um detalhe: animar pixels faz o resultado depender
do tamanho da tela (um `spring` com o mesmo `stiffness` se comporta diferente indo até
800 px ou até 2600 px).

A solução é animar o **progresso** de `0f` a `1f` e multiplicar pelo raio final:

```kotlin
val progress = remember { Animatable(0f) }
// ...
progress.animateTo(1f, currentSpec)
// no desenho:
radius = targetRadius * progress.value
```

Agora qualquer spec funciona igual em qualquer tela:

```kotlin
ThemeRevealHost(..., animationSpec = tween(700, easing = FastOutSlowInEasing)) { ... }
ThemeRevealHost(..., animationSpec = spring(stiffness = Spring.StiffnessLow)) { ... }
```

O valor padrão vai para um objeto `ThemeRevealDefaults` (Passo 9), como o
`ButtonDefaults` do Material: o consumidor pode reaproveitá-lo
(`ThemeRevealDefaults.DurationMillis`) ao sincronizar outras animações.

---

## Passo 5 — Mover a geometria (código `internal`)

As duas funções puras saem do arquivo do componente para um arquivo próprio. Elas são
`internal`: visíveis para o código e para os testes do módulo, invisíveis para quem
consome.

`themereveal/src/main/java/com/br/themereveal/RevealGeometry.kt`:

```kotlin
package com.br.themereveal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import kotlin.math.hypot
import kotlin.math.max

/** Converte a origem de coordenadas da raiz para locais e a mantém dentro do host. */
internal fun resolveRevealOrigin(
    revealCenterInRoot: Offset,
    hostPositionInRoot: Offset,
    hostSize: Size,
): Offset {
    if (!revealCenterInRoot.isSpecified) {
        return Offset(hostSize.width / 2f, hostSize.height / 2f)
    }
    val local = revealCenterInRoot - hostPositionInRoot
    return Offset(
        x = local.x.coerceIn(0f, hostSize.width),
        y = local.y.coerceIn(0f, hostSize.height),
    )
}

/** Distância de [origin] até o canto mais distante: o menor raio que cobre tudo. */
internal fun calculateRevealRadius(origin: Offset, size: Size): Float {
    val w = size.width
    val h = size.height
    return max(
        max(hypot(origin.x, origin.y), hypot(w - origin.x, origin.y)),
        max(hypot(origin.x, h - origin.y), hypot(w - origin.x, h - origin.y)),
    )
}
```

> A explicação longa da matemática (os 4 cantos, Pitágoras, o exemplo 1080 × 2400) já
> está comentada no `CircularRevealThemeWrapper.kt` do app — leve o comentário junto se
> quiser manter o material de estudo dentro da biblioteca.

---

## Passo 6 — O `ThemeRevealHost` completo

`themereveal/src/main/java/com/br/themereveal/ThemeRevealHost.kt`:

```kotlin
package com.br.themereveal

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.first

/**
 * Aplica um tema a uma camada do reveal. Recebe o tema da camada e o conteúdo
 * que deve ficar dentro dele.
 *
 * Contrato: o tema deve ser ESTÁTICO (sem animar cores). A transição visual é
 * feita pelo recorte circular; um tema que anima cores faria a camada de baixo
 * "desbotar" ao mesmo tempo.
 */
public typealias RevealLayerTheme =
    @Composable (isDarkTheme: Boolean, content: @Composable () -> Unit) -> Unit

/**
 * Troca o tema do [content] com um círculo que cresce a partir de [revealOrigin].
 *
 * Desenha duas camadas do mesmo conteúdo: a de baixo com o tema antigo e a de cima,
 * recortada pelo círculo, com o tema novo. Quando a animação termina, sobra só uma.
 *
 * @param isDarkTheme tema que deve ficar visível. Quando muda, dispara o reveal.
 * @param theme como aplicar o tema do app a uma camada (ver [RevealLayerTheme]).
 * @param modifier aplicado à caixa que contém as camadas.
 * @param revealOrigin ponto de onde o círculo nasce, em coordenadas da RAIZ da
 *   janela (ver [onCenterInRootChanged]). [Offset.Unspecified] = centro do host.
 * @param animationSpec ritmo do círculo. Anima o PROGRESSO de 0 a 1, então
 *   funciona igual em qualquer tamanho de tela.
 * @param content conteúdo de cada camada. Recebe o tema daquela camada e deve
 *   pintar o próprio fundo (ex.: `Surface`); um conteúdo transparente deixaria a
 *   camada de baixo aparecer e o reveal não seria visto.
 */
@Composable
public fun ThemeRevealHost(
    isDarkTheme: Boolean,
    theme: RevealLayerTheme,
    modifier: Modifier = Modifier,
    revealOrigin: Offset = Offset.Unspecified,
    animationSpec: AnimationSpec<Float> = ThemeRevealDefaults.AnimationSpec,
    content: @Composable (isDarkTheme: Boolean) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    // Tema da camada de baixo: continua o ANTIGO até o círculo cobrir tudo.
    var baseIsDark by remember { mutableStateOf(isDarkTheme) }
    var isRevealing by remember { mutableStateOf(false) }
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    var targetRadius by remember { mutableFloatStateOf(0f) }
    val progress = remember { Animatable(0f) }
    val path = remember { Path() }

    // O efeito é chaveado só no tema; estes leem o valor MAIS RECENTE quando ele roda.
    val currentOrigin by rememberUpdatedState(revealOrigin)
    val currentSpec by rememberUpdatedState(animationSpec)

    // Mesmo conteúdo nas duas camadas, cada uma passando o seu tema.
    val layerContent = remember(content) {
        movableContentOf { isDark: Boolean -> content(isDark) }
    }

    LaunchedEffect(isDarkTheme) {
        if (isDarkTheme == baseIsDark) {
            isRevealing = false
            return@LaunchedEffect
        }
        snapshotFlow { size }.first { it != IntSize.Zero }

        val hostSize = Size(size.width.toFloat(), size.height.toFloat())
        val origin = resolveRevealOrigin(currentOrigin, positionInRoot, hostSize)
        circleCenter = origin
        targetRadius = calculateRevealRadius(origin, hostSize)
        progress.snapTo(0f)
        isRevealing = true
        try {
            progress.animateTo(1f, currentSpec)
        } finally {
            // Também roda se um novo clique interromper a animação.
            baseIsDark = isDarkTheme
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
        theme(baseIsDark) { layerContent(baseIsDark) }

        if (isRevealing) {
            theme(isDarkTheme) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            path.rewind()
                            path.addOval(
                                Rect(center = circleCenter, radius = targetRadius * progress.value)
                            )
                            clipPath(path) { this@drawWithContent.drawContent() }
                        }
                ) {
                    layerContent(isDarkTheme)
                }
            }
        }
    }
}

/** Versão que lê o tema e a origem de um [ThemeRevealState]. */
@Composable
public fun ThemeRevealHost(
    state: ThemeRevealState,
    theme: RevealLayerTheme,
    modifier: Modifier = Modifier,
    animationSpec: AnimationSpec<Float> = ThemeRevealDefaults.AnimationSpec,
    content: @Composable (isDarkTheme: Boolean) -> Unit,
) {
    ThemeRevealHost(
        isDarkTheme = state.isDarkTheme,
        theme = theme,
        modifier = modifier,
        revealOrigin = state.origin,
        animationSpec = animationSpec,
        content = content,
    )
}
```

O que mudou em relação ao `CircularRevealThemeWrapper`, além dos parâmetros:

- `StaticAppTheme(...)` → `theme(...)`: as duas camadas chamam o slot.
- `Animatable` anima `progress` (0→1); o raio final fica em `targetRadius`, calculado uma
  vez no início do reveal. `mutableFloatStateOf` evita o *boxing* de `Float`.
- `rememberUpdatedState(animationSpec)`: se o app trocar a spec, o próximo reveal já usa
  a nova, sem reiniciar o efeito.
- A preview saiu do arquivo: a biblioteca não depende de `ui-tooling` nem de
  componentes do app.

Rode `./gradlew :themereveal:compileDebugKotlin`. (Ele só compila depois dos Passos 8 e 9,
que criam `ThemeRevealState` e `ThemeRevealDefaults` — se preferir compilar já, crie
esses arquivos antes.)

---

## Passo 7 — A origem do círculo: `Modifier.onCenterInRootChanged`

No app, o `StatelessThemeToggleButton` mede o próprio centro com
`onGloballyPositioned` + `positionInRoot()`. Todo consumidor precisaria repetir isso,
então a parte genérica vira um `Modifier` da biblioteca:

```kotlin
/**
 * Informa o centro deste elemento em coordenadas da raiz sempre que o layout muda.
 * Use no botão que dispara a troca e passe o valor para `revealOrigin`.
 */
public fun Modifier.onCenterInRootChanged(onChanged: (centerInRoot: Offset) -> Unit): Modifier =
    onGloballyPositioned { coordinates ->
        onChanged(
            coordinates.positionInRoot() +
                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
        )
    }
```

Uso:

```kotlin
var buttonCenter by remember { mutableStateOf(Offset.Unspecified) }

Button(
    onClick = { revealState.toggle(fromCenterInRoot = buttonCenter) },
    modifier = Modifier.onCenterInRootChanged { buttonCenter = it },
) { Text("Trocar tema") }
```

Por que **coordenadas da raiz**? O botão e o host podem estar em pontos diferentes da
árvore (o botão numa `TopAppBar`, o host envolvendo a tela). A raiz é o único sistema de
coordenadas que os dois conhecem; o host converte para o seu próprio
(`resolveRevealOrigin`).

---

## Passo 8 — Um "state holder" opcional: `ThemeRevealState`

No estudo de stateful × stateless, o `StatefulCircularRevealThemeToggle` guardava o
estado e o `StatelessThemeToggleButton` só desenhava. Numa biblioteca, o mesmo par
aparece de outro jeito — o padrão dos componentes do próprio Compose (`DrawerState`,
`ScrollState`, `rememberLazyListState()`):

- o **componente continua stateless** (`ThemeRevealHost(isDarkTheme, ...)`);
- um **objeto de estado opcional** junta o que costuma andar junto (tema + origem) para
  quem não tem ViewModel.

`themereveal/src/main/java/com/br/themereveal/ThemeRevealState.kt`:

```kotlin
package com.br.themereveal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Estado do tema para quem NÃO tem um ViewModel/DataStore como fonte da verdade.
 *
 * Mesmo padrão de `DrawerState`/`ScrollState` do Compose: o host continua
 * stateless, e este objeto só junta "qual tema" e "de onde o círculo nasce".
 */
@Stable
public class ThemeRevealState(initialDarkTheme: Boolean) {

    public var isDarkTheme: Boolean by mutableStateOf(initialDarkTheme)
        private set

    /** Origem do último reveal, em coordenadas da raiz. */
    public var origin: Offset by mutableStateOf(Offset.Unspecified)
        private set

    /** Inverte o tema. O círculo nasce em [fromCenterInRoot] (ou no centro do host). */
    public fun toggle(fromCenterInRoot: Offset = Offset.Unspecified) {
        setDarkTheme(!isDarkTheme, fromCenterInRoot)
    }

    public fun setDarkTheme(darkTheme: Boolean, fromCenterInRoot: Offset = Offset.Unspecified) {
        origin = fromCenterInRoot
        isDarkTheme = darkTheme
    }

    public companion object {
        /** Salva só o tema: a origem não faz sentido depois de recriar a tela. */
        public val Saver: Saver<ThemeRevealState, Boolean> = Saver(
            save = { it.isDarkTheme },
            restore = { ThemeRevealState(it) },
        )
    }
}

/** Cria e lembra um [ThemeRevealState] que sobrevive a rotação e morte do processo. */
@Composable
public fun rememberThemeRevealState(
    initialDarkTheme: Boolean = isSystemInDarkTheme(),
): ThemeRevealState = rememberSaveable(saver = ThemeRevealState.Saver) {
    ThemeRevealState(initialDarkTheme)
}
```

Pontos de estudo:

- **`private set`**: de fora, só dá para mudar o estado pelos métodos (`toggle`,
  `setDarkTheme`). Isso garante que a origem e o tema mudem **juntos** — a origem
  precisa estar certa antes de o tema mudar, porque o efeito lê a origem quando o tema
  muda.
- **`@Stable`**: promete ao compilador do Compose que mudanças no objeto são notificadas
  via `State`. Com isso, quem recebe um `ThemeRevealState` pode ser pulado na
  recomposição quando nada mudou.
- **`Saver`**: ensina o `rememberSaveable` a guardar o objeto num `Bundle`. Só o
  `Boolean` é salvo.
- **`initialDarkTheme = isSystemInDarkTheme()`**: um padrão sensato para quem não tem
  preferência salva — começa igual ao sistema.

Com o state holder, o `StatefulCircularRevealThemeToggle` do app vira poucas linhas:

```kotlin
val revealState = rememberThemeRevealState()
ThemeRevealHost(state = revealState, theme = appRevealTheme) { layerIsDark -> ... }
```

---

## Passo 9 — Extras: barras do sistema e valores padrão

`themereveal/src/main/java/com/br/themereveal/ThemeRevealExtras.kt` (junte com o
`Modifier` do Passo 7):

```kotlin
package com.br.themereveal

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

/** Valores padrão do [ThemeRevealHost]. */
public object ThemeRevealDefaults {
    public const val DurationMillis: Int = 500

    public val AnimationSpec: AnimationSpec<Float> = tween(durationMillis = DurationMillis)
}

// Modifier.onCenterInRootChanged (Passo 7) aqui

/**
 * Faz os ícones das barras do sistema seguirem o tema do APP (opcional).
 * Só tem efeito numa [ComponentActivity] edge-to-edge; em previews não faz nada.
 */
@Composable
public fun ThemeRevealSystemBarsEffect(isDarkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, isDarkTheme) {
        val style = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { isDarkTheme },
        )
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
```

**Por que o host NÃO chama `ThemeRevealSystemBarsEffect` sozinho?** Porque mexer nas
barras do sistema é uma decisão da **tela**: o app pode não ser edge-to-edge, pode ter
duas áreas com temas diferentes, ou já controlar as barras de outro jeito. Uma biblioteca
que altera a janela por conta própria surpreende quem a usa. Fica disponível, mas
**opt-in**.

---

## Passo 10 — Persistência fica FORA da biblioteca

`ThemePreferenceManager` (DataStore) e `ThemeViewModel` continuam no app. A biblioteca
não sabe — nem deveria saber — se o tema vem do DataStore, do servidor, de uma conta do
usuário ou de lugar nenhum. Ela recebe um `Boolean`.

A tela do app liga as peças (código compilado na validação):

```kotlin
private val appRevealTheme: RevealLayerTheme = { isDark, content ->
    SwitchDarkAndLightThemeTheme(darkTheme = isDark, content = content)
}

@Composable
fun DarkThemeScreen(
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = viewModel(
        factory = ThemeViewModel.provideFactory(ThemePreferenceManager(LocalContext.current))
    ),
) {
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    var revealOrigin by remember { mutableStateOf(Offset.Unspecified) }

    ThemeRevealSystemBarsEffect(isDarkTheme = isDark)

    ThemeRevealHost(
        isDarkTheme = isDark,
        theme = appRevealTheme,
        modifier = modifier,
        revealOrigin = revealOrigin,
    ) { layerIsDark ->
        StatelessThemeToggleButton(
            isDarkTheme = layerIsDark,
            onToggleTheme = { center ->
                revealOrigin = center
                viewModel.toggleTheme()
            }
        )
    }
}
```

> **Por que `appRevealTheme` é um `val` de topo?** O host faz
> `remember(content) { movableContentOf { ... } }`. Lambdas estáveis evitam recriar o
> conteúdo móvel. Um `val` de topo é sempre a mesma instância; uma lambda escrita na
> chamada também seria memorizada pelo compilador do Compose (strong skipping), mas o
> `val` deixa a intenção explícita e reaproveita o tema em várias telas.

Se quiser reaproveitar a persistência entre projetos, ela vira **outro** módulo (ver
Exercícios) — nunca uma dependência do `:themereveal`.

---

## Passo 11 — Levar os testes para a biblioteca

### 11.1 Geometria (JVM pura)

Mova `CircularRevealMathTest.kt` para `themereveal/src/test/java/com/br/themereveal/` com
o nome `RevealGeometryTest.kt` e ajuste:

- `package com.br.themereveal`;
- os nomes de parâmetro: `wrapperPositionInRoot` → `hostPositionInRoot`,
  `wrapperSize` → `hostSize`.

Os 7 testes continuam valendo: como as funções são `internal`, o teste do mesmo módulo
enxerga.

### 11.2 Host com Robolectric — e um tema que NÃO é Material

Este teste prova duas coisas: o comportamento das camadas e que o slot `theme` aceita
**qualquer** tema. A biblioteca não tem `material3`, então o "tema" é um
`CompositionLocal`:

```kotlin
package com.br.themereveal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * O "tema" do teste é um CompositionLocal próprio, e não o MaterialTheme: prova que
 * a biblioteca funciona com QUALQUER sistema de design.
 */
private val LocalTestPalette = staticCompositionLocalOf { "nenhum" }

private val testTheme: RevealLayerTheme = { isDark, content ->
    CompositionLocalProvider(LocalTestPalette provides if (isDark) "escuro" else "claro") {
        content()
    }
}

@RunWith(RobolectricTestRunner::class)
class ThemeRevealHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var isDark by mutableStateOf(false)
    private val palettePerLayer = mutableMapOf<Boolean, String>()

    private fun setHost() {
        composeRule.setContent {
            ThemeRevealHost(isDarkTheme = isDark, theme = testTheme) { layerIsDark ->
                palettePerLayer[layerIsDark] = LocalTestPalette.current
                Box(Modifier.fillMaxSize().testTag(if (layerIsDark) "escuro" else "claro"))
            }
        }
    }

    private fun advanceTimeFrameByFrame(millis: Long) {
        val end = composeRule.mainClock.currentTime + millis
        while (composeRule.mainClock.currentTime < end) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun assertLayers(light: Int, dark: Int) {
        composeRule.onAllNodesWithTag("claro").assertCountEquals(light)
        composeRule.onAllNodesWithTag("escuro").assertCountEquals(dark)
    }

    @Test
    fun semTroca_umaCamadaSo() {
        setHost()
        assertLayers(light = 1, dark = 0)
    }

    @Test
    fun duranteOReveal_duasCamadasCadaUmaComOSeuTema() {
        setHost()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { isDark = true }
        advanceTimeFrameByFrame(250)

        assertLayers(light = 1, dark = 1)
        assertEquals("claro", palettePerLayer[false])
        assertEquals("escuro", palettePerLayer[true])
    }

    @Test
    fun aoFimDoReveal_soOTemaNovo() {
        setHost()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { isDark = true }
        advanceTimeFrameByFrame(1_000)

        assertLayers(light = 0, dark = 1)
    }
}
```

> O `advanceTimeFrameByFrame` é o mesmo do `CircularRevealThemeWrapperTest`: com
> `autoAdvance = false`, é preciso avançar quadro a quadro **e** chamar `waitForIdle()`
> em cada quadro, senão o `LaunchedEffect` não roda a tempo (explicação completa no guia
> `reveal-theme-wrapper-poligonos-passo-a-passo.md`). Os outros cenários do
> `CircularRevealThemeWrapperTest` (sentido inverso, interrupção, recomposição sem troca)
> podem ser portados do mesmo jeito.

### 11.3 O state holder — incluindo rotação de tela

`StateRestorationTester` simula salvar e restaurar o estado da Activity:

```kotlin
package com.br.themereveal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeRevealStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun toggle_inverteOTemaEGuardaAOrigem() {
        val state = ThemeRevealState(initialDarkTheme = false)

        state.toggle(fromCenterInRoot = Offset(10f, 20f))

        assertTrue(state.isDarkTheme)
        assertEquals(Offset(10f, 20f), state.origin)
    }

    @Test
    fun rememberThemeRevealState_sobreviveARecriacaoDaTela() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var state: ThemeRevealState
        restorationTester.setContent {
            state = rememberThemeRevealState(initialDarkTheme = false)
        }

        composeRule.runOnIdle { state.toggle(Offset(5f, 5f)) }
        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertTrue(state.isDarkTheme)
            // A origem não é salva: depois de recriar a tela ela não faz sentido.
            assertFalse(state.origin.isSpecified)
        }
    }
}
```

Rode:

```bash
./gradlew :themereveal:testDebugUnitTest
```

Resultado na validação: `RevealGeometryTest` 7/7, `ThemeRevealHostTest` 3/3,
`ThemeRevealStateTest` 2/2.

### 11.4 Testes de pixels (instrumentados)

O `CircularRevealThemeWrapperUiTest` pode ir para `themereveal/src/androidTest/...`
trocando a chamada do componente:

```kotlin
// No topo do arquivo de teste:
private val LocalTestBackground = staticCompositionLocalOf { Color.Unspecified }

// Dentro do setContent:
ThemeRevealHost(
    isDarkTheme = darkTheme,
    theme = { dark, content ->
        // Um tema mínimo que só define a cor de fundo lida nos pixels.
        CompositionLocalProvider(LocalTestBackground provides if (dark) Color.Black else Color.White) {
            content()
        }
    },
    modifier = Modifier.padding(WRAPPER_OFFSET).size(WRAPPER_SIZE).testTag(WRAPPER_TAG),
    revealOrigin = revealCenter,
) {
    Box(Modifier.fillMaxSize().background(LocalTestBackground.current))
}
```

O teste `cliqueNoBotaoDoConteudo_trocaOTemaComReveal` usa o `StatelessThemeToggleButton`,
que é do app: ele **fica no app**, como teste de integração.

> **Não executado na validação:** a configuração do `androidTest` da biblioteca foi
> conferida (o manifest do APK de teste contém a `ComponentActivity`), mas os testes de
> pixels adaptados acima não foram rodados no emulador.

---

## Passo 12 — Migrar o app para usar o módulo

1. Em `app/build.gradle.kts`:
   ```kotlin
   implementation(project(":themereveal"))
   ```
2. Troque `CircularRevealThemeWrapper(...)` por `ThemeRevealHost(...)` nas telas (exemplo
   completo no Passo 10) e `SystemBarsAppearanceEffect` por `ThemeRevealSystemBarsEffect`.
3. Reescreva o `StatefulCircularRevealThemeToggle` com `rememberThemeRevealState()` (Passo 8)
   — ou mantenha a versão atual como material de estudo.
4. Apague do app o que foi para a biblioteca: `CircularRevealThemeWrapper.kt`,
   `SystemBarsAppearanceEffect.kt` e `CircularRevealMathTest.kt` (que virou
   `RevealGeometryTest`). Os testes do app que usam o wrapper passam a usar o host.
5. `THEME_ANIMATION_DURATION_MS` continua no app só se o `AnimatedAppTheme` ainda o usar;
   para sincronizar com o reveal, use `ThemeRevealDefaults.DurationMillis`.
6. Rode `./gradlew :app:testDebugUnitTest :themereveal:testDebugUnitTest`.

> Dica: faça a migração **antes** de apagar os arquivos antigos. Com os dois componentes
> convivendo, dá para comparar lado a lado na mesma tela que o comportamento é igual.

---

## Passo 13 — Usar em OUTRO projeto (3 caminhos)

### Caminho A — Copiar o módulo (ou `git submodule`)

O mais simples: copie a pasta `themereveal/` para o outro projeto, adicione
`include(":themereveal")` e as entradas do catálogo (Passo 1.1). Bom para experimentar;
ruim para manter, porque cada cópia evolui separada.

### Caminho B — Composite build (código-fonte, sem publicar)

No `settings.gradle.kts` do **outro** projeto:

```kotlin
includeBuild("../SwitchDarkAndLightTheme") {
    dependencySubstitution {
        substitute(module("com.br:themereveal")).using(project(":themereveal"))
    }
}
```

E no módulo que usa:

```kotlin
implementation("com.br:themereveal")
```

O Gradle troca a coordenada Maven pelo projeto local: uma alteração na biblioteca aparece
no outro app no próximo build, sem publicar nada. Ótimo para desenvolver a biblioteca e
um app ao mesmo tempo.

> **Não executado na validação.** Os dois projetos precisam de versões compatíveis de AGP
> e Kotlin.

### Caminho C — Publicar como artefato Maven

Acrescente ao `themereveal/build.gradle.kts` (validado):

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    // ... tudo do Passo 1.2 ...
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.br"
            artifactId = "themereveal"
            version = "0.1.0"
            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
```

Atenção aos dois blocos `publishing`: o de **dentro** do `android { }` é do AGP e diz
**qual variante** vira componente publicável (só a `release`, com o código-fonte junto
para a IDE mostrar o código e o KDoc). O de **fora** é do `maven-publish` e diz **como**
publicar (coordenadas e repositórios). O `afterEvaluate` é necessário porque o
componente `release` só existe depois que o AGP configura as variantes.

**Publicar no repositório Maven local** (`~/.m2`):

```bash
./gradlew :themereveal:publishToMavenLocal
```

No outro projeto:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()   // só para desenvolvimento
    }
}

// app/build.gradle.kts
implementation("com.br:themereveal:0.1.0")
```

O que é publicado (conferido na validação): `themereveal-0.1.0.aar`,
`themereveal-0.1.0-sources.jar`, `themereveal-0.1.0.pom` e `themereveal-0.1.0.module`.
No `.pom`, `ui` e `animation-core` aparecem com escopo `compile` (eram `api`) e
`foundation` e `activity-compose` com escopo `runtime` (eram `implementation`) —
exatamente a tabela do Passo 1.3 materializada.

> **Dica:** para inspecionar o que seria publicado sem tocar no `~/.m2`, declare um
> repositório dentro do `build/`:
> ```kotlin
> publishing {
>     repositories {
>         maven {
>             name = "buildDir"
>             url = uri(layout.buildDirectory.dir("repo"))
>         }
>     }
> }
> ```
> e rode `./gradlew :themereveal:publishReleasePublicationToBuildDirRepository`. Foi
> assim que a armadilha do BOM foi encontrada: abrindo o `.module` gerado
> ([Apêndice A.1](#a1-dependências-api-publicadas-sem-versão-o-bom-precisa-ser-api)).

**Para compartilhar com outras pessoas:** troque o repositório por um remoto — GitHub
Packages (repositório Maven com autenticação por token) ou JitPack (gera o artefato a
partir de uma tag do Git). Ambos seguem a mesma ideia; não foram testados aqui.

---

## Passo 14 — Checklist de qualidade de biblioteca

- [ ] **Nada do app no módulo**: nenhum import de `com.br.switchdarkandlighttheme`,
      nenhuma cor, string ou anotação de preview do app.
- [ ] **`explicitApi()`** ligado; tudo que não é para o consumidor é `internal` ou `private`.
- [ ] **`api` só para tipos que aparecem na API pública**; o resto `implementation`.
- [ ] **BOM como `api(platform(...))`** (ou versões explícitas) — confira o `.module` publicado.
- [ ] **KDoc em todo `public`**, incluindo os contratos que o compilador não verifica
      (tema estático, conteúdo opaco).
- [ ] **Valores padrão num `...Defaults`**, não em constantes soltas.
- [ ] **Sem efeitos colaterais escondidos**: a biblioteca não muda a janela, as barras ou
      o tema global sem o app pedir.
- [ ] **Testes no módulo** — inclusive um que use um tema que não é Material.
- [ ] **Versão semântica** (`0.1.0` → `0.2.0` quando quebrar a API enquanto estiver em
      `0.x`; `1.0.0` quando estabilizar) e um `CHANGELOG.md`.
- [ ] **README** curto com: dependência, exemplo mínimo, os dois contratos do slot e um GIF.
- [ ] **R8/ProGuard**: este módulo não usa reflexão, então não precisa de
      `consumer-rules.pro`. Se um dia precisar, use `consumerProguardFiles(...)` — as regras
      viajam dentro do `.aar` para o app.
- [ ] (próximo nível) **Validação de ABI**: ferramentas como o *binary-compatibility-validator*
      geram um arquivo `.api` com a API pública e falham o build se ela mudar sem querer.
      Não configurado neste guia.

---

## Apêndice A — Problemas encontrados na validação

Os dois problemas abaixo **não aparecem no build da biblioteca**: ela compila, os testes
passam e a publicação funciona. Eles só aparecem do lado de **quem consome**. Por isso,
ao escrever uma biblioteca, não basta ela "buildar": é preciso olhar o que ela entrega.

### A.1 Dependências `api` publicadas sem versão (o BOM precisa ser `api`)

**Configuração que causa o problema:**

```kotlin
dependencies {
    implementation(platform(libs.androidx.compose.bom))   // ← BOM como implementation
    api(libs.androidx.compose.ui)                          // ← sem versão: vem do BOM
    api(libs.androidx.compose.animation.core)              // ← sem versão: vem do BOM
    implementation(libs.androidx.compose.foundation)
}
```

É exatamente a forma do `app/build.gradle.kts` — e lá está certo. O problema só existe
quando o módulo é **publicado**.

**Sintoma.** O build da biblioteca passa, e o `.pom` até parece correto (ele importa o
BOM num `<dependencyManagement>`). Mas o arquivo `.module` — que é o que o **Gradle** lê
quando um projeto consome a biblioteca — tem duas variantes, e na de compilação as
versões somem:

```
releaseVariantReleaseApiPublication       ← usada para COMPILAR o consumidor
  ui                 (sem versão)
  animation-core     (sem versão)
  kotlin-stdlib      2.4.21

releaseVariantReleaseRuntimePublication   ← usada para RODAR o consumidor
  compose-bom        2026.11.01  (platform)
  foundation         (sem versão)
  activity-compose   1.13.0
  ui                 (sem versão)
  animation-core     (sem versão)
  kotlin-stdlib      2.4.21
```

**Consequência.** Um app que já usa o BOM do Compose não percebe nada: o BOM dele
preenche as versões. Um app que **não** usa o BOM recebe `androidx.compose.ui:ui` sem
versão para compilar e o Gradle não tem como resolver a dependência. (Esse erro no
consumidor é a consequência direta do `.module` acima; ele não foi reproduzido num
projeto consumidor separado.)

**Causa.** O Gradle separa o que um módulo expõe em duas "variantes":

| Configuração | Vai para a variante de compilação (API)? | Vai para a de runtime? |
|---|---|---|
| `api(...)` | sim | sim |
| `implementation(...)` | **não** | sim |

O `platform(bom)` não é uma biblioteca: é um conjunto de **restrições de versão**
("se aparecer `ui`, use 1.13.0"). Declarado como `implementation`, ele vale só no
runtime. As dependências `api` sem versão vão para a variante de compilação sem ninguém
para dizer qual versão usar.

**O que NÃO resolveu.** A solução clássica do `maven-publish` é publicar as versões
resolvidas:

```kotlin
register<MavenPublication>("release") {
    versionMapping {
        allVariants { fromResolutionResult() }
    }
    // ...
}
```

Com o AGP 9.5.2, o `.module` gerado ficou **idêntico**: as dependências continuaram sem
versão.

**Correção.** Declarar o BOM como `api`:

```kotlin
dependencies {
    api(platform(libs.androidx.compose.bom))   // ← agora vale também para compilar
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.foundation)
}
```

Resultado no `.module`:

```
releaseVariantReleaseApiPublication
  compose-bom        2026.11.01  (platform)   ← apareceu aqui
  ui                 (sem versão — resolvida pelo BOM acima)
  animation-core     (sem versão — resolvida pelo BOM acima)
  kotlin-stdlib      2.4.21
```

**Efeito colateral (e por que é aceitável).** Com o BOM em `api`, o consumidor passa a
receber as restrições do BOM 2026.11.01. O Gradle sempre escolhe a **maior** versão
pedida: se o app usar um BOM mais novo, o dele vence; se usar um mais antigo, as
bibliotecas do Compose sobem para as versões do BOM da biblioteca. Na prática, a
biblioteca passa a exigir "Compose **pelo menos** 2026.11.01". Isso é honesto: ela foi
compilada e testada com essas versões. Se quiser exigir menos, compile a biblioteca
contra um BOM mais antigo.

**Alternativa equivalente:** dar versões explícitas às dependências `api` no catálogo,
sem BOM. Funciona, mas obriga a atualizar cada versão à mão.

**Como conferir no seu build:**

```bash
./gradlew :themereveal:publishReleasePublicationToBuildDirRepository
python3 -c '
import json
m = json.load(open("themereveal/build/repo/com/br/themereveal/0.1.0/themereveal-0.1.0.module"))
for v in m["variants"]:
    print(v["name"])
    for d in v.get("dependencies", []):
        print("   ", d["module"], d.get("version", {}).get("requires", "(sem versão)"))
'
```

Na variante `...ApiPublication`, toda dependência sem versão precisa de um
`compose-bom` (ou outra `platform`) na mesma lista.

> **Lição:** `api`/`implementation` não decide só "quem enxerga a classe". Decide também
> o que vai em cada variante publicada, e isso inclui as restrições de versão.

---

### A.2 A Activity de teste vaza para o app que consome a biblioteca

**Contexto.** `createComposeRule()` abre uma `androidx.activity.ComponentActivity`
vazia para hospedar o conteúdo do teste. Toda Activity precisa estar declarada num
manifest, e quem faz isso é o artefato `ui-test-manifest`: ele é só um
`AndroidManifest.xml` com essa Activity.

No app, a recomendação oficial é:

```kotlin
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

e no app isso está certo. Os testes rodam contra a variante debug, e a Activity extra só
existe no APK debug, que você mesmo instala.

**Configuração que causa o problema:** copiar essa linha para a **biblioteca**.

**Sintoma.** Nada quebra. Os testes da biblioteca passam, e o build do app também. O
problema só aparece abrindo o manifest mesclado do **app**:

```xml
<!-- app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml -->
<activity
    android:name="androidx.activity.ComponentActivity"
    android:exported="true"
    android:theme="@android:style/Theme.Material.Light.NoActionBar" />
```

O app nunca declarou essa Activity. Ela veio da biblioteca.

**Causa.** Para quem consome, `implementation` (e as variações `debugImplementation`,
`releaseImplementation`) esconde a dependência da **compilação**, mas não do **runtime**:
o código e o manifest da dependência vão juntos para o APK do app. Quando o app depende
de `project(":themereveal")`, o build debug do app usa a variante debug da biblioteca, e
com ela o `ui-test-manifest`.

**Alcance (o que foi verificado):**

| Cenário | A Activity aparece no app? |
|---|---|
| App em **debug** com `implementation(project(":themereveal"))` | **sim**, exportada |
| App em **release** | não |
| App consumindo o artefato **publicado** (`release`) | não: o `.pom` publicado não lista o `ui-test-manifest` |

Ou seja, o vazamento atinge principalmente quem usa a biblioteca **como módulo ou
composite build** (Caminhos A e B do Passo 13), que é justamente o jeito mais comum de
desenvolver uma biblioteca junto com um app.

**Por que é um problema, mesmo só em debug:**

- **`android:exported="true"`**: qualquer app no aparelho consegue abrir essa Activity
  no build debug do consumidor.
- **Surpresa no manifest**: quem audita o manifest do app encontra uma Activity que
  ninguém declarou.
- **Conflitos de merge**: se o app declarar a própria `ComponentActivity` com outros
  atributos, o merge de manifests pode falhar ou escolher valores inesperados.
- **Princípio**: uma dependência que só a biblioteca usa nos testes não deve chegar a
  quem consome.

**Correção.** Colocar o `ui-test-manifest` só nos escopos de teste:

```kotlin
dependencies {
    // Robolectric (testes de unidade na JVM)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    // Testes instrumentados (APK de teste)
    androidTestImplementation(libs.androidx.compose.ui.test.manifest)
}
```

Por que funciona:

- **`testImplementation`**: com `isIncludeAndroidResources = true`, o manifest usado pelo
  Robolectric inclui as dependências de teste. Os 12 testes da biblioteca passaram assim.
- **`androidTestImplementation`**: numa biblioteca, os testes instrumentados rodam num
  APK de teste próprio, e o manifest desse APK recebe a Activity (conferido no manifest
  mesclado de `debugAndroidTest`).
- **Nenhum dos dois** faz parte do que a biblioteca expõe ao consumidor. Com essa
  configuração, o manifest debug do app ficou sem a `ComponentActivity`.

> **E no `:app`?** Mantenha `debugImplementation` lá: o app é o produto final, não é
> consumido por ninguém, e essa é a configuração que a documentação do Compose recomenda.

**Como conferir no seu build:**

```bash
./gradlew :app:processDebugMainManifest
grep -n -A3 "androidx.activity.ComponentActivity" \
  app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml
```

Para o teste ser conclusivo, o próprio `:app` não pode declarar o `ui-test-manifest`
(senão a Activity aparece por causa dele). Remova a linha temporariamente, ou use a aba
*Merged Manifest* do Android Studio, que mostra de qual dependência veio cada elemento.

> **Lição:** numa biblioteca, cada dependência é também uma decisão sobre o app de outra
> pessoa. Antes de usar um escopo `debug*`/`release*`, pergunte se aquilo deve chegar ao
> APK de quem consome.

---

## Exercícios

1. **Módulo `:themereveal-material3`.** Crie um segundo módulo, que depende do
   `:themereveal` e do `material3`, com um atalho:
   ```kotlin
   public fun materialRevealTheme(light: ColorScheme, dark: ColorScheme): RevealLayerTheme
   ```
   Pergunta: por que esse atalho não deve ficar no `:themereveal`?
2. **Formas além do círculo.** Junte este guia com o
   `reveal-theme-wrapper-poligonos-passo-a-passo.md`: adicione um parâmetro
   `shape: RevealShape = CircleRevealShape` ao host. Qual parte da API muda? Isso quebra
   quem já usa a versão `0.1.0`?
3. **Persistência reutilizável.** Crie `:themereveal-datastore` com uma classe que exponha
   `Flow<Boolean>` e `suspend fun setDarkTheme(Boolean)`. O `:themereveal` continua sem
   saber que ela existe?
4. **Módulo `:sample`.** Mova as previews e telas de demonstração para um app de exemplo
   separado, que consome a biblioteca como um usuário externo consumiria.
5. **Publicar de verdade.** Publique a `0.1.0` no GitHub Packages e use-a num projeto
   novo criado do zero, sem `mavenLocal()`.
6. **Quebrar e versionar.** Renomeie `revealOrigin` para `origin`. Isso é uma mudança
   compatível? Que versão o próximo release deveria ter? Como você ofereceria uma
   transição suave (dica: `@Deprecated` com `ReplaceWith` numa sobrecarga)?
