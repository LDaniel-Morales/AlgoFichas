package com.dan.opencv.vision

/**
 * Resultado de analizar una imagen: las fichas detectadas ya con su posición relativa
 * inferida, más las dimensiones (en píxeles) de la imagen analizada -para que la UI pueda,
 * si hace falta, mapear coordenadas a la vista de la cámara-.
 */
data class FrameResult(
    val positioned: List<PositionedShape>,
    val frameWidth: Int,
    val frameHeight: Int
)
