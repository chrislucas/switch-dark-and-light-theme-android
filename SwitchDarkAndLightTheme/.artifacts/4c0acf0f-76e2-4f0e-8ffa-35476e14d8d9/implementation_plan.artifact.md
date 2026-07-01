# Plano de Implementação - Correção do Efeito Circular e Troca de Tema

Análise do código identificou que o `CircularRevealThemeWrapper` possui falhas na gestão de estado e na lógica de animação, impedindo que o efeito seja visível.

## Problemas Identificados

1.  **Estado Estático**: A variável `isDarkTheme` dentro do wrapper é inicializada com o parâmetro `darkTheme`, mas nunca é atualizada quando o parâmetro muda. Isso impede que o `LaunchedEffect` seja disparado.
2.  **Sobreposição de Temas Idênticos**: No início da animação, o `underlyingDarkTheme` (tema de fundo) é atualizado imediatamente para o novo tema. Como a camada de revelação (círculo) também usa o novo tema, não há contraste entre o "velho" e o "novo", tornando o efeito invisível.
3.  **Conflito de Animações**: O `AnimatedAppTheme` já realiza uma transição suave de cores (500ms). Ao usá-lo dentro do wrapper circular (650ms), as duas animações ocorrem simultaneamente, o que pode suavizar demais o corte seco esperado do efeito circular.

## Mudanças Propostas

### [Componente]

#### [MODIFY] [CircularRevealThemeWrapper.kt](file:///Users/christoffer/Documents/android-interviews/SwitchDarkAndLightTheme/app/src/main/java/com/br/switchdarkandlighttheme/feature/switchthemeeffect/presentation/component/CircularRevealThemeWrapper.kt)

- **Correção da Lógica de Estado**:
    - Remover `isDarkTheme` local redundante ou sincronizá-lo corretamente.
    - Usar `side-effects` para detectar a mudança no parâmetro `darkTheme`.
    - Manter o tema "anterior" na camada de baixo até que o círculo cubra toda a tela.
- **Melhoria do Preview**:
    - Adicionar um botão de toggle no preview para permitir testar a animação interativamente.
- **Refinamento da Animação**:
    - Garantir que `underlyingDarkTheme` só mude após o início da animação ou que a lógica de camadas respeite o tema antigo vs novo.

## Plano de Verificação

### Testes Manuais
- Executar o Preview interativo no Android Studio.
- Clicar no botão de alternância e verificar se:
    1.  O círculo se expande a partir do centro (ou ponto definido).
    2.  O tema muda visualmente seguindo a expansão do círculo.
    3.  Não há "piscadas" ou estados inconsistentes no final da animação.
