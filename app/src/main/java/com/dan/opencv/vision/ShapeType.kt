package com.dan.opencv.vision

/**
 * Tipos de ficha de diagrama de flujo que reconocemos por geometría del contorno.
 * La correspondencia con el significado estándar de flowchart se deja como comentario.
 */
enum class ShapeType(val label: String) {
    RECTANGLE("rectángulo"),   // Proceso
    DIAMOND("rombo"),          // Decisión
    OVAL("óvalo/círculo"),     // Inicio/Fin
    TRIANGLE("triángulo"),     // (poco común en flowcharts, se detecta igual)
    UNKNOWN("desconocido")
}
