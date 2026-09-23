package com.dan.opencv.vision

import org.opencv.core.Rect

/**
 * Una ficha detectada en el frame actual, en coordenadas de píxel de la imagen analizada
 * (no de la vista en pantalla).
 *
 * @param id identificador estable dentro del frame (índice de contorno).
 * @param type forma geométrica clasificada.
 * @param colorName nombre de color aproximado ("rojo", "azul", ...).
 * @param centerX centroide X en píxeles.
 * @param centerY centroide Y en píxeles.
 * @param boundingBox rectángulo delimitador en píxeles.
 * @param areaPx área del contorno en píxeles cuadrados.
 */
data class DetectedShape(
    val id: Int,
    val type: ShapeType,
    val colorName: String,
    val centerX: Double,
    val centerY: Double,
    val boundingBox: Rect,
    val areaPx: Double
)
