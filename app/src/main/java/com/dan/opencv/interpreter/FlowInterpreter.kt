package com.dan.opencv.interpreter

import com.dan.opencv.ocr.ParsedStatement

data class ExecutionStep(
    val nodeId: Int,
    val label: String,
    val detail: String,
    val variables: Map<String, String>
)

data class ExecutionResult(
    val steps: List<ExecutionStep>,
    val variables: Map<String, String>,
    val output: List<String>,
    val warnings: List<String> = emptyList(),
    val completed: Boolean
)

/**
 * Intérprete para ejecutar el diagrama de flujo detectado por AlgoFichas.
 * Soporta operaciones aritméticas (+, -, *, /, %), variables, condicionales
 * y control paso a paso para fines pedagógicos en entornos sin conexión.
 */
object FlowInterpreter {

    private const val MAX_STEPS = 100

    fun requiredInputs(program: FlowProgram): List<String> = program.nodes
        .mapNotNull { (it.statement as? ParsedStatement.Input)?.variableName }
        .filter { it.isNotBlank() }
        .distinct()

    fun execute(program: FlowProgram, inputValues: Map<String, String> = emptyMap()): ExecutionResult {
        if (program.nodes.isEmpty() || program.startId == null) {
            return ExecutionResult(emptyList(), emptyMap(), emptyList(), program.warnings, false)
        }

        val nodes = program.nodes.associateBy { it.id }
        val variables = inputValues.mapKeys { it.key.lowercase() }.toMutableMap()
        val output = mutableListOf<String>()
        val steps = mutableListOf<ExecutionStep>()
        val warnings = program.warnings.toMutableList()
        val visited = mutableSetOf<Int>()
        var currentId: Int? = program.startId
        var completed = false

        while (steps.size < MAX_STEPS) {
            val node = currentId?.let(nodes::get) ?: break
            if (!visited.add(node.id)) {
                warnings += "Se detectó un ciclo en el diagrama; se detuvo la ejecución."
                break
            }

            val detail = when (val statement = node.statement) {
                is ParsedStatement.Terminal -> if (statement.isStart) "Comienza el algoritmo" else "Termina el algoritmo"
                is ParsedStatement.Input -> {
                    val value = variables[statement.variableName]
                    if (value == null) {
                        warnings += "La entrada '${statement.variableName}' no tiene un valor proporcionado; se usa '0'."
                        variables[statement.variableName] = "0"
                    }
                    "${statement.variableName} = ${variables[statement.variableName]}"
                }
                is ParsedStatement.Assignment -> {
                    val value = evaluate(statement.expression, variables, warnings)
                    variables[statement.targetVar] = value
                    "${statement.targetVar} = $value"
                }
                is ParsedStatement.Output -> {
                    val value = evaluate(statement.expression, variables, warnings)
                    output += value
                    "Pantalla: $value"
                }
                is ParsedStatement.Decision -> {
                    val result = evaluateCondition(statement.condition, variables)
                    "${statement.condition} → ${if (result) "Sí" else "No"}"
                }
                is ParsedStatement.Generic -> {
                    if (statement.text.isNotBlank()) statement.text else "Texto no interpretable"
                }
            }

            steps += ExecutionStep(node.id, node.label, detail, variables.toMap())
            val statement = node.statement
            if (statement is ParsedStatement.Terminal && !statement.isStart) {
                completed = true
                break
            }
            currentId = node.nextId
            if (currentId == null) {
                warnings += "La ficha '${node.label}' no tiene una siguiente conexión."
                break
            }
        }

        if (steps.size >= MAX_STEPS) warnings += "Se alcanzó el límite de pasos de seguridad."
        return ExecutionResult(steps, variables.toMap(), output.toList(), warnings.distinct(), completed)
    }

    fun evaluate(expression: String, variables: Map<String, String>, warnings: MutableList<String>? = null): String {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return ""
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.drop(1).dropLast(1)
        }

        return try {
            val parser = SimpleExprParser(trimmed, variables, warnings)
            parser.parse()
        } catch (_: Exception) {
            evaluateAtom(trimmed, variables)
        }
    }

    private fun evaluateAtom(atom: String, variables: Map<String, String>): String {
        val value = atom.trim()
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.drop(1).dropLast(1)
        }
        return variables[value.lowercase()] ?: value
    }

    fun evaluateCondition(condition: String, variables: Map<String, String>): Boolean {
        val trimmed = condition.trim()
        val match = Regex("(.+?)\\s*(==|!=|<=|>=|<>|=|<|>)\\s*(.+)").matchEntire(trimmed)
            ?: return evaluate(trimmed, variables).lowercase() in setOf("sí", "si", "true", "verdadero", "1")

        val leftRaw = evaluate(match.groupValues[1], variables)
        val op = match.groupValues[2]
        val rightRaw = evaluate(match.groupValues[3], variables)

        val leftNum = leftRaw.toDoubleOrNull()
        val rightNum = rightRaw.toDoubleOrNull()

        return if (leftNum != null && rightNum != null) {
            when (op) {
                "==", "=" -> leftNum == rightNum
                "!=", "<>" -> leftNum != rightNum
                "<" -> leftNum < rightNum
                ">" -> leftNum > rightNum
                "<=" -> leftNum <= rightNum
                ">=" -> leftNum >= rightNum
                else -> false
            }
        } else {
            when (op) {
                "==", "=" -> leftRaw.equals(rightRaw, ignoreCase = true)
                "!=", "<>" -> !leftRaw.equals(rightRaw, ignoreCase = true)
                else -> false
            }
        }
    }

    private fun formatNumber(num: Double): String {
        return if (num % 1.0 == 0.0) num.toLong().toString() else num.toString()
    }

    /**
     * Parser aritmético recursivo liviano para expresiones matemáticas y de texto.
     */
    private class SimpleExprParser(
        private val input: String,
        private val variables: Map<String, String>,
        private val warnings: MutableList<String>?
    ) {
        private var pos = 0

        fun parse(): String {
            val res = parseExpression()
            skipWhitespace()
            return res
        }

        private fun parseExpression(): String {
            var left = parseTerm()
            while (true) {
                skipWhitespace()
                if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
                    val op = input[pos]
                    pos++
                    val right = parseTerm()
                    left = applyBinaryOp(left, op, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): String {
            var left = parseFactor()
            while (true) {
                skipWhitespace()
                if (pos < input.length && (input[pos] == '*' || input[pos] == '/' || input[pos] == '%')) {
                    val op = input[pos]
                    pos++
                    val right = parseFactor()
                    left = applyBinaryOp(left, op, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseFactor(): String {
            skipWhitespace()
            if (pos < input.length && (input[pos] == '+' || input[pos] == '-')) {
                val sign = input[pos]
                pos++
                val primary = parsePrimary()
                val num = primary.toDoubleOrNull()
                return if (num != null) {
                    formatNumber(if (sign == '-') -num else num)
                } else {
                    primary
                }
            }
            return parsePrimary()
        }

        private fun parsePrimary(): String {
            skipWhitespace()
            if (pos >= input.length) return ""

            // Paréntesis
            if (input[pos] == '(') {
                pos++
                val res = parseExpression()
                skipWhitespace()
                if (pos < input.length && input[pos] == ')') pos++
                return res
            }

            // String con comillas
            if (input[pos] == '"' || input[pos] == '\'') {
                val quote = input[pos]
                pos++
                val start = pos
                while (pos < input.length && input[pos] != quote) pos++
                val str = input.substring(start, pos)
                if (pos < input.length && input[pos] == quote) pos++
                return str
            }

            // Número
            if (input[pos].isDigit() || (input[pos] == '.' && pos + 1 < input.length && input[pos + 1].isDigit())) {
                val start = pos
                while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
                return input.substring(start, pos)
            }

            // Identificador (variable o texto)
            if (input[pos].isLetter() || input[pos] == '_') {
                val start = pos
                while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
                val id = input.substring(start, pos).lowercase()
                return variables[id] ?: id
            }

            // Caracter inesperado
            val ch = input[pos].toString()
            pos++
            return ch
        }

        private fun applyBinaryOp(left: String, op: Char, right: String): String {
            val leftNum = left.toDoubleOrNull()
            val rightNum = right.toDoubleOrNull()

            if (op == '+') {
                if (leftNum != null && rightNum != null) {
                    return formatNumber(leftNum + rightNum)
                }
                return left + right
            }

            if (leftNum != null && rightNum != null) {
                return when (op) {
                    '-' -> formatNumber(leftNum - rightNum)
                    '*' -> formatNumber(leftNum * rightNum)
                    '/' -> {
                        if (rightNum == 0.0) {
                            warnings?.add("División entre cero en la expresión.")
                            "0"
                        } else {
                            formatNumber(leftNum / rightNum)
                        }
                    }
                    '%' -> {
                        if (rightNum == 0.0) {
                            warnings?.add("Módulo con divisor cero.")
                            "0"
                        } else {
                            formatNumber(leftNum % rightNum)
                        }
                    }
                    else -> left
                }
            }
            return "$left $op $right"
        }

        private fun skipWhitespace() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }

        private fun formatNumber(num: Double): String {
            return if (num % 1.0 == 0.0) num.toLong().toString() else num.toString()
        }
    }
}
