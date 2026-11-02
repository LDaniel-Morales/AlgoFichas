package com.dan.opencv.interpreter

import com.dan.opencv.ocr.ParsedStatement
import com.dan.opencv.vision.FlowRole
import com.dan.opencv.vision.PositionedShape

/** Nodo ejecutable del diagrama detectado. */
data class FlowNode(
    val id: Int,
    val role: FlowRole,
    val statement: ParsedStatement,
    val label: String,
    val nextId: Int?
)

/** Programa lineal construido desde la posición inferida de las fichas. */
data class FlowProgram(
    val nodes: List<FlowNode>,
    val startId: Int?,
    val warnings: List<String> = emptyList()
)

object FlowProgramBuilder {

    fun build(positioned: List<PositionedShape>): FlowProgram {
        if (positioned.isEmpty()) {
            return FlowProgram(emptyList(), null, listOf("No se detectaron fichas ejecutables."))
        }

        val ordered = positioned.sortedWith(compareBy({ it.row }, { it.column }))
        val byId = positioned.associateBy { it.shape.id }
        val warnings = mutableListOf<String>()

        val nodes = ordered.mapIndexed { index, item ->
            val shape = item.shape
            val statement = parseStatement(shape)
            val isTerminalEnd = statement is ParsedStatement.Terminal && !statement.isStart
            val nextId = item.inferredNextId?.takeIf { byId.containsKey(it) }
                ?: if (!isTerminalEnd) ordered.getOrNull(index + 1)?.shape?.id else null
            FlowNode(
                id = shape.id,
                role = shape.role,
                statement = statement,
                label = shape.parsedInstruction ?: shape.role.title,
                nextId = nextId
            )
        }

        val starts = nodes.filter { it.role == FlowRole.TERMINAL && it.statement is ParsedStatement.Terminal && it.statement.isStart }
        val start = starts.firstOrNull() ?: nodes.firstOrNull()
        if (starts.isEmpty()) warnings += "No se encontró una ficha de Inicio; se usará la primera ficha."
        if (nodes.none { it.role == FlowRole.TERMINAL && it.statement is ParsedStatement.Terminal && !it.statement.isStart }) {
            warnings += "No se encontró una ficha de Fin."
        }
        if (nodes.any { it.statement is ParsedStatement.Generic && it.label.isBlank() }) {
            warnings += "Hay fichas cuyo texto no pudo interpretarse."
        }

        return FlowProgram(nodes, start?.id, warnings)
    }

    private fun parseStatement(shape: com.dan.opencv.vision.DetectedShape): ParsedStatement {
        val text = shape.parsedInstruction ?: shape.recognizedText.orEmpty()
        return com.dan.opencv.ocr.FichaInstructionParser.parse(text, shape.role)
    }
}
