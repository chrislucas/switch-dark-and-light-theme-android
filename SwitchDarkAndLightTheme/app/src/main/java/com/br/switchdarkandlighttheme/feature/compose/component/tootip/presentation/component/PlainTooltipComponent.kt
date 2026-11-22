package com.br.switchdarkandlighttheme.feature.compose.component.tootip

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.br.switchdarkandlighttheme.core.ui.AppIcons

/*
    https://developer.android.com/agents/skills/jetpack-compose/adaptive/references/tooltips
    https://developer.android.com/develop/ui/compose/components/tooltip

    - Use tooltips para adicionar contexto para botoes ou outro elemento UI
    - Ha 2 tipos de Tooltips
        - plain tooltip
            - Descreve elementos ou ações de um Icon Button
         - Rich Tooltips
            - Prove mais detalhes, tais como descrever o valor da funcionalidade. Pode
            incluir um título opcional, um link e botões
 */



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlainTooltipComponent(
    modifier: Modifier = Modifier,
    plainToolTipText: String,
    onClick: () -> Unit = {}
) {
    /*
        https://developer.android.com/develop/ui/compose/components/tooltip#api-surface
        - Usamos TooltipBox para implementar uma tooltip usando as seguintes propriedades
            - positionProvider: posiciona a tooltip relativa ao conteúdo da sua âncora. Usamos
            um provedor de posicao paadrao a partir do objeto TooltipDefaults, ou
            podemos personalizar o posicionamento dela
            - tootip: A composable que contem o conteúdo da tooltip. Usamos geralmente PlainTooltip
            ou RichTooltip
                - PlainTooltip: Para descrever elementos ou acoes de um botão de ícone
                - RichTooltip: Prove mais detalhes, como descrição de uma funcionalidades. Rich
                tooltips  podem incluir um título opcional, link ou botoões
            - state: O gerenciador de estados que contém a logica da UI e o estado do elemento.
            - content: A composable que a tooltip está âncorada

     */
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            TooltipAnchorPosition.Above,
            /**
             *  @see androidx.compose.material3 - SpacingBetweenTooltipAndAnchor = 4.dp
             *  Valor padrão, mas quis testar com outro valor
             */
            spacingBetweenTooltipAndAnchor = 8.dp
        ),
        tooltip = {
            PlainTooltip { Text(plainToolTipText) }
        },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick) {
            Icon(
                painter = rememberVectorPainter(
                    AppIcons.Favorite
                    // Icons.Filled.Favorite
                ),
                contentDescription = "Add To"
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PlainTooltipComponentPreview() {
    PlainTooltipComponent(
        plainToolTipText = "Add to favorites",
        onClick = {}
    )
}