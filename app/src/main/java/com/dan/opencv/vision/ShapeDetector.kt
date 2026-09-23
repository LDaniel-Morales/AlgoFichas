package com.dan.opencv.vision

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfInt4
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.geometry.Geometry
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Detecta fichas de diagrama de flujo (forma + color + posición) en un frame RGBA.
 *
 * Nota sobre la API: en el AAR de OpenCV 5.0 incluido en este proyecto, funciones de
 * contorno que en versiones previas vivían en [Imgproc] (approxPolyDP, contourArea,
 * arcLength, boundingRect, minAreaRect, moments, fitEllipse, convexHull,
 * convexityDefects) se movieron al paquete [org.opencv.geometry.Geometry].
 * findContours/drawContours/cvtColor/HoughLinesP siguen en Imgproc.
 *
 * Criterios de clasificación, por tipo de ficha:
 *  - Triángulo: 3 vértices.
 *  - Rectángulo / cuadrado / rombo / paralelogramo (4 vértices): se miden los 4 ángulos
 *    internos reales (invariante a rotación). Si se alejan de 90°, es un paralelogramo.
 *    Si son ~90°, el aspect ratio separa cuadrado de rectángulo, y el ángulo de rotación
 *    del minAreaRect detecta el rombo (geométricamente, un cuadrado rotado ~45°).
 *  - Llamada a subrutina: un rectángulo cuyo interior (buscado con HoughLinesP sobre el
 *    mapa de bordes, no sobre el contorno) contiene dos líneas casi verticales.
 *  - Estadio (Inicio/Fin): tiene dos lados rectos y paralelos que ocupan buena parte del
 *    perímetro, más extremos curvos (extent = área/área del minAreaRect intermedio entre
 *    el de un rectángulo (~1) y el de una elipse (~0.785)).
 *  - Círculo / óvalo: alta circularidad -> se ajusta una elipse (fitEllipse) sobre el
 *    contorno completo; su aspect ratio separa círculo de óvalo.
 *  - Pentágonos (conector de página / salida en pantalla): 5 vértices; el vértice más
 *    alejado del centroide es la "punta". Si se desvía más en horizontal -> apunta a la
 *    derecha -> símbolo de pantalla. Si se desvía más en vertical -> apunta hacia
 *    abajo/arriba -> conector de página.
 *  - Iteración: hexágono con alta solidez (contorno ~convexo, sin encajes).
 *  - Entrada por teclado / salida impresa: contornos NO convexos (borde superior/inferior
 *    irregular). Se cuentan los "convexity defects": muchos y pequeños (zigzag) -> entrada
 *    por teclado; pocos y amplios (onda) -> salida impresa.
 *
 * Simplificaciones conocidas (primera versión, pendiente de ajustar con fichas reales):
 *  - "Iteración" solo valida el cuerpo hexagonal, no la línea punteada + círculo que lo
 *    acompaña en el símbolo completo.
 *  - La distinción teclado/documento por conteo de defectos de convexidad es una heurística
 *    de partida, no calibrada todavía.
 *  - No se distingue "cuadrado" ni "óvalo" como fichas propias en el set de referencia,
 *    pero se conservan como clasificación geométrica general.
 */
object ShapeDetector {

    // --- Filtro de tamaño ---
    private const val MIN_AREA_RATIO = 0.003
    private const val MAX_AREA_RATIO = 0.85

    // --- Aproximación poligonal ---
    private const val APPROX_EPSILON_FRACTION = 0.015

    // --- Familia rectángulo / cuadrado / rombo / paralelogramo ---
    private const val RIGHT_ANGLE_TOLERANCE_DEG = 18.0
    private const val SQUARE_ASPECT_TOLERANCE = 1.25
    private const val DIAMOND_ANGLE_TOLERANCE_DEG = 20.0

    // --- Familia círculo / óvalo ---
    private const val CIRCULARITY_THRESHOLD = 0.65
    private const val CIRCLE_ASPECT_TOLERANCE = 1.15

