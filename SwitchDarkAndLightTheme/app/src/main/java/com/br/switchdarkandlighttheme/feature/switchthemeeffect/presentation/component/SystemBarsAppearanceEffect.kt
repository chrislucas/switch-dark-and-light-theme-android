package com.br.switchdarkandlighttheme.feature.switchthemeeffect.presentation.component

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Faz os ícones da status bar e da navigation bar seguirem o tema do APP, e não
 * o do sistema: ícones claros quando [isDarkTheme] é `true`, escuros quando é `false`.
 *
 * Necessário quando a tela é edge-to-edge e o fundo do tema é desenhado atrás das
 * barras do sistema. Sem isso, com o app claro e o sistema escuro (ou o contrário),
 * os ícones ficariam com a mesma cor do fundo e sumiriam.
 *
 * Chamar `enableEdgeToEdge` de novo é seguro: ele só atualiza o estilo das barras.
 * As barras ficam transparentes (scrim transparente) para o fundo do app aparecer.
 *
 * Deve ser chamado por quem é DONO do estado do tema (só ele sabe o tema atual).
 * Em previews não há Activity, então o efeito não faz nada.
 */
@Composable
fun SystemBarsAppearanceEffect(isDarkTheme: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, isDarkTheme) {
        val barStyle = SystemBarStyle.auto(
            lightScrim = Color.TRANSPARENT,
            darkScrim = Color.TRANSPARENT,
            detectDarkMode = { isDarkTheme }
        )
        activity.enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
    }
}
