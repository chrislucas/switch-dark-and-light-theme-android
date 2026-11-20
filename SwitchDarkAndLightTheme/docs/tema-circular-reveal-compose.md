# Guia Completo: Tema Animado com Circular Reveal, DataStore e ViewModel no Jetpack Compose

Este documento reúne todas as explicações, decisões técnicas e códigos discutidos nesta conversa para implementar um sistema completo de troca de tema no Jetpack Compose:

1. Atualização do `ColorScheme` para a API moderna (`.copy()`).
2. Diferença entre `android.graphics.Path` e `androidx.compose.ui.graphics.Path` (`addCircle` vs `addOval`).
3. Implementação da animação de revelação circular (`CircularRevealThemeWrapper`).
4. Persistência da preferência de tema usando **Jetpack Preferences DataStore**.
5. Arquitetura recomendada com **ViewModel** e **DSL moderna (`viewModelFactory` + `initializer`)** sem bibliotecas externas de Injeção de Dependência.

---

## 1. Dependências Necessárias (`build.gradle.kts`)

Certifique-se de incluir as dependências abaixo no módulo do aplicativo (`app/build.gradle.kts`) para suporte ao DataStore, `collectAsStateWithLifecycle` e `viewModel()` no Compose:

```kotlin
dependencies {
    // Jetpack Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ViewModel e integração com Compose + Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
}
```

---

## 2. Atualização do `ColorScheme` (`AnimatedAppTheme`)

O construtor direto da classe `ColorScheme` foi marcado como obsoleto (*deprecated*) na API do Material 3. A recomendação oficial da documentação é clonar um esquema existente usando `.copy()` e animar apenas os tokens necessários com `animateColorAsState`.

### Por que essa abordagem é melhor?
* **Aderência à Documentação:** Elimina o aviso de código obsoleto e garante compatibilidade com versões futuras do Compose.
* **Código Limpo:** Evita declarar manualmente os ~30 parâmetros do construtor; os tokens omitidos herdam automaticamente os valores de `targetColors`.

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AnimatedAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    // 1. Seleciona a paleta estática alvo (DarkColors ou LightColors)
    val targetColors = if (darkTheme) DarkColors else LightColors

    // 2. Define a especificação da animação
    val animationSpec = tween<Color>(durationMillis = 500)

    // 3. Aplica o .copy() animando os tokens de cor necessários
    val animatedColorScheme = targetColors.copy(
        primary = animateColorAsState(targetColors.primary, animationSpec, label = "primary").value,
        onPrimary = animateColorAsState(targetColors.onPrimary, animationSpec, label = "onPrimary").value,
        background = animateColorAsState(targetColors.background, animationSpec, label = "background").value,
        onBackground = animateColorAsState(targetColors.onBackground, animationSpec, label = "onBackground").value,
        surface = animateColorAsState(targetColors.surface, animationSpec, label = "surface").value,
        onSurface = animateColorAsState(targetColors.onSurface, animationSpec, label = "onSurface").value
        // Adicione outros tokens (como primaryContainer, outline, etc.) se necessário
    )

    MaterialTheme(
        colorScheme = animatedColorScheme,
        content = content
    )
}
```

---

## 3. Nota Técnica: `addCircle` vs `addOval` no `Path`

* **Android Nativo Tradicional (`android.graphics.Path`):** Possui o método `addCircle(x, y, radius, dir)`.
* **Jetpack Compose (`androidx.compose.ui.graphics.Path`):** Não possui `addCircle`. Para criar um círculo em um `Path` no Compose, utiliza-se **`addOval`** passando um `Rect` definido por um centro (`Offset`) e um raio (`radius`).

---

## 4. Efeito de Revelação Circular (`CircularRevealThemeWrapper`)

Para que o novo tema seja revelado por meio de um círculo expansivo:
1. A camada inferior renderiza continuamente o tema anterior (`previousDarkTheme`).
2. Enquanto `animatedRadius.value > 0f`, a camada superior renderiza o novo tema (`currentDarkTheme`) recortado dinamicamente pelo `Path` circular via `clipPath`.

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot

@Composable
fun CircularRevealThemeWrapper(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val animatedRadius = remember { Animatable(0f) }

    var previousDarkTheme by remember { mutableStateOf(darkTheme) }
    var currentDarkTheme by remember { mutableStateOf(darkTheme) }

    val widthF = size.width.toFloat()
    val heightF = size.height.toFloat()

    // Ponto de origem da animação (canto inferior direito; use widthF/2f e heightF/2f para o centro)
    val centerX = widthF
    val centerY = heightF

    LaunchedEffect(darkTheme) {
        if (darkTheme != previousDarkTheme) {
            currentDarkTheme = darkTheme
            val maxRadius = hypot(widthF, heightF)

            animatedRadius.snapTo(0f)
            animatedRadius.animateTo(
                targetValue = maxRadius,
                animationSpec = tween(durationMillis = 650)
            )
            previousDarkTheme = darkTheme
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { size = it.size }
    ) {
        // 1. Camada de Fundo: Renderiza sempre o tema ANTERIOR
        AnimatedAppTheme(darkTheme = previousDarkTheme) {
            content()
        }

        // 2. Camada Superior: Recorta e revela o NOVO tema em formato de círculo
        if (animatedRadius.value > 0f && size != IntSize.Zero) {
            AnimatedAppTheme(darkTheme = currentDarkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val circlePath = Path().apply {
                                addOval(
                                    Rect(
                                        center = Offset(centerX, centerY),
                                        radius = animatedRadius.value
                                    )
                                )
                            }

                            clipPath(path = circlePath) {
                                this@drawWithContent.drawContent()
                            }
                        }
                ) {
                    content()
                }
            }
        }
    }
}
```

