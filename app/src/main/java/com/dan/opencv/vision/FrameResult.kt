package com.dan.opencv.vision

/**
 * Resultado de analizar una imagen: las fichas detectadas ya con su posición relativa
 * inferida, más las dimensiones (en píxeles) de la imagen analizada -para que la UI pueda,
 * si hace falta, mapear coordenadas a la vista de la cámara-. [aviso] es un mensaje para el
 * usuario cuando la foto parece difícil de leer (fondo con poco contraste o con textura).
 */
data class FrameResult(
    val positioned: List<PositionedShape>,
    val frameWidth: Int,
    val frameHeight: Int,
    val aviso: String? = null
)