    // --- Estadio (Inicio/Fin): dos lados rectos y paralelos + extremos curvos ---
    private const val LONG_EDGE_PERIMETER_FRACTION = 0.15
    private const val PARALLEL_ANGLE_TOLERANCE_DEG = 12.0
    private const val STADIUM_EXTENT_MIN = 0.75
    private const val STADIUM_EXTENT_MAX = 0.97

    // --- Hexágono de iteración ---
    private const val HEXAGON_SOLIDITY_MIN = 0.90

    // --- Bordes irregulares (entrada por teclado / salida impresa) ---
    private const val JAGGED_SOLIDITY_MAX = 0.92
    private const val JAGGED_DEFECT_COUNT_FOR_KEYBOARD = 4

    // --- Líneas internas (llamada a subrutina) ---
    private const val PREDEFINED_PROCESS_INSET_X_FRACTION = 0.08
    private const val PREDEFINED_PROCESS_INSET_Y_FRACTION = 0.12
    private const val PREDEFINED_PROCESS_VERTICAL_TOLERANCE_DEG = 12.0

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
                if (perimeter <= 0) {
                    contour2f.release()
                    continue
                }

                val approx2f = MatOfPoint2f()
                Geometry.approxPolyDP(contour2f, approx2f, APPROX_EPSILON_FRACTION * perimeter, true)
                val approxPoints = approx2f.toArray()