---

## 5. Persistência com Preferences DataStore (`ThemePreferenceManager`)

Classe responsável por encapsular a leitura (`Flow<Boolean>`) e a gravação (`suspend fun`) da preferência de tema no disco.

```kotlin
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val THEME_PREFERENCE_KEY = "theme_preference"
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = THEME_PREFERENCE_KEY)

class ThemePreferenceManager(private val context: Context) {
    companion object {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }

    suspend fun saveThemePreference(isDarkMode: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_THEME] = isDarkMode
        }
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[IS_DARK_THEME] ?: false
    }
}
```

---

## 6. Arquitetura com ViewModel e DSL Moderna (`viewModelFactory`)

Em vez de criar uma subclasse boilerplate de `ViewModelProvider.Factory` com verificações `isAssignableFrom`, utilizamos a DSL moderna `viewModelFactory { initializer { ... } }`:

* **Separação de Conceitos:** A UI apenas exibe o estado e delega ações ao `ViewModel`.
* **Sobrevivência à Rotação:** O estado é mantido em memória durante mudanças de configuração.
* **Gerenciamento Seguro de Corrotinas:** As operações de escrita rodam no `viewModelScope`.

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val themePreferenceManager: ThemePreferenceManager) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean> = themePreferenceManager.isDarkTheme
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    fun toggleTheme() {
        viewModelScope.launch {
            themePreferenceManager.saveThemePreference(!isDarkTheme.value)
        }
    }

    companion object {
        fun provideFactory(themePreferenceManager: ThemePreferenceManager): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ThemeViewModel(themePreferenceManager = themePreferenceManager)
                }
            }
    }
}
```

---

## 7. Tela Integrada (`ToggleThemeScreen`)

Conecta o `ThemeViewModel` à interface utilizando `collectAsStateWithLifecycle()` e `applicationContext` (para evitar *memory leaks*).

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ToggleThemeScreen() {
    val context = LocalContext.current.applicationContext
    val manager = remember { ThemePreferenceManager(context) }

    val viewModel: ThemeViewModel = viewModel(
        factory = ThemeViewModel.provideFactory(manager)
    )

    val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    CircularRevealThemeWrapper(darkTheme = isDarkTheme) {
        Surface(
            modifier = Modifier
                .safeDrawingPadding()
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isDarkTheme) "Dark Theme" else "Light Theme",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = TextStyle(
                        fontSize = MaterialTheme.typography.headlineLarge.fontSize
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.toggleTheme() },
                    shape = RectangleShape
                ) {
                    Text(
                        text = "Toggle Theme Mode",
                        style = TextStyle(
                            fontSize = MaterialTheme.typography.titleLarge.fontSize
                        )
                    )
                }
            }
        }
    }
}
```
