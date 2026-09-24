package com.dan.opencv.retos

/** Los 5 tipos de ficha que usan los ejercicios (subconjunto simplificado de [com.dan.opencv.vision.ShapeType]). */
enum class FichaTipo(val nombre: String) {
    TERMINAL("Inicio / Fin"),
    ENTRADA("Leer dato"),
    PROCESO("Calcular"),
    DECISION("Pregunta sí/no"),
    SALIDA("Mostrar")
}

data class PiezaRequerida(val tipo: FichaTipo, val cantidad: Int)

data class Reto(
    val titulo: String,
    val nivel: String,
    val tiempo: String,
    val enunciado: String,
    val entra: String,
    val sale: String,
    val piezas: List<PiezaRequerida>,
    val pista: String
) {
    val totalFichas: Int get() = piezas.sumOf { it.cantidad }
    val fichasTxt: String get() = "$totalFichas fichas"
}

/** Los 3 retos del proyecto de diseño "AlgoFichas Vistas". */
val RETOS: List<Reto> = listOf(
    Reto(
        titulo = "Saluda a tu compañero",
        nivel = "Nivel 1",
        tiempo = "10 min",
        enunciado = "Haz un diagrama que pida el nombre de una persona y después le muestre un saludo con ese nombre.",
        entra = "Lupita",
        sale = "Hola, Lupita",
        piezas = listOf(
            PiezaRequerida(FichaTipo.TERMINAL, 2),
            PiezaRequerida(FichaTipo.ENTRADA, 1),
            PiezaRequerida(FichaTipo.SALIDA, 1)
        ),
        pista = "Primero se lee, después se muestra. El orden de las fichas importa."
    ),
    Reto(
        titulo = "Suma dos números",
        nivel = "Nivel 1",
        tiempo = "15 min",
        enunciado = "Pide dos números, súmalos y muestra el resultado. Por ejemplo, las gallinas de dos corrales.",
        entra = "4 y 3",
        sale = "7",
        piezas = listOf(
            PiezaRequerida(FichaTipo.TERMINAL, 2),
            PiezaRequerida(FichaTipo.ENTRADA, 2),
            PiezaRequerida(FichaTipo.PROCESO, 1),
            PiezaRequerida(FichaTipo.SALIDA, 1)
        ),
        pista = "Usa una ficha de Calcular para guardar la suma antes de mostrarla."
    ),
    Reto(
        titulo = "¿Llueve hoy?",
        nivel = "Nivel 2",
        tiempo = "15 min",
        enunciado = "Pregunta si está lloviendo. Si la respuesta es sí, muestra “Lleva paraguas”. Si es no, muestra “Sal a jugar”.",
        entra = "sí",
        sale = "Lleva paraguas",
        piezas = listOf(
            PiezaRequerida(FichaTipo.TERMINAL, 2),
            PiezaRequerida(FichaTipo.ENTRADA, 1),
            PiezaRequerida(FichaTipo.DECISION, 1),
            PiezaRequerida(FichaTipo.SALIDA, 2)
        ),
        pista = "La ficha de Pregunta tiene dos caminos. Cada camino termina en su propia ficha de Mostrar."
    )
)
