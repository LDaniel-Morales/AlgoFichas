package com.dan.opencv.vision

/**
 * Tipos de ficha de diagrama de flujo que reconocemos por geometría del contorno.
 * La correspondencia con el significado estándar de flowchart se deja como comentario.
 * Basado en el set de referencia de 11 fichas (ver memoria del proyecto).
 */
enum class ShapeType(val label: String, val role: FlowRole) {
    RECTANGLE("rectángulo", FlowRole.PROCESS),              // Acción/Proceso General
    SQUARE("cuadrado", FlowRole.PROCESS),                   // Proceso (variante cuadrada)
    PARALLELOGRAM("paralelogramo", FlowRole.INPUT),         // Entrada General (Entrada/Salida de datos)
    DIAMOND("rombo", FlowRole.DECISION),                   // Decisión
    STADIUM("estadio", FlowRole.TERMINAL),                 // Inicio/Final
    CIRCLE("círculo", FlowRole.CONNECTOR),                  // Conector (misma página)
    OVAL("óvalo", FlowRole.TERMINAL),                      // Elipse / Inicio/Fin alternativo
    CONNECTOR_PAGE("conector de página", FlowRole.CONNECTOR), // Conector fuera de página
    DISPLAY("salida en pantalla", FlowRole.OUTPUT),        // Salida en pantalla
    KEYBOARD_INPUT("entrada por teclado", FlowRole.INPUT),  // Entrada manual
    DOCUMENT("salida impresa", FlowRole.OUTPUT),           // Salida impresa / documento
    PREDEFINED_PROCESS("llamada a subrutina", FlowRole.PROCESS), // Subrutina
    LOOP("iteración", FlowRole.DECISION),                  // Hexágono de iteración
    TRIANGLE("triángulo", FlowRole.UNKNOWN),                // No estándar
    UNKNOWN("desconocido", FlowRole.UNKNOWN)
}
