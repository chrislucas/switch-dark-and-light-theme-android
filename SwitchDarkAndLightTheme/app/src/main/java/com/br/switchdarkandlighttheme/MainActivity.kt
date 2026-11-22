package com.br.switchdarkandlighttheme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.br.switchdarkandlighttheme.ui.theme.SwitchDarkAndLightThemeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwitchDarkAndLightThemeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {

    Scaffold(topBar = {
        Text(text = "Hello $name!")
    }, modifier = modifier) { i ->

        Grid(
            config = {
                repeat(2) {
                    column(percentage = .5f)
                }

                repeat(1) {
                    row(percentage = 1f)
                }
            },
            modifier = Modifier.padding(i)
        ) {
            Card() {
                Text(text = "Hello $name!")
            }

            Card() {
                Text(text = "Hello $name!")
            }
        }
    }


}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SwitchDarkAndLightThemeTheme {
        Greeting("Android")
    }
}