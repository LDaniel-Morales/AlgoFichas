package com.dan.opencv.vision

/**
 * Roles estándar de las figuras en un diagrama de flujo según la norma ANSI/ISO
 * y la pedagogía escolar de algoritmos.
 */
enum class FlowRole(
    val title: String,
    val description: String
) {
    TERMINAL(
        title = "Inicio / Fin",
        description = "Marca el inicio o término del diagrama de flujo."
    ),
    INPUT(
        title = "Leer dato (Entrada)",
        description = "Lectura o ingreso de variables desde el exterior."
    ),
    PROCESS(
        title = "Calcular / Asignar (Proceso)",
        description = "Operación matemática, asignación de variable o transformación interna."
    ),
    DECISION(
        title = "Pregunta sí/no (Decisión)",
        description = "Bifurcación lógica condicional evaluando una expresión booleana."
    ),
    OUTPUT(
        title = "Mostrar dato (Salida)",
        description = "Impresión de resultados o mensajes al usuario."
    ),
    CONNECTOR(
        title = "Conector de flujo",
        description = "Une partes del diagrama en la misma o diferente sección."
    ),
    UNKNOWN(
        title = "No estándar",
        description = "Forma geométrica no clasificada dentro del estándar básico."
    )
}
