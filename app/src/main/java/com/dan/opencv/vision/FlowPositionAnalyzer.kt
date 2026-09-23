package com.dan.opencv.vision

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Ficha con su posición relativa dentro del diagrama, inferida solo por geometría
 * (sin usar flechas todavía, tal como se pidió para esta primera versión).
 */
data class PositionedShape(
    val shape: DetectedShape,
    val row: Int,
    val column: Int,
    /** id de la ficha más cercana por debajo (candidata a "siguiente paso"), o null si no hay. */
    val inferredNextId: Int?
)

/**
 * Infiere el orden de lectura de un diagrama de flujo (arriba->abajo, izquierda->derecha)
 * agrupando fichas en "filas" por cercanía en Y, y para cada ficha busca la más cercana
 * por debajo dentro de un corredor horizontal como posible siguiente paso.
 *
 * Es una heurística de posición pura: sirve de punto de partida mientras no se procesen
 * flechas; con diagramas ambiguos (ramas de decisión en paralelo, etc.) puede fallar.
 */
object FlowPositionAnalyzer {

    fun analyze(shapes: List<DetectedShape>): List<PositionedShape> {
        if (shapes.isEmpty()) return emptyList()

        val avgHeight = shapes.map { it.boundingBox.height }.average()
        val rowThreshold = (avgHeight * 0.6).coerceAtLeast(1.0)

        // Agrupar en filas: ordenar por Y y juntar los que caen dentro del umbral del
        // primer elemento de cada fila.
        val byY = shapes.sortedBy { it.centerY }
        val rows = ArrayList<MutableList<DetectedShape>>()
        for (shape in byY) {
            val row = rows.lastOrNull()
            if (row != null && abs(shape.centerY - row.first().centerY) <= rowThreshold) {
                row.add(shape)
            } else {
                rows.add(mutableListOf(shape))
            }
        }

        val rowIndexOf = HashMap<Int, Int>()
        val columnIndexOf = HashMap<Int, Int>()
        rows.forEachIndexed { rowIdx, row ->
            row.sortBy { it.centerX }
            row.forEachIndexed { colIdx, shape ->
                rowIndexOf[shape.id] = rowIdx
                columnIndexOf[shape.id] = colIdx
            }
        }

        // Corredor horizontal para buscar "siguiente" ficha por debajo: proporcional al
        // ancho promedio de las fichas para tolerar diagramas no perfectamente alineados.
        val avgWidth = shapes.map { it.boundingBox.width }.average()
        val corridor = (avgWidth * 1.2).coerceAtLeast(1.0)

        return shapes.map { shape ->
            val candidate = shapes
                .filter { it.id != shape.id && it.centerY > shape.centerY }
                .filter { abs(it.centerX - shape.centerX) <= corridor }
                .minByOrNull { hypot(it.centerX - shape.centerX, it.centerY - shape.centerY) }

            PositionedShape(
                shape = shape,
                row = rowIndexOf[shape.id] ?: 0,
                column = columnIndexOf[shape.id] ?: 0,
                inferredNextId = candidate?.id
            )
        }
    }
}
