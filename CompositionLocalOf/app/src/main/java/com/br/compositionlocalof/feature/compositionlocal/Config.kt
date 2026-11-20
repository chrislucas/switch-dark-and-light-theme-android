package com.br.compositionlocalof.feature.compositionlocal

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
    https://share.google/aimode/rf8AStPDyPGgcgNvg

    Gostaria que eu explique a diferença exata entre staticCompositionLocalOf e compositionLocalOf
    com um exemplo de quando usar cada um
 */


data class AppSpacing(
    val small: Dp = 4.dp,
    val medium: Dp = 8.dp,
    val large: Dp = 16.dp
)