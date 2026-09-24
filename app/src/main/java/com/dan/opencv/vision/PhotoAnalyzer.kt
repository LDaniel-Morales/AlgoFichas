package com.dan.opencv.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Mat

/**
 * Analiza una foto ya capturada, una sola vez (no hay detección continua de video en vivo).
 * `Utils.bitmapToMat` entrega un Mat en RGBA de 4 canales, el mismo formato que espera
 * [ShapeDetector.detect].
 */
object PhotoAnalyzer {
    fun analyze(bitmap: Bitmap): FrameResult {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        return try {
            val shapes = ShapeDetector.detect(rgba)
            val positioned = FlowPositionAnalyzer.analyze(shapes)
            FrameResult(positioned, bitmap.width, bitmap.height)
        } finally {
            rgba.release()
        }
    }
}
