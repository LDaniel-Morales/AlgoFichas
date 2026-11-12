package com.dan.opencv.interpreter

import com.dan.opencv.ocr.ParsedStatement

/**
 * Generador de código fuente a partir de un [FlowProgram] detectado.
 * Produce código ejecutable en Python 3 y pseudocódigo escolar (estilo PSeInt).
 */
object FlowCodeGenerator {

    /**
     * Genera un script en Python 3 ejecutable y legible para estudiantes.
     */
    fun toPython(program: FlowProgram): String {
        if (program.nodes.isEmpty()) {
            return "# No se detectaron fichas en el diagrama.\n"
        }

        val sb = StringBuilder()
        sb.appendLine("# ===========================================")
        sb.appendLine("# Algoritmo generado con AlgoFichas (Python)")
        sb.appendLine("# ===========================================")
        sb.appendLine()

        val nodes = program.nodes

        for (node in nodes) {
            when (val stmt = node.statement) {
                is ParsedStatement.Terminal -> {
                    if (stmt.isStart) {
                        sb.appendLine("# --- Inicio del algoritmo ---")
                    } else {
                        sb.appendLine("# --- Fin del algoritmo ---")
                    }
                }
                is ParsedStatement.Input -> {
                    val varName = stmt.variableName
                    sb.appendLine("# Leer entrada: $varName")
                    sb.appendLine("try:")
                    sb.appendLine("    $varName = float(input(\"Ingrese $varName: \"))")
                    sb.appendLine("    if ${varName}.is_integer():")
                    sb.appendLine("        $varName = int($varName)")
                    sb.appendLine("except ValueError:")
                    sb.appendLine("    pass  # se mantiene como texto si no es numérico")
                }
                is ParsedStatement.Assignment -> {
                    sb.appendLine("${stmt.targetVar} = ${stmt.expression}")
                }
                is ParsedStatement.Output -> {
                    sb.appendLine("print(${stmt.expression})")
                }
                is ParsedStatement.Decision -> {
                    sb.appendLine("if ${stmt.condition}:")
                    sb.appendLine("    print(\"[Condición '${stmt.condition}' cumplida]\")")
                }
                is ParsedStatement.Generic -> {
                    if (stmt.text.isNotBlank()) {
                        sb.appendLine("# ${stmt.text}")
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Genera pseudocódigo estándar en español (compatible con PSeInt).
     */
    fun toPseudocode(program: FlowProgram): String {
        if (program.nodes.isEmpty()) {
            return "// No se detectaron fichas en el diagrama.\n"
        }

        val sb = StringBuilder()
        sb.appendLine("Algoritmo AlgoFichas")

        for (node in program.nodes) {
            when (val stmt = node.statement) {
                is ParsedStatement.Terminal -> {
                    if (stmt.isStart) {
                        sb.appendLine("    // Inicio")
                    } else {
                        sb.appendLine("    // Fin")
                    }
                }
                is ParsedStatement.Input -> {
                    sb.appendLine("    Leer ${stmt.variableName}")
                }
                is ParsedStatement.Assignment -> {
                    sb.appendLine("    ${stmt.targetVar} <- ${stmt.expression}")
                }
                is ParsedStatement.Output -> {
                    sb.appendLine("    Escribir ${stmt.expression}")
                }
                is ParsedStatement.Decision -> {
                    sb.appendLine("    Si ${stmt.condition} Entonces")
                    sb.appendLine("        // Instrucciones de la rama")
                    sb.appendLine("    FinSi")
                }
                is ParsedStatement.Generic -> {
                    if (stmt.text.isNotBlank()) {
                        sb.appendLine("    // ${stmt.text}")
                    }
                }
            }
        }

        sb.appendLine("FinAlgoritmo")
        return sb.toString()
    }
}
