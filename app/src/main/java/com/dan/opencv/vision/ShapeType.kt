package com.dan.opencv.vision

/**
 * Tipos de ficha de diagrama de flujo que reconocemos por geometría del contorno.
 * La correspondencia con el significado estándar de flowchart se deja como comentario.
 * Basado en el set de referencia de 11 fichas (ver memoria del proyecto).
 */
enum class ShapeType(val label: String) {
    RECTANGLE("rectángulo"),              // Acción/Proceso General
    SQUARE("cuadrado"),                   // Proceso (variante cuadrada; no es una ficha
                                           // distinta en el set de referencia, pero se
                                           // conserva la distinción geométrica)
    PARALLELOGRAM("paralelogramo"),       // Entrada General (Entrada/Salida de datos)
    DIAMOND("rombo"),                     // Decisión
    STADIUM("estadio"),                   // Inicio/Final
    CIRCLE("círculo"),                    // Conector (misma página)
    OVAL("óvalo"),                        // Elipse alargada genérica (no está en el set de
                                           // referencia; se deja como clasificación general)
    CONNECTOR_PAGE("conector de página"), // Conector (páginas diferentes) - pentágono con
                                           // la punta hacia abajo
    DISPLAY("salida en pantalla"),        // pentágono con la punta hacia la derecha
    KEYBOARD_INPUT("entrada por teclado"),// rectángulo con borde superior dentado
    DOCUMENT("salida impresa"),           // rectángulo con borde inferior ondulado
    PREDEFINED_PROCESS("llamada a subrutina"), // rectángulo con dos líneas verticales internas
    LOOP("iteración"),                    // hexágono alargado (no valida la línea punteada
                                           // + círculo que lo acompaña en el símbolo completo)
    TRIANGLE("triángulo"),                // (poco común en flowcharts, se detecta igual)
    UNKNOWN("desconocido")
}
