package com.dan.opencv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.dan.opencv.retos.FichaTipo
import com.dan.opencv.ui.theme.Carton
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.vision.ShapeType

/**
 * Un paralelogramo (Compose no tiene "skew" como CSS, así que se arma el contorno a mano).
 * Se usa para la ficha de "Entrada" (Leer dato).
 */
private class ParallelogramShape(private val skewFraction: Float = 0.32f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val skew = size.width * skewFraction
        val path = Path().apply {
            moveTo(skew, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - skew, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Ícono pequeño de la forma de una ficha, igual al mapa `FORMA` del diseño de origen. */
@Composable
fun FichaShapeIcon(tipo: FichaTipo, modifier: Modifier = Modifier) {
    when (tipo) {
        FichaTipo.TERMINAL -> Box(
            modifier
                .size(width = 26.dp, height = 14.dp)
                .clip(RoundedCornerShape(50))
                .background(Carton)
                .border(2.dp, VerdeFuerte, RoundedCornerShape(50))
        )
        FichaTipo.ENTRADA -> Box(
            modifier
                .size(width = 24.dp, height = 14.dp)
                .clip(ParallelogramShape())
                .background(Carton)
                .border(2.dp, VerdeFuerte, ParallelogramShape())
        )
        FichaTipo.PROCESO -> Box(
            modifier
                .size(width = 24.dp, height = 16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Carton)
                .border(2.dp, VerdeFuerte, RoundedCornerShape(3.dp))
        )
        FichaTipo.DECISION -> Box(
            modifier
                .size(15.dp)
                .graphicsLayer(rotationZ = 45f)
                .background(Carton)
                .border(2.dp, VerdeFuerte)
        )
        FichaTipo.SALIDA -> Box(
            modifier
                .size(width = 24.dp, height = 16.dp)
                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 10.dp, bottomEnd = 4.dp))
                .background(TarjetaFondo)
                .border(2.dp, VerdeFuerte, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 10.dp, bottomEnd = 4.dp))
        )
    }
}

/**
 * Traduce una forma detectada por la cámara ([ShapeType], el vocabulario completo de 11+
 * fichas) al vocabulario simplificado de 5 tipos que usan los ejercicios ([FichaTipo]).
 * Devuelve null para formas sin equivalente directo (conectores, óvalo, triángulo, etc.).
 */
fun ShapeType.toFichaTipoOrNull(): FichaTipo? = when (this) {
    ShapeType.STADIUM -> FichaTipo.TERMINAL
    ShapeType.PARALLELOGRAM, ShapeType.KEYBOARD_INPUT -> FichaTipo.ENTRADA
    ShapeType.RECTANGLE, ShapeType.SQUARE, ShapeType.PREDEFINED_PROCESS -> FichaTipo.PROCESO
    ShapeType.DIAMOND -> FichaTipo.DECISION
    ShapeType.DOCUMENT -> FichaTipo.SALIDA
    else -> null
}
