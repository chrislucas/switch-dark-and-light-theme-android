package com.br.switchdarkandlighttheme.feature.compose.foundation.grid

import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/*
    https://developer.android.com/develop/ui/compose/layouts/adaptive/grid/get-started
 */


@Preview(showBackground = true)
@Composable
fun GridComponent() {
    Grid(config = {
        repeat(2) {
            column(percentage = .5f)
        }

        repeat(3) {
            row(percentage = 1f)
        }
    }) {
        CardComponent(content = "Card 1")
        CardComponent(content = "Card 2")
        CardComponent(content = "Card 3")
        CardComponent(content = "Card 4")
        CardComponent(content = "Card 5")
        CardComponent(content = "Card 6")
    }
}


@Composable
private fun CardComponent(
    content: String,
    onClick: () -> Unit = {}
) {
    Card(shape = CircleShape, modifier = Modifier.fillMaxSize()) {
        Text(text = content)
    }
}