package com.br.switchdarkandlighttheme.feature.compose.component.tootip.presentation.screen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.br.switchdarkandlighttheme.feature.compose.component.tootip.PlainTooltipComponent
import com.br.switchdarkandlighttheme.feature.compose.component.tootip.presentation.screen.ui.theme.SwitchDarkAndLightThemeTheme

class SimpleTooltipActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwitchDarkAndLightThemeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PlainTooltipScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Composable
fun PlainTooltipScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        PlainTooltipComponent(
            modifier = Modifier,
            plainToolTipText = "Plain Tooltip"
        ) {
            Toast.makeText(
                ctx,
                "Plain Tooltip Clicked",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}