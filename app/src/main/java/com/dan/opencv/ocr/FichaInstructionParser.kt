package com.dan.opencv.ocr

import com.dan.opencv.vision.FlowRole

/**
 * Representación sintáctica de una instrucción identificada en una ficha.
 */
sealed class ParsedStatement {
    data class Terminal(val isStart: Boolean, val raw: String) : ParsedStatement()
    data class Input(val variableName: String, val raw: String) : ParsedStatement()
    data class Assignment(val targetVar: String, val expression: String, val raw: String) : ParsedStatement()
    data class Decision(val condition: String, val raw: String) : ParsedStatement()
    data class Output(val expression: String, val raw: String) : ParsedStatement()
    data class Generic(val text: String) : ParsedStatement()
}

/**
 * Parser de lenguaje natural estructurado para fichas escolares de algoritmos.
 * Convierte expresiones como:
 * - "Leer X", "Entrada A", "Pedir nombre" -> Input
 * - "X = A + B", "Suma = 0", "Contador = Contador + 1" -> Assignment
 * - "X > 10", "¿X == 0?", "A <= B" -> Decision
 * - "Imprimir X", "Mostrar 'Hola'", "Escribir Resultado" -> Output
 * - "Inicio", "Fin", "Start", "End" -> Terminal
 */
object FichaInstructionParser {

    private val REGEX_READ = Regex("(?i)^(?:leer|lee|pide|pedir|entrada|input)\\s+([a-zA-Z_][a-zA-Z0-9_]*)")
    private val REGEX_PRINT = Regex("(?i)^(?:imprimir|imprime|mostrar|muestra|escribir|escribe|print|salida)\\s+(.+)")
    private val REGEX_ASSIGNMENT = Regex("(?i)^([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:=|:=|<-|←)\\s*(.+)")
    private val REGEX_START = Regex("(?i)^(?:inicio|start|comienzo|empezar)")
    private val REGEX_END = Regex("(?i)^(?:fin|end|termina|final)")

    fun parse(rawText: String, expectedRole: FlowRole = FlowRole.UNKNOWN): ParsedStatement {
        val trimmed = rawText.trim().replace(Regex("[¿?¡!]"), "").trim()
        if (trimmed.isEmpty()) return ParsedStatement.Generic("")

        // 1. Terminal (Inicio / Fin)
        if (REGEX_START.containsMatchIn(trimmed) || (expectedRole == FlowRole.TERMINAL && !REGEX_END.containsMatchIn(trimmed))) {
            return ParsedStatement.Terminal(isStart = true, raw = trimmed)
        }
        if (REGEX_END.containsMatchIn(trimmed)) {
            return ParsedStatement.Terminal(isStart = false, raw = trimmed)
        }

        // 2. Lectura / Entrada
        REGEX_READ.find(trimmed)?.let { match ->
            val variable = match.groupValues[1]
            return ParsedStatement.Input(variableName = variable, raw = trimmed)
        }
        if (expectedRole == FlowRole.INPUT && !trimmed.contains("=") && !REGEX_PRINT.containsMatchIn(trimmed)) {
            val words = trimmed.split(Regex("\\s+"))
            val variableCandidate = words.lastOrNull()?.filter { it.isLetterOrDigit() || it == '_' } ?: trimmed
            return ParsedStatement.Input(variableName = variableCandidate, raw = trimmed)
        }

        // 3. Salida / Impresión
        REGEX_PRINT.find(trimmed)?.let { match ->
            val expr = match.groupValues[1]
            return ParsedStatement.Output(expression = expr, raw = trimmed)
        }
        if (expectedRole == FlowRole.OUTPUT && !trimmed.contains("=")) {
            return ParsedStatement.Output(expression = trimmed, raw = trimmed)
        }

        // 4. Asignación / Proceso
        REGEX_ASSIGNMENT.find(trimmed)?.let { match ->
            val target = match.groupValues[1].trim()
            val expr = match.groupValues[2].trim()
            return ParsedStatement.Assignment(targetVar = target, expression = expr, raw = trimmed)
        }

        // 5. Decisión / Condición
        if (expectedRole == FlowRole.DECISION || trimmed.contains(Regex("[><=]|==|!=|<=|>="))) {
            val cond = trimmed.replace(Regex("(?i)^(?:si|if)\\s+"), "")
            return ParsedStatement.Decision(condition = cond, raw = trimmed)
        }

        return ParsedStatement.Generic(trimmed)
    }

    /**
     * Entrega una etiqueta amigable y legible para mostrar en pantalla.
     */
    fun formatDisplay(statement: ParsedStatement): String = when (statement) {
        is ParsedStatement.Terminal -> if (statement.isStart) "Inicio" else "Fin"
        is ParsedStatement.Input -> "Leer ${statement.variableName}"
        is ParsedStatement.Assignment -> "${statement.targetVar} = ${statement.expression}"
        is ParsedStatement.Decision -> "¿${statement.condition}?"
        is ParsedStatement.Output -> "Mostrar ${statement.expression}"
        is ParsedStatement.Generic -> statement.text
    }
}
