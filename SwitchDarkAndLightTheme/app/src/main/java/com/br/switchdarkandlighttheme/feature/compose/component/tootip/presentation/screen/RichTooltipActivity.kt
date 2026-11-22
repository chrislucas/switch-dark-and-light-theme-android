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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.br.switchdarkandlighttheme.feature.compose.component.tootip.RichTooltipComponent
import com.br.switchdarkandlighttheme.feature.compose.component.tootip.presentation.screen.ui.theme.SwitchDarkAndLightThemeTheme

class RichTooltipActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwitchDarkAndLightThemeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RichToolTipScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun RichToolTipScreen(modifier: Modifier) {
    val ctx = LocalContext.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        RichTooltipComponent(
            modifier = Modifier,
            richTooltipSubHeadText = {
                Text(text = "Rich Tooltip Sub Head")
            },
            richTooltipText = {
                Text(text = "Rich Tooltip Text")
            }
        ) {
            Toast.makeText(
                ctx,
                "Rich Tooltip Clicked",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RichToolTipScreenPreview() {
    SwitchDarkAndLightThemeTheme {
        RichToolTipScreen(modifier = Modifier)
    }
}