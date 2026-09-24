package com.dan.opencv.vision

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
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
 *    el de un rectángulo (~1) y el de una elipse (~0.785)). Se evalúa DESPUÉS del hexágono
 *    de iteración (ver abajo), porque con fichas reales ambos pueden compartir extent y
 *    circularidad muy parecidos.
 *  - Círculo / óvalo: alta circularidad -> se ajusta una elipse (fitEllipse) sobre el
 *    contorno completo; su aspect ratio separa círculo de óvalo.
 *  - Pentágonos (conector de página / salida en pantalla): 5 vértices; el vértice más
 *    alejado del centroide es la "punta". Si se desvía más en horizontal -> apunta a la
 *    derecha -> símbolo de pantalla. Si se desvía más en vertical -> apunta hacia
 *    abajo/arriba -> conector de página.
 *  - Iteración: hexágono (exactamente 6 vértices) con alta solidez (contorno ~convexo, sin
 *    encajes). Se decide antes que estadio -ver arriba-.
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

    /**
     * Modo diagnóstico: si está en `true`, cada contorno vuelca por logcat (tag [DEBUG_TAG])
     * sus métricas internas (vértices, extent, circularidad, solidez, candidatos de estadio,
     * clasificación final) para poder afinar los umbrales con datos reales en vez de a
     * ciegas. Pensado para prenderse temporalmente durante una sesión de calibración.
     */
    var debugLogging: Boolean = true // poner en true para volcar por logcat (tag ShapeDebug) las métricas internas de cada contorno al calibrar
    private const val DEBUG_TAG = "ShapeDebug"

    // --- Filtro de tamaño ---
    private const val MIN_AREA_RATIO = 0.0012
    private const val MAX_AREA_RATIO = 0.85

    // --- Segmentación multi-método (ver docs/deteccion-fondos.md) ---
    private const val MEAN_SHIFT_SPATIAL_RADIUS = 10.0
    private const val MEAN_SHIFT_COLOR_RADIUS = 25.0
    private const val CANNY_L_LOW = 40.0
    private const val CANNY_L_HIGH = 120.0
    private const val CANNY_AB_LOW = 15.0
    private const val CANNY_AB_HIGH = 45.0
    private const val BORDER_SAMPLE_FRACTION = 0.05
    private const val BACKGROUND_L_WEIGHT = 0.5
    private const val MIN_BACKGROUND_DISTANCE = 12.0
    private const val MIN_CANDIDATE_SOLIDITY = 0.75
    private const val DUPLICATE_IOU = 0.4
    private const val BORDER_MARGIN_PX = 3
    private const val LONE_SMALL_AREA_FRACTION = 0.3

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
    private const val MIN_CAP_VERTICES = 2
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

    private inline fun dlog(message: () -> String) {
        if (debugLogging) Log.d(DEBUG_TAG, message())
    }

    /** Un contorno candidato a ficha, propuesto por uno de los métodos de segmentación. */
    private class Candidato(
        val metodo: String,
        val contour: MatOfPoint,
        val area: Double,
        val solidez: Double,
        val rect: Rect
    ) {
        /** Cuántos métodos propusieron esta misma ficha (se llena en [selectBest]). */
        var votos = 1
    }

    /**
     * Detecta las fichas combinando varios métodos de segmentación, para no depender del
     * material/color del fondo (análisis completo en docs/deteccion-fondos.md):
     *  1. Suaviza la textura del fondo (vetas, grano) con mean-shift, conservando bordes.
     *  2. Genera candidatos con 3 máscaras: bordes por canal Lab, saturación (Otsu) y
     *     distancia al color de fondo estimado en las orillas de la foto.
     *  3. Descarta candidatos rotos (poca solidez), los que tocan el borde de la foto (objetos
     *     cortados, como una laptop al costado) y se queda con el mejor por ficha.
     */
    fun detect(rgba: Mat): DetectionOutput {
        val frameArea = rgba.rows().toDouble() * rgba.cols().toDouble()
        val minArea = frameArea * MIN_AREA_RATIO
        val maxArea = frameArea * MAX_AREA_RATIO

        val rgb = Mat()
        val smooth = Mat()
        val hsv = Mat()
        val lab = Mat()
        val edges = Mat()
        val masks = ArrayList<Pair<String, Mat>>()
        val candidatos = ArrayList<Candidato>()
        val results = ArrayList<DetectedShape>()
        var fragmentos = 0

        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            val t0 = System.currentTimeMillis()
            Imgproc.pyrMeanShiftFiltering(rgb, smooth, MEAN_SHIFT_SPATIAL_RADIUS, MEAN_SHIFT_COLOR_RADIUS)
            dlog { "=== detect(): frame ${rgba.cols()}x${rgba.rows()}, minArea=${minArea.toInt()} maxArea=${maxArea.toInt()}, mean-shift ${System.currentTimeMillis() - t0} ms ===" }

            Imgproc.cvtColor(smooth, hsv, Imgproc.COLOR_RGB2HSV)
            Imgproc.cvtColor(smooth, lab, Imgproc.COLOR_RGB2Lab)

            colorEdges(lab, edges)
            masks += "bordes" to closedEdgesMask(edges)
            masks += "saturacion" to saturationMask(hsv)
            masks += "fondo" to backgroundDistanceMask(lab)

            for ((metodo, mask) in masks) {
                fragmentos = maxOf(fragmentos, extractCandidates(metodo, mask, minArea, maxArea, candidatos))
            }

            val elegidos = selectBest(candidatos)
            dlog { "elegidos ${elegidos.size} de ${candidatos.size} candidatos; fragmentos descartados (máx por método)=$fragmentos" }

            elegidos.forEachIndexed { idx, c ->
                classifyCandidate(idx, c, edges, hsv)?.let { results += it }
            }
        } finally {
            rgb.release()
            smooth.release()
            hsv.release()
            lab.release()
            edges.release()
            masks.forEach { it.second.release() }
            candidatos.forEach { it.contour.release() }
        }

        return DetectionOutput(results, fragmentos)
    }

    private fun classifyCandidate(idx: Int, c: Candidato, edges: Mat, hsv: Mat): DetectedShape? {
        val contour = c.contour
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approx2f = MatOfPoint2f()
        try {
            val perimeter = Geometry.arcLength(contour2f, true)
            if (perimeter <= 0) return null
            Geometry.approxPolyDP(contour2f, approx2f, APPROX_EPSILON_FRACTION * perimeter, true)
            val approxPoints = approx2f.toArray()

            dlog { "ficha[$idx] (${c.metodo}): area=${c.area.toInt()} perim=${perimeter.toInt()} vertices=${approxPoints.size} solidez=${"%.2f".format(c.solidez)}" }

            var type = classifyShape(approxPoints, c.area, perimeter, contour, contour2f, idx)
            if (type == ShapeType.RECTANGLE && hasInternalVerticalLines(edges, c.rect)) {
                type = ShapeType.PREDEFINED_PROCESS
                dlog { "ficha[$idx]: RECTANGLE -> PREDEFINED_PROCESS (líneas internas detectadas)" }
            }

            val moments = Geometry.moments(contour)
            if (moments.m00 == 0.0) return null
            val cx = moments.m10 / moments.m00
            val cy = moments.m01 / moments.m00
            val colorName = meanColorName(hsv, contour)

            dlog { "ficha[$idx]: FINAL tipo=$type color=$colorName pos=(${cx.toInt()},${cy.toInt()})" }
            return DetectedShape(
                id = idx,
                type = type,
                colorName = colorName,
                centerX = cx,
                centerY = cy,
                boundingBox = c.rect,
                areaPx = c.area
            )
        } finally {
            contour2f.release()
            approx2f.release()
        }
    }

    /** Método B: Canny en cada canal Lab (brillo + dos ejes de color), unidos. Sin cerrar. */
    private fun colorEdges(lab: Mat, out: Mat) {
        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        val tmp = Mat()
        try {
            Imgproc.Canny(channels[0], out, CANNY_L_LOW, CANNY_L_HIGH)
            for (i in 1..2) {
                Imgproc.Canny(channels[i], tmp, CANNY_AB_LOW, CANNY_AB_HIGH)
                Core.bitwise_or(out, tmp, out)
            }
        } finally {
            tmp.release()
            channels.forEach { it.release() }
        }
    }

    private fun closedEdgesMask(edges: Mat): Mat {
        val mask = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
        Imgproc.morphologyEx(edges, mask, Imgproc.MORPH_CLOSE, kernel, Point(-1.0, -1.0), 2)
        kernel.release()
        return mask
    }

    /** Método A: zonas más saturadas que el resto (Otsu). Si "lo saturado" es la mayoría, es el fondo: se invierte. */
    private fun saturationMask(hsv: Mat): Mat {
        val channels = ArrayList<Mat>()
        Core.split(hsv, channels)
        val mask = Mat()
        try {
            Imgproc.threshold(channels[1], mask, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            if (Core.countNonZero(mask) > mask.total() / 2) Core.bitwise_not(mask, mask)
            openMask(mask)
        } finally {
            channels.forEach { it.release() }
        }
        return mask
    }

    /**
     * Método C: estima el color del fondo como la mediana Lab de una franja en las orillas
     * de la foto, y marca todo lo que se aleje de él. El brillo pesa la mitad, para que las
     * sombras suaves no cuenten como ficha.
     */
    private fun backgroundDistanceMask(lab: Mat): Mat {
        val bg = borderMedianLab(lab)
        dlog { "fondo estimado Lab=(${bg[0].toInt()},${bg[1].toInt()},${bg[2].toInt()})" }

        val diff = Mat()
        val channels = ArrayList<Mat>()
        val dist = Mat()
        val mask = Mat()
        try {
            Core.absdiff(lab, Scalar(bg[0], bg[1], bg[2]), diff)
            diff.convertTo(diff, CvType.CV_32FC3)
            Core.split(diff, channels)
            Core.multiply(channels[0], Scalar(BACKGROUND_L_WEIGHT), channels[0])
            channels.forEach { Core.multiply(it, it, it) }
            Core.add(channels[0], channels[1], dist)
            Core.add(dist, channels[2], dist)
            Core.sqrt(dist, dist)
            dist.convertTo(dist, CvType.CV_8U)

            val otsu = Imgproc.threshold(dist, mask, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            if (otsu < MIN_BACKGROUND_DISTANCE) {
                Imgproc.threshold(dist, mask, MIN_BACKGROUND_DISTANCE, 255.0, Imgproc.THRESH_BINARY)
            }
            dlog { "fondo: umbral Otsu=${otsu.toInt()} (mínimo $MIN_BACKGROUND_DISTANCE)" }
            openMask(mask)
        } finally {
            diff.release()
            channels.forEach { it.release() }
            dist.release()
        }
        return mask
    }

    private fun borderMedianLab(lab: Mat): DoubleArray {
        val band = (minOf(lab.rows(), lab.cols()) * BORDER_SAMPLE_FRACTION).toInt().coerceAtLeast(1)
        val strips = listOf(
            Rect(0, 0, lab.cols(), band),
            Rect(0, lab.rows() - band, lab.cols(), band),
            Rect(0, band, band, lab.rows() - 2 * band),
            Rect(lab.cols() - band, band, band, lab.rows() - 2 * band)
        )
        val hist = Array(3) { IntArray(256) }
        var total = 0
        for (r in strips) {
            val strip = Mat(lab, r).clone()
            val bytes = ByteArray((strip.total() * 3).toInt())
            strip.get(0, 0, bytes)
            strip.release()
            for (i in bytes.indices) hist[i % 3][bytes[i].toInt() and 0xFF]++
            total += bytes.size / 3
        }
        return DoubleArray(3) { ch ->
            var acc = 0
            var v = 0
            while (v < 255 && acc + hist[ch][v] < total / 2) { acc += hist[ch][v]; v++ }
            v.toDouble()
        }
    }

    private fun openMask(mask: Mat) {
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        kernel.release()
    }

    /**
     * Extrae los contornos externos de una máscara y agrega a [out] los que parecen una ficha
     * entera. Devuelve cuántos se descartaron por verse rotos (poca solidez), para el aviso de
     * "fondo difícil".
     */
    private fun extractCandidates(
        metodo: String,
        mask: Mat,
        minArea: Double,
        maxArea: Double,
        out: MutableList<Candidato>
    ): Int {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()

        var fragmentos = 0
        var aceptados = 0
        for (contour in contours) {
            val area = Geometry.contourArea(contour)
            if (area < minArea || area > maxArea) { contour.release(); continue }

            val rect = Geometry.boundingRect(contour)
            val tocaBorde = rect.x <= BORDER_MARGIN_PX || rect.y <= BORDER_MARGIN_PX ||
                rect.x + rect.width >= mask.cols() - BORDER_MARGIN_PX ||
                rect.y + rect.height >= mask.rows() - BORDER_MARGIN_PX
            if (tocaBorde) {
                dlog { "[$metodo] descartado: toca el borde de la foto area=${area.toInt()} pos=(${rect.x},${rect.y})" }
                contour.release(); continue
            }

            val solidez = convexStats(contour).first
            if (solidez < MIN_CANDIDATE_SOLIDITY) {
                fragmentos++
                dlog { "[$metodo] descartado: fragmento solidez=${"%.2f".format(solidez)} area=${area.toInt()} pos=(${rect.x + rect.width / 2},${rect.y + rect.height / 2})" }
                contour.release(); continue
            }

            out += Candidato(metodo, contour, area, solidez, rect)
            aceptados++
        }
        dlog { "[$metodo] ${contours.size} contornos -> $aceptados candidatos, $fragmentos fragmentos" }
        return fragmentos
    }

    /**
     * Se queda con un candidato por ficha: primero los más sólidos, descartando los que se
     * encimen con uno ya elegido. Luego quita "contenedores" (una hoja o bandeja que rodea
     * varias fichas) y piezas sueltas que caen dentro de una ficha más grande. Por último
     * quita las piezas pequeñas que solo vio un método (detalles de objetos del entorno,
     * como una tecla de laptop): una ficha real la suelen encontrar varios métodos.
     */
    private fun selectBest(candidatos: List<Candidato>): List<Candidato> {
        val elegidos = ArrayList<Candidato>()
        for (c in candidatos.sortedWith(compareByDescending<Candidato> { it.solidez }.thenByDescending { it.area })) {
            val igual = elegidos.firstOrNull { iou(it.rect, c.rect) > DUPLICATE_IOU }
            if (igual == null) elegidos += c else if (igual.metodo != c.metodo) igual.votos++
        }
        val sinContenedores = elegidos.filter { c ->
            val dentro = elegidos.count { o -> o !== c && contiene(c.rect, o.rect) }
            if (dentro >= 2) dlog { "[${c.metodo}] descartado: contiene $dentro fichas (hoja/base)" }
            dentro < 2
        }
        val sinAnidados = sinContenedores.filter { c ->
            sinContenedores.none { o -> o !== c && o.area > c.area && contiene(o.rect, c.rect) }
        }
        val areaMediana = sinAnidados.map { it.area }.sorted().getOrNull(sinAnidados.size / 2) ?: 0.0
        return sinAnidados.filter { c ->
            val suelta = c.votos == 1 && c.area < areaMediana * LONE_SMALL_AREA_FRACTION
            if (suelta) dlog { "[${c.metodo}] descartado: pequeña (area=${c.area.toInt()}, mediana=${areaMediana.toInt()}) y la vio un solo método" }
            !suelta
        }
    }

    private fun contiene(outer: Rect, inner: Rect): Boolean {
        val cx = inner.x + inner.width / 2
        val cy = inner.y + inner.height / 2
        return cx > outer.x && cx < outer.x + outer.width && cy > outer.y && cy < outer.y + outer.height
    }

    private fun iou(a: Rect, b: Rect): Double {
        val ix = maxOf(0, minOf(a.x + a.width, b.x + b.width) - maxOf(a.x, b.x))
        val iy = maxOf(0, minOf(a.y + a.height, b.y + b.height) - maxOf(a.y, b.y))
        val inter = ix.toDouble() * iy
        val union = a.area() + b.area() - inter
        return if (union > 0) inter / union else 0.0
    }

    private fun classifyShape(
        approxPoints: Array<Point>,
        area: Double,
        perimeter: Double,
        contour: MatOfPoint,
        contour2f: MatOfPoint2f,
        rawIdx: Int
    ): ShapeType {
        val vertexCount = approxPoints.size
        if (vertexCount < 3) return ShapeType.UNKNOWN
        if (vertexCount == 3) return ShapeType.TRIANGLE
        if (vertexCount == 4) return classifyQuad(approxPoints, contour2f, rawIdx)

        val circularity = if (perimeter > 0) (4.0 * Math.PI * area) / (perimeter * perimeter) else 0.0
        val rotatedRect = Geometry.minAreaRect(contour2f)
        val rectArea = rotatedRect.size.width * rotatedRect.size.height
        val extent = if (rectArea > 0) area / rectArea else 0.0

        dlog { "contorno[$rawIdx]: (>=5) vertices=$vertexCount circularidad=${"%.2f".format(circularity)} extent=${"%.2f".format(extent)}" }

        val (solidity, defectCount) = convexStats(contour)
        dlog { "contorno[$rawIdx]: solidez=${"%.2f".format(solidity)} defectos=$defectCount (mínimo hexágono=$HEXAGON_SOLIDITY_MIN)" }

        // El hexágono de iteración se decide ANTES que el estadio: un hexágono aplanado (dos
        // lados largos paralelos) puede parecer geométricamente un estadio -extent y
        // circularidad casi idénticos, visto con fichas reales-, pero un hexágono real cae en
        // exactamente 6 vértices, mientras que un estadio real (con extremos curvos) casi
        // siempre necesita 7+ para aproximarse. Es la señal más confiable entre las dos.
        if (vertexCount == 6 && solidity >= HEXAGON_SOLIDITY_MIN) {
            dlog { "contorno[$rawIdx]: 6 vértices + muy convexo -> LOOP (antes de evaluar estadio)" }
            return ShapeType.LOOP
        }

        if (isStadium(approxPoints, perimeter, extent, rawIdx)) return ShapeType.STADIUM
        if (vertexCount == 5) return classifyPentagon(approxPoints)

        val result = when {
            circularity > CIRCULARITY_THRESHOLD -> classifyEllipse(contour2f)
            solidity < JAGGED_SOLIDITY_MAX -> classifyJaggedRectangle(defectCount)
            else -> ShapeType.UNKNOWN
        }
        dlog { "contorno[$rawIdx]: (>=5, no estadio ni hexágono) -> $result" }
        return result
    }

    /**
     * Distingue cuadrado / rectángulo / rombo (decisión) / paralelogramo (entrada-salida)
     * midiendo los 4 ángulos internos reales del cuadrilátero (esto es invariante a
     * rotación: un rectángulo rotado sigue teniendo sus 4 ángulos en ~90°, solo un
     * cuadrilátero realmente "inclinado" -paralelogramo- se aleja de 90°).
     */
    private fun classifyQuad(points: Array<Point>, contour2f: MatOfPoint2f, rawIdx: Int): ShapeType {
        val angles = DoubleArray(4) { i ->
            interiorAngleDegrees(points[(i + 3) % 4], points[i], points[(i + 1) % 4])
        }
        val maxDeviationFrom90 = angles.maxOf { abs(it - 90.0) }

        if (maxDeviationFrom90 > RIGHT_ANGLE_TOLERANCE_DEG) {
            dlog { "contorno[$rawIdx]: quad desviación=${"%.1f".format(maxDeviationFrom90)}° -> PARALLELOGRAM" }
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

        val result = when {
            nearlySquare && rotated45 -> ShapeType.DIAMOND
            nearlySquare -> ShapeType.SQUARE
            else -> ShapeType.RECTANGLE
        }
        dlog {
            "contorno[$rawIdx]: quad desviación=${"%.1f".format(maxDeviationFrom90)}° aspect=${"%.2f".format(aspect)} " +
                "ángulo=${"%.1f".format(angle)}° -> $result"
        }
        return result
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
    private fun isStadium(points: Array<Point>, perimeter: Double, extent: Double, rawIdx: Int): Boolean {
        if (extent < STADIUM_EXTENT_MIN || extent > STADIUM_EXTENT_MAX) {
            dlog { "contorno[$rawIdx]: stadium descartado por extent=${"%.2f".format(extent)} (rango $STADIUM_EXTENT_MIN-$STADIUM_EXTENT_MAX)" }
            return false
        }
        return hasLongParallelEdgePair(points, perimeter, rawIdx)
    }

    private fun hasLongParallelEdgePair(points: Array<Point>, perimeter: Double, rawIdx: Int): Boolean {
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
        var bestCandidate = "ninguno (sin par de lados largos y paralelos)"
        for (i in 0 until n) {
            if (edgeLens[i] < minLen) continue
            for (j in i + 1 until n) {
                if (edgeLens[j] < minLen) continue
                var diff = abs(edgeAngles[i] - edgeAngles[j]) % 180.0
                if (diff > 90.0) diff = 180.0 - diff
                if (diff >= PARALLEL_ANGLE_TOLERANCE_DEG) continue

                // Además de paralelos y largos, los tramos ENTRE esos dos lados deben verse
                // curvos (varios vértices por extremo) y no una esquina recta -si no, un
                // rectángulo o hexágono con esquinas ligeramente imprecisas (corte a mano,
                // compresión JPEG) se confunde con un estadio-.
                val cap1 = (j - (i + 1) + n) % n
                val cap2 = (i - (j + 1) + n) % n
                bestCandidate = "i=$i j=$j diffÁngulo=${"%.1f".format(diff)}° cap1=$cap1 cap2=$cap2 (n=$n, mínimo exigido=$MIN_CAP_VERTICES)"
                if (cap1 >= MIN_CAP_VERTICES && cap2 >= MIN_CAP_VERTICES) {
                    dlog { "contorno[$rawIdx]: stadium OK, candidato $bestCandidate" }
                    return true
                }
            }
        }
        dlog { "contorno[$rawIdx]: stadium rechazado, mejor candidato: $bestCandidate" }
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
        val hue = Mat()
        val hist = Mat()
        return try {
            Imgproc.drawContours(mask, single, -1, Scalar(255.0), -1)
            val mean = Core.mean(hsv, mask)
            // El tono es circular (el rojo está en ~0 y en ~179 a la vez): promediarlo da un
            // valor intermedio -verde/cian- para una ficha roja. Se usa el tono más frecuente.
            Core.extractChannel(hsv, hue, 0)
            Imgproc.calcHist(listOf(hue), MatOfInt(0), mask, hist, MatOfInt(180), MatOfFloat(0f, 180f))
            // El histograma puede venir como columna (180x1) o como fila (1x180) según la versión.
            val maxLoc = Core.minMaxLoc(hist).maxLoc
            val dominantHue = if (hist.rows() == 1) maxLoc.x else maxLoc.y
            val name = classifyColor(dominantHue, mean.`val`[1], mean.`val`[2])
            dlog { "color: hist ${hist.rows()}x${hist.cols()} tono dominante=${dominantHue.toInt()} (promedio=${mean.`val`[0].toInt()}) S=${mean.`val`[1].toInt()} V=${mean.`val`[2].toInt()} -> $name" }
            name
        } finally {
            mask.release()
            hue.release()
            hist.release()
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