                val boundingBox = Geometry.boundingRect(contour)
                var type = classifyShape(approxPoints, area, perimeter, contour, contour2f)
                if (type == ShapeType.RECTANGLE && hasInternalVerticalLines(edges, boundingBox)) {
                    type = ShapeType.PREDEFINED_PROCESS
                }

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
        approxPoints: Array<Point>,
        area: Double,
        perimeter: Double,
        contour: MatOfPoint,
        contour2f: MatOfPoint2f
    ): ShapeType {
        val vertexCount = approxPoints.size
        if (vertexCount < 3) return ShapeType.UNKNOWN
        if (vertexCount == 3) return ShapeType.TRIANGLE
        if (vertexCount == 4) return classifyQuad(approxPoints, contour2f)

        val circularity = if (perimeter > 0) (4.0 * Math.PI * area) / (perimeter * perimeter) else 0.0
        val rotatedRect = Geometry.minAreaRect(contour2f)
        val rectArea = rotatedRect.size.width * rotatedRect.size.height
        val extent = if (rectArea > 0) area / rectArea else 0.0

        if (isStadium(approxPoints, perimeter, extent)) return ShapeType.STADIUM
        if (vertexCount == 5) return classifyPentagon(approxPoints)

        val (solidity, defectCount) = convexStats(contour)

        return when {
            vertexCount == 6 && solidity >= HEXAGON_SOLIDITY_MIN -> ShapeType.LOOP
            circularity > CIRCULARITY_THRESHOLD -> classifyEllipse(contour2f)
            solidity < JAGGED_SOLIDITY_MAX -> classifyJaggedRectangle(defectCount)
            else -> ShapeType.UNKNOWN
        }
    }

    /**
     * Distingue cuadrado / rectángulo / rombo (decisión) / paralelogramo (entrada-salida)
     * midiendo los 4 ángulos internos reales del cuadrilátero (esto es invariante a
     * rotación: un rectángulo rotado sigue teniendo sus 4 ángulos en ~90°, solo un
     * cuadrilátero realmente "inclinado" -paralelogramo- se aleja de 90°).
     */
    private fun classifyQuad(points: Array<Point>, contour2f: MatOfPoint2f): ShapeType {
        val angles = DoubleArray(4) { i ->
            interiorAngleDegrees(points[(i + 3) % 4], points[i], points[(i + 1) % 4])
        }
        val maxDeviationFrom90 = angles.maxOf { abs(it - 90.0) }

        if (maxDeviationFrom90 > RIGHT_ANGLE_TOLERANCE_DEG) {
            return ShapeType.PARALLELOGRAM
        }

        val rotatedRect = Geometry.minAreaRect(contour2f)
        val w = rotatedRect.size.width
        val h = rotatedRect.size.height
        if (w <= 0 || h <= 0) return ShapeType.RECTANGLE

        val aspect = maxOf(w, h) / minOf(w, h)
        // ángulo normalizado a [0, 90)
        var angle = rotatedRect.angle % 90.0
        if (angle < 0) angle += 90.0
        val distanceFrom45 = abs(angle - 45.0)

        val nearlySquare = aspect < SQUARE_ASPECT_TOLERANCE
        val rotated45 = distanceFrom45 < DIAMOND_ANGLE_TOLERANCE_DEG

        return when {
            nearlySquare && rotated45 -> ShapeType.DIAMOND
            nearlySquare -> ShapeType.SQUARE
            else -> ShapeType.RECTANGLE
        }
    }

    /** Ángulo interno en el vértice [b], formado por los segmentos b->a y b->c, en grados. */
    private fun interiorAngleDegrees(a: Point, b: Point, c: Point): Double {
        val v1x = a.x - b.x; val v1y = a.y - b.y
        val v2x = c.x - b.x; val v2y = c.y - b.y
        val mag1 = hypot(v1x, v1y)
        val mag2 = hypot(v2x, v2y)
        if (mag1 == 0.0 || mag2 == 0.0) return 90.0
        val cos = ((v1x * v2x + v1y * v2y) / (mag1 * mag2)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    /**
     * Estadio (Inicio/Fin): tiene dos lados rectos y paralelos que ocupan una fracción
     * significativa del perímetro (a diferencia de una elipse, que no tiene tramos rectos),
     * y un "extent" (área del contorno / área del minAreaRect) intermedio entre el de un
     * rectángulo puro (~1.0) y el de una elipse inscrita (~0.785).
     */
    private fun isStadium(points: Array<Point>, perimeter: Double, extent: Double): Boolean {
        if (extent < STADIUM_EXTENT_MIN || extent > STADIUM_EXTENT_MAX) return false
        return hasLongParallelEdgePair(points, perimeter)
    }

    private fun hasLongParallelEdgePair(points: Array<Point>, perimeter: Double): Boolean {
        val n = points.size
        val minLen = LONG_EDGE_PERIMETER_FRACTION * perimeter
        val edgeLens = DoubleArray(n)
        val edgeAngles = DoubleArray(n)
        for (i in 0 until n) {
            val p1 = points[i]; val p2 = points[(i + 1) % n]
            val dx = p2.x - p1.x; val dy = p2.y - p1.y
            edgeLens[i] = hypot(dx, dy)
            edgeAngles[i] = Math.toDegrees(atan2(dy, dx))
        }
        for (i in 0 until n) {
            if (edgeLens[i] < minLen) continue
            for (j in i + 1 until n) {
                if (edgeLens[j] < minLen) continue
                var diff = abs(edgeAngles[i] - edgeAngles[j]) % 180.0
                if (diff > 90.0) diff = 180.0 - diff
                if (diff < PARALLEL_ANGLE_TOLERANCE_DEG) return true
            }
        }
        return false
    }

    /**
     * Conector de página (punta hacia abajo) vs. salida en pantalla (punta hacia la
     * derecha): el vértice más alejado del centroide del pentágono es la "punta"; si se
     * desvía más en horizontal es la de pantalla, si más en vertical es el conector.
     */
    private fun classifyPentagon(points: Array<Point>): ShapeType {
        val centroidX = points.sumOf { it.x } / points.size
        val centroidY = points.sumOf { it.y } / points.size
        val tip = points.maxByOrNull { hypot(it.x - centroidX, it.y - centroidY) }
            ?: return ShapeType.UNKNOWN
        val dx = abs(tip.x - centroidX)
        val dy = abs(tip.y - centroidY)
        return if (dx > dy) ShapeType.DISPLAY else ShapeType.CONNECTOR_PAGE
    }

    /** Distingue círculo de óvalo ajustando una elipse al contorno completo. */
    private fun classifyEllipse(contour2f: MatOfPoint2f): ShapeType {
        if (contour2f.rows() < 5) return ShapeType.OVAL // fitEllipse exige >= 5 puntos
        val ellipse = Geometry.fitEllipse(contour2f)
        val w = ellipse.size.width
        val h = ellipse.size.height
        if (w <= 0 || h <= 0) return ShapeType.OVAL

        val aspect = maxOf(w, h) / minOf(w, h)
        return if (aspect < CIRCLE_ASPECT_TOLERANCE) ShapeType.CIRCLE else ShapeType.OVAL
    }

    /**
     * Entrada por teclado (borde dentado en zigzag) vs. salida impresa (borde ondulado):
     * un zigzag genera muchos defectos de convexidad pequeños; una onda suave, pocos y
     * amplios. Heurística de primera versión, no calibrada con fichas reales todavía.
     */
    private fun classifyJaggedRectangle(defectCount: Int): ShapeType {
        return if (defectCount >= JAGGED_DEFECT_COUNT_FOR_KEYBOARD) {
            ShapeType.KEYBOARD_INPUT
        } else {
            ShapeType.DOCUMENT
        }
    }

    /** solidity = área del contorno / área de su cápsula convexa; defectCount = nº de defectos de convexidad. */
    private fun convexStats(contour: MatOfPoint): Pair<Double, Int> {
        val hullIndices = MatOfInt()
        try {
            Geometry.convexHull(contour, hullIndices)
            val idx = hullIndices.toArray()
            if (idx.size < 3) return 1.0 to 0

            val contourPoints = contour.toArray()
            val hullPoints = Array(idx.size) { contourPoints[idx[it]] }
            val hullMat = MatOfPoint2f(*hullPoints)
            val hullArea = Geometry.contourArea(hullMat)
            hullMat.release()

            val contourArea = Geometry.contourArea(contour)
            val solidity = if (hullArea > 0) (contourArea / hullArea).coerceAtMost(1.0) else 1.0

            var defectCount = 0
            val defects = MatOfInt4()
            try {
                Geometry.convexityDefects(contour, hullIndices, defects)
                defectCount = defects.rows()
            } catch (_: Throwable) {
                defectCount = 0
            } finally {
                defects.release()
            }

            return solidity to defectCount
        } finally {
            hullIndices.release()
        }
    }

    /**
     * "Llamada a subrutina": un rectángulo con dos líneas verticales internas cerca de
     * los bordes izquierdo y derecho. El contorno externo (RETR_EXTERNAL) no las ve, así
     * que se buscan directamente en el mapa de bordes (Canny) recortado al interior del
     * rectángulo, con HoughLinesP.
     */
    private fun hasInternalVerticalLines(edges: Mat, boundingBox: Rect): Boolean {
        val insetX = (boundingBox.width * PREDEFINED_PROCESS_INSET_X_FRACTION).toInt().coerceAtLeast(2)
        val insetY = (boundingBox.height * PREDEFINED_PROCESS_INSET_Y_FRACTION).toInt().coerceAtLeast(2)
        val roiWidth = boundingBox.width - 2 * insetX
        val roiHeight = boundingBox.height - 2 * insetY
        if (roiWidth <= 0 || roiHeight <= 0) return false

        val roiX = boundingBox.x + insetX
        val roiY = boundingBox.y + insetY
        if (roiX < 0 || roiY < 0 || roiX + roiWidth > edges.cols() || roiY + roiHeight > edges.rows()) {
            return false
        }

        val roi = Mat(edges, Rect(roiX, roiY, roiWidth, roiHeight))
        val lines = Mat()
        var verticalCount = 0
        try {
            Imgproc.HoughLinesP(
                roi, lines, 1.0, Math.PI / 180.0, 15,
                roiHeight * 0.6, roiHeight * 0.2
            )
            for (i in 0 until lines.rows()) {
                val l = lines.get(i, 0)
                val dx = l[2] - l[0]
                val dy = l[3] - l[1]
                val angleFromVertical = abs(abs(Math.toDegrees(atan2(dy, dx))) - 90.0)
                if (angleFromVertical < PREDEFINED_PROCESS_VERTICAL_TOLERANCE_DEG) verticalCount++
            }
        } finally {
            lines.release()
            roi.release()
        }
        return verticalCount >= 2
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
