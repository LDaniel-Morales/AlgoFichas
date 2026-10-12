package com.dan.opencv.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * Analiza una foto ya capturada, una sola vez (no hay detección continua de video en vivo).
 *
 * `ImageCapture` entrega la foto a resolución de sensor completa (varios megapíxeles), muy
 * por encima de la resolución de los frames de análisis que se usaban antes con
 * `ImageAnalysis`. Los parámetros de [ShapeDetector] (kernel de blur, umbrales de Canny, área
 * mínima) están en píxeles/proporciones pensados para esa resolución más baja, así que la
 * foto se reduce primero a una resolución de trabajo consistente -esto también hace el
 * análisis bastante más rápido que procesar la foto a resolución completa-.
 */
object PhotoAnalyzer {

    /** Dimensión máxima (en px) del lado más largo antes de analizar. */
    private const val MAX_WORKING_DIMENSION = 1280

    fun analyze(bitmap: Bitmap): FrameResult {
        val working = downscale(bitmap, MAX_WORKING_DIMENSION)
        val rgba = Mat()
        Utils.bitmapToMat(working, rgba)
        return try {
            val deteccion = ShapeDetector.detect(rgba)
            val positioned = FlowPositionAnalyzer.analyze(deteccion.shapes)
            // Más piezas rotas que fichas enteras: el fondo probablemente está confundiendo al detector.
            val aviso = if (deteccion.shapes.isNotEmpty() && deteccion.fragmentosDescartados > deteccion.shapes.size) {
                "El fondo dificulta ver algunas fichas. Si falta alguna, prueba sobre una superficie lisa de otro color."
            } else null
            FrameResult(positioned, working.width, working.height, aviso)
        } finally {
            rgba.release()
            if (working !== bitmap) working.recycle()
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
