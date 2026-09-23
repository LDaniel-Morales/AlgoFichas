package com.dan.opencv.vision

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer

/**
 * Resultado de analizar un frame: las fichas detectadas ya con su posición relativa
 * inferida, más las dimensiones (en píxeles) del frame analizado -para que la UI pueda,
 * si hace falta, mapear coordenadas a la vista de la cámara-.
 */
data class FrameResult(
    val positioned: List<PositionedShape>,
    val frameWidth: Int,
    val frameHeight: Int
)

/**
 * [ImageAnalysis.Analyzer] que convierte cada frame YUV_420_888 de CameraX a un Mat RGBA
 * de OpenCV, corrige la rotación del sensor y ejecuta el pipeline de detección.
 *
 * Solo lógica: no dibuja nada, únicamente entrega el resultado por [onResult].
 */
class FrameAnalyzer(
    private val onResult: (FrameResult) -> Unit
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val nv21 = yuv420ToNv21(image)
            val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
            yuvMat.put(0, 0, nv21)

            val rgba = Mat()
            Imgproc.cvtColor(yuvMat, rgba, Imgproc.COLOR_YUV2RGBA_NV21)
            yuvMat.release()

            val rotated = rotateIfNeeded(rgba, image.imageInfo.rotationDegrees)
            if (rotated !== rgba) rgba.release()

            val shapes = ShapeDetector.detect(rotated)
            val positioned = FlowPositionAnalyzer.analyze(shapes)
            rotated.release()

            onResult(FrameResult(positioned, image.width, image.height))
        } catch (t: Throwable) {
            Log.e(TAG, "Error analizando frame", t)
        } finally {
            image.close()
        }
    }

    private fun rotateIfNeeded(src: Mat, rotationDegrees: Int): Mat {
        val code = when (rotationDegrees) {
            90 -> Core.ROTATE_90_CLOCKWISE
            180 -> Core.ROTATE_180
            270 -> Core.ROTATE_90_COUNTERCLOCKWISE
            else -> return src
        }
        val dst = Mat()
        Core.rotate(src, dst, code)
        return dst
    }

    /** Empaqueta los planos Y, U, V de un ImageProxy YUV_420_888 al formato NV21 (Y + VU intercalado). */
    private fun yuv420ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val nv21 = ByteArray(ySize + width * height / 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        copyPlaneToBuffer(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, width, height, nv21, 0)

        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val vBuffer: ByteBuffer = vPlane.buffer
        val uBuffer: ByteBuffer = uPlane.buffer
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride

        var offset = ySize
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                nv21[offset++] = vBuffer.get(vIndex)
                nv21[offset++] = uBuffer.get(uIndex)
            }
        }
        return nv21
    }

    private fun copyPlaneToBuffer(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        dst: ByteArray,
        dstOffset: Int
    ) {
        var offset = dstOffset
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(dst, offset, width * height)
            return
        }
        for (row in 0 until height) {
            for (col in 0 until width) {
                dst[offset++] = buffer.get(row * rowStride + col * pixelStride)
            }
        }
    }

    companion object {
        private const val TAG = "FrameAnalyzer"
    }
}
