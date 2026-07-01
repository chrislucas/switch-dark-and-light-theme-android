package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import com.br.switchdarkandlighttheme.feature.MultiDpiPreviews
import kotlin.math.hypot
import kotlinx.coroutines.flow.first

@Composable
fun CircularRevealThemeWrapper(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val animatedRadius = remember { Animatable(0f) }
    // Armazena o tema que está sendo exibido no fundo (camada base)
    var previousDarkTheme by remember { mutableStateOf(darkTheme) }

    var isRevealing by remember { mutableStateOf(false) }

    val path = remember { Path() }

    val movableContent = remember(content) { movableContentOf { content() } }

    // Dispara a animação sempre que a prop darkTheme mudar
    LaunchedEffect(darkTheme, size) {
        if (darkTheme == previousDarkTheme) {
            return@LaunchedEffect
        }
        snapshotFlow {
            size
        }.first {
            it != IntSize.Zero
        } // Espera até que o tamanho seja definido

        isRevealing = true
        animatedRadius.snapTo(0f)
        previousDarkTheme = darkTheme
        animatedRadius.animateTo(
            targetValue = hypot(size.width.toFloat(), size.height.toFloat()),
            animationSpec = tween(durationMillis = THEME_ANIMATION_DURATION_MS)
        )
        isRevealing = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { if (it.size != size) size = it.size }
    ) {
        // Camada 1: Tema Base (Antigo até a animação acabar)
        // Usamos SwitchDarkAndLightThemeTheme diretamente ou AnimatedAppTheme se quisermos animação de cor também
        AnimatedAppTheme(darkTheme = previousDarkTheme) {
            movableContent()
        }

        // Camada 2: Tema Revelado (Novo, recortado pelo círculo)
        if (isRevealing) {
            AnimatedAppTheme(darkTheme = darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            path.rewind()
                            path.addOval(
                                Rect(
                                    center = Offset(
                                        this.size.width * .5f,
                                        this.size.height * .5f
                                    ),
                                    radius = animatedRadius.value
                                )
                            )
                            clipPath(path) {
                                this@drawWithContent.drawContent()
                            }
                        }
                ) {
                    movableContent()
                }
            }
        }
    }
}

@MultiDpiPreviews
@Preview(showBackground = true)
@Composable
fun CircularRevealThemeButtonWrapperPreviewSimpleStateless() {
    var isDarkTheme by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isDarkTheme = !isDarkTheme }
            ) {
                Text("Toggle")
            }
        }
    ) { paddingValues ->
        CircularRevealThemeWrapper(
            modifier = Modifier.padding(paddingValues),
            darkTheme = isDarkTheme
        ) {
            StatelessToggleDarkThemeManagerButton(isDarkTheme = isDarkTheme, {})
        }
    }
}
