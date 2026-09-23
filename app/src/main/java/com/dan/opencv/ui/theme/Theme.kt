package com.dan.opencv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Paleta única y deliberada del diseño de AlgoFichas (fondo crema + verdes). El diseño de
 * origen no define una variante oscura, así que -a diferencia de la plantilla por
 * defecto- este tema no sigue el modo claro/oscuro del sistema ni el color dinámico de
 * Material You: siempre usa esta paleta fija.
 */
private val AlgoFichasColorScheme = lightColorScheme(
    primary = VerdeMedio,
    onPrimary = Fondo,
    primaryContainer = Brote,
    onPrimaryContainer = VerdeFuerte,
    secondary = Carton,
    onSecondary = VerdeFuerte,
    background = Fondo,
    onBackground = VerdeFuerte,
    surface = TarjetaFondo,
    onSurface = VerdeFuerte,
    surfaceVariant = Brote,
    onSurfaceVariant = VerdeFuerte,
    outline = VerdeFuerte
)

@Composable
fun OpencvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AlgoFichasColorScheme,
        typography = Typography,
        content = content
    )
}
