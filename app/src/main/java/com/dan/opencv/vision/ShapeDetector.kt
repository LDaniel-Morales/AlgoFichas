package com.dan.opencv.vision

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc

/**
 * Detecta fichas de diagrama de flujo (forma + color + posición) en un frame RGBA.
 *
 * Nota sobre la API: en el AAR de OpenCV 5.0 incluido en este proyecto, funciones de
 * contorno que en versiones previas vivían en [Imgproc] (approxPolyDP, contourArea,
 * arcLength, boundingRect, minAreaRect, moments) se movieron al paquete
 * [org.opencv.geometry.Geometry]. findContours/drawContours/cvtColor siguen en Imgproc.
 *
 * Simplificaciones deliberadas de esta primera versión (solo lógica, sin ajuste fino):
 *  - Solo se distinguen 4 formas: rectángulo, rombo (decisión), óvalo/círculo y triángulo.
 *    No se separa paralelogramo (entrada/salida) del rectángulo todavía.
 *  - La clasificación rombo vs. rectángulo usa el ángulo de rotación del rectángulo
 *    mínimo (minAreaRect): si está cerca de 45° y es aproximadamente cuadrado, es rombo.
 */
object ShapeDetector {

    /** Ignora contornos más pequeños que este % del área del frame (ruido). */
    private const val MIN_AREA_RATIO = 0.003

    /** Ignora contornos mayores a este % del área del frame (borde de la imagen completa). */
    private const val MAX_AREA_RATIO = 0.85

    fun detect(rgba: Mat): List<DetectedShape> {
        val frameArea = rgba.rows().toDouble() * rgba.cols().toDouble()
        val minArea = frameArea * MIN_AREA_RATIO
        val maxArea = frameArea * MAX_AREA_RATIO

        val gray = Mat()
        val edges = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()

        val results = ArrayList<DetectedShape>()

        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(gray, edges, 50.0, 150.0)
            Imgproc.dilate(edges, edges, Mat(), Point(-1.0, -1.0), 1)

            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

            Imgproc.findContours(
                edges, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )

            var nextId = 0
            for (contour in contours) {
                val area = Geometry.contourArea(contour)
                if (area < minArea || area > maxArea) continue

                val contour2f = MatOfPoint2f(*contour.toArray())
                val perimeter = Geometry.arcLength(contour2f, true)
                if (perimeter <= 0) continue

                val approx2f = MatOfPoint2f()
                Geometry.approxPolyDP(contour2f, approx2f, 0.02 * perimeter, true)
                val approxPoints = approx2f.toArray()

                val boundingBox = Geometry.boundingRect(contour)
                val type = classifyShape(approxPoints.size, area, perimeter, contour2f)

                val moments = Geometry.moments(contour)
                if (moments.m00 == 0.0) {
                    contour2f.release(); approx2f.release()
                    continue
                }
                val cx = moments.m10 / moments.m00
                val cy = moments.m01 / moments.m00

                val colorName = meanColorName(hsv, contour)

                results.add(
                    DetectedShape(
                        id = nextId++,
                        type = type,
                        colorName = colorName,
                        centerX = cx,
                        centerY = cy,
                        boundingBox = boundingBox,
                        areaPx = area
                    )
                )

                contour2f.release()
                approx2f.release()
            }
        } finally {
            gray.release()
            edges.release()
            rgb.release()
            hsv.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }

        return results
    }

    private fun classifyShape(
        vertexCount: Int,
        area: Double,
        perimeter: Double,
        contour2f: MatOfPoint2f
    ): ShapeType {
        val circularity = if (perimeter > 0) (4.0 * Math.PI * area) / (perimeter * perimeter) else 0.0

        return when {
            vertexCount == 3 -> ShapeType.TRIANGLE
            vertexCount == 4 -> classifyQuad(contour2f)
            vertexCount in 5..8 && circularity > 0.55 -> ShapeType.OVAL
            vertexCount > 8 -> ShapeType.OVAL
            else -> ShapeType.UNKNOWN
        }
    }

    /** Distingue rombo (decisión) de rectángulo (proceso) usando el ángulo del minAreaRect. */
    private fun classifyQuad(contour2f: MatOfPoint2f): ShapeType {
        val rotatedRect = Geometry.minAreaRect(contour2f)
        val w = rotatedRect.size.width
        val h = rotatedRect.size.height
        if (w <= 0 || h <= 0) return ShapeType.RECTANGLE

        val aspect = maxOf(w, h) / minOf(w, h)
        // ángulo normalizado a [0, 90)
        var angle = rotatedRect.angle % 90.0
        if (angle < 0) angle += 90.0
        val distanceFrom45 = kotlin.math.abs(angle - 45.0)

        val nearlySquare = aspect < 1.35
        val rotated45 = distanceFrom45 < 20.0

        return if (nearlySquare && rotated45) ShapeType.DIAMOND else ShapeType.RECTANGLE
    }

    /** Color dominante dentro del contorno, usando la media en HSV enmascarada por el contorno. */
    private fun meanColorName(hsv: Mat, contour: MatOfPoint): String {
        val mask = Mat.zeros(hsv.size(), CvType.CV_8UC1)
        val single = listOf(contour)
        return try {
            Imgproc.drawContours(mask, single, -1, Scalar(255.0), -1)
            val mean = Core.mean(hsv, mask)
            classifyColor(mean.`val`[0], mean.`val`[1], mean.`val`[2])
        } finally {
            mask.release()
        }
    }

    /** Clasifica un color HSV (H:0-179, S/V:0-255, convención OpenCV de 8 bits) a un nombre. */
    fun classifyColor(h: Double, s: Double, v: Double): String {
        if (v < 50) return "negro"
        if (s < 40) return if (v > 200) "blanco" else "gris"

        return when {
            h < 10 || h >= 170 -> "rojo"
            h < 22 -> "naranja"
            h < 38 -> "amarillo"
            h < 85 -> "verde"
            h < 100 -> "cian"
            h < 135 -> "azul"
            h < 160 -> "violeta"
            else -> "rosa"
        }
    }
}
