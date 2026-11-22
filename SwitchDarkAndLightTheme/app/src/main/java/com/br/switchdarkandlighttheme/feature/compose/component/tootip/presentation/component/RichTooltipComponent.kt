package com.br.switchdarkandlighttheme.feature.compose.component.tootip

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.br.switchdarkandlighttheme.core.ui.AppIcons
import com.br.switchdarkandlighttheme.ui.theme.SwitchDarkAndLightThemeTheme

/*
    https://developer.android.com/develop/ui/compose/components/tooltip
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTooltipComponent(
    modifier: Modifier = Modifier,
    richTooltipSubHeadText: @Composable () -> Unit,
    richTooltipText: @Composable () -> Unit,
    onClick: () -> Unit = {}
) {
    /*
        TooltipBox: Lida com a interacao do usuario e atualiza
        o TooltipState de acordo.

        QUando TooltipState indica que a tooltip deve ser mostrda,
        a funcao lambda tooltip executa e a TooltipBox
        mostra o RichTooltip

        A TooltipiBox atua como angora e container de ambos o contéudo
        e a tooltip
            - No exemplo abaixo, o conteúdo é um IconButton. Ele que lida
            o comportamento de TAP.
            - Ao pressionar por um período de tempo longo qualquer parte
            do conteúdo do TooltipBox, a tooltipi é mostrada
     */
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
            spacingBetweenTooltipAndAnchor = 8.dp
        ),
        tooltip = {
            RichTooltip(
                title = richTooltipSubHeadText,
                text = richTooltipText,
                action = {
                    Button(onClick = {}) {
                        Text(text = "Action")
                    }
                }
            )
        },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = AppIcons.Info,
                contentDescription = "Info",
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RichTooltipComponentPreview() {
    SwitchDarkAndLightThemeTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            RichTooltipComponent(
                richTooltipSubHeadText = {
                    Text(text = "Rich Tooltip Sub Head")
                },
                richTooltipText = {
                    Text(text = "Rich Tooltip Text")
                }
            ) {}
        }
    }

}