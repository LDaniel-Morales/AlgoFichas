package com.dan.opencv.vision

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Extrae y preprocesa la Región de Interés (ROI) interna de una ficha detectada.
 * Aísla el área interior donde se escribe la instrucción, eliminando sombras, fondo de mesa
 * o rebordes irregulares del corte con tijera.
 */
object FichaRoiExtractor {

    /**
     * Margen interno porcentual para evitar tomar bordes de cartón o líneas externas.
     * 4% horizontal y 5% vertical.
     */
    private const val INSET_X_PERCENT = 0.04
    private const val INSET_Y_PERCENT = 0.05

    /**
     * Extrae un [Bitmap] RGB limpio recortando la región de la ficha desde la matriz [rgba].
     */
    fun extractRoi(rgba: Mat, boundingBox: Rect, applyInset: Boolean = true): Bitmap? {
        if (rgba.empty() || rgba.cols() <= 0 || rgba.rows() <= 0) return null

        val imgWidth = rgba.cols()
        val imgHeight = rgba.rows()

        val safeX = boundingBox.x.coerceIn(0, imgWidth - 1)
        val safeY = boundingBox.y.coerceIn(0, imgHeight - 1)
        val safeW = boundingBox.width.coerceAtMost(imgWidth - safeX)
        val safeH = boundingBox.height.coerceAtMost(imgHeight - safeY)

        if (safeW < 8 || safeH < 8) return null

        val finalRect = if (applyInset) {
            val insetX = (safeW * INSET_X_PERCENT).toInt()
            val insetY = (safeH * INSET_Y_PERCENT).toInt()
            val rx = (safeX + insetX).coerceIn(0, imgWidth - 1)
            val ry = (safeY + insetY).coerceIn(0, imgHeight - 1)
            val rw = (safeW - 2 * insetX).coerceIn(8, imgWidth - rx)
            val rh = (safeH - 2 * insetY).coerceIn(8, imgHeight - ry)
            Rect(rx, ry, rw, rh)
        } else {
            Rect(safeX, safeY, safeW, safeH)
        }

        val subMat = Mat(rgba, finalRect)
        return try {
            val bitmap = Bitmap.createBitmap(finalRect.width, finalRect.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(subMat, bitmap)
            bitmap
        } catch (e: Throwable) {
            null
        } finally {
            subMat.release()
        }
    }

    /**
     * Preprocesa un [Bitmap] de ficha para maximizar la legibilidad en OCR de trazos
     * a lápiz o plumón tenue sobre papel/cartón de diferente tonalidad.
     * Aplica escala de grises y ecualización adaptativa de contraste (CLAHE).
     */
    fun enhanceForOcr(roiBitmap: Bitmap): Bitmap? {
        val srcMat = Mat()
        val grayMat = Mat()
        val claheMat = Mat()
        return try {
            Utils.bitmapToMat(roiBitmap, srcMat)
            Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(grayMat, claheMat)

            val outBitmap = Bitmap.createBitmap(roiBitmap.width, roiBitmap.height, Bitmap.Config.ARGB_8888)
            Imgproc.cvtColor(claheMat, srcMat, Imgproc.COLOR_GRAY2RGBA)
            Utils.matToBitmap(srcMat, outBitmap)
            outBitmap
        } catch (e: Throwable) {
            null
        } finally {
            srcMat.release()
            grayMat.release()
            claheMat.release()
        }
    }
}
