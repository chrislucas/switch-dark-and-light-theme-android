package com.br.switchdarkandlighttheme.feature.switchthemeeffect.core.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Esquema de cores para o tema CLARO (Light).
 *
 * Cada parâmetro do [lightColorScheme] tem a seguinte função:
 * - [primary]: Cor principal da marca/app. Usada em componentes de destaque como botões,
 *   FABs, switches ativos e indicadores de seleção.
 * - [background]: Cor de fundo das telas/janelas (área "atrás" de todo o conteúdo).
 *   Geralmente aplicada na raiz de uma tela (ex.: Scaffold).
 * - [surface]: Cor de fundo de superfícies elevadas/contêineres como Card, Sheet,
 *   Menu, AppBar e Dialog. Diferencia componentes do [background].
 * - [onPrimary]: Cor do conteúdo (texto/ícones) desenhado sobre [primary].
 *   Deve ter contraste adequado em relação à cor primária.
 * - [onBackground]: Cor do conteúdo (texto/ícones) desenhado sobre [background].
 *   Garante legibilidade do texto principal da tela.
 * - [onSurface]: Cor do conteúdo (texto/ícones) desenhado sobre [surface].
 *   Usada em textos dentro de cards, menus, dialogs, etc.
 */
val LightColors = lightColorScheme(
    primary = Color(0xFF6750A4),      // Roxo principal da marca (Material You)
    background = Color(0xFFFFFFFF),   // Fundo branco da tela
    surface = Color(0xFFFFFFFF),      // Superfícies (cards, sheets) brancas
    onPrimary = Color(0xFFFFFFFF),    // Texto/ícone branco sobre o roxo primário
    onBackground = Color(0xFF1C1B1F), // Texto quase-preto sobre o fundo claro
    onSurface = Color(0xFF1C1B1F)     // Texto quase-preto sobre superfícies claras
)

/**
 * Esquema de cores para o tema ESCURO (Dark).
 *
 * Cada parâmetro do [darkColorScheme] tem a seguinte função:
 * - [primary]: Cor principal da marca/app no tema escuro. Tipicamente uma variação
 *   mais clara da cor primária para manter contraste em fundos escuros.
 *   Usada em botões, FABs, switches ativos e indicadores.
 * - [background]: Cor de fundo das telas/janelas no tema escuro.
 *   Tipicamente um cinza/preto muito escuro para reduzir fadiga visual.
 * - [surface]: Cor de fundo de superfícies elevadas (Card, Sheet, Menu, AppBar, Dialog)
 *   no tema escuro. Pode ser igual ou ligeiramente diferente do [background].
 * - [onPrimary]: Cor do conteúdo (texto/ícones) desenhado sobre [primary].
 *   No tema escuro, geralmente é uma cor escura para contrastar com a [primary] clara.
 * - [onBackground]: Cor do conteúdo (texto/ícones) desenhado sobre [background].
 *   Tipicamente um tom claro para legibilidade sobre fundos escuros.
 * - [onSurface]: Cor do conteúdo (texto/ícones) desenhado sobre [surface].
 *   Usada em textos dentro de cards, menus, dialogs no tema escuro.
 */
val DarkColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),      // Roxo claro (versão clara do primário para tema escuro)
    background = Color(0xFF141218),   // Fundo quase-preto da tela
    surface = Color(0xFF141218),      // Superfícies (cards, sheets) escuras
    onPrimary = Color(0xFF381E72),    // Texto/ícone roxo escuro sobre o primário claro
    onBackground = Color(0xFFE6E1E5), // Texto quase-branco sobre o fundo escuro
    onSurface = Color(0xFFE6E1E5)     // Texto quase-branco sobre superfícies escuras
)