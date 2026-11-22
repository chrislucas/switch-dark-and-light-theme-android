package com.br.switchdarkandlighttheme.core.ui

import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "HDPI",
    group = "DPI",
    device = "spec:width=360dp,height=640dp,dpi=240",
    showSystemUi = true,
    showBackground = true
)
@Preview(
    name = "XHDPI",
    group = "DPI",
    device = "spec:width=360dp,height=640dp,dpi=320",
    showSystemUi = true,
    showBackground = true
)
@Preview(
    name = "XXHDPI",
    group = "DPI",
    device = "spec:width=360dp,height=640dp,dpi=480",
    showSystemUi = true,
    showBackground = true
)
@Preview(
    name = "XXXHDPI",
    group = "DPI",
    device = "spec:width=360dp,height=640dp,dpi=640",
    showSystemUi = true,
    showBackground = true
)
annotation class MultiDpiPreviews
