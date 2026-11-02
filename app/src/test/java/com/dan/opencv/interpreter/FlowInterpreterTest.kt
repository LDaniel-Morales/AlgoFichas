package com.dan.opencv.interpreter

import com.dan.opencv.ocr.ParsedStatement
import com.dan.opencv.vision.FlowRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowInterpreterTest {

    @Test
    fun testArithmeticEvaluation() {
        val vars = mapOf("a" to "10", "b" to "5", "nombre" to "ana")

        // Suma numérica
        assertEquals("15", FlowInterpreter.evaluate("a + b", vars))
        // Resta
        assertEquals("5", FlowInterpreter.evaluate("a - b", vars))
        // Multiplicación
        assertEquals("50", FlowInterpreter.evaluate("a * b", vars))
        // División
        assertEquals("2", FlowInterpreter.evaluate("a / b", vars))
        // Módulo
        assertEquals("0", FlowInterpreter.evaluate("a % b", vars))
        // Paréntesis
        assertEquals("30", FlowInterpreter.evaluate("(a + b) * 2", vars))
        // Concatenación de texto
        assertEquals("hola ana", FlowInterpreter.evaluate("\"hola \" + nombre", vars))
    }

    @Test
    fun testDivisionByZeroHandledGracefully() {
        val warnings = mutableListOf<String>()
        val result = FlowInterpreter.evaluate("10 / 0", emptyMap(), warnings)
        assertEquals("0", result)
        assertTrue(warnings.any { it.contains("cero") })
    }

    @Test
    fun testConditionEvaluation() {
        val vars = mapOf("x" to "20", "y" to "5")

        assertTrue(FlowInterpreter.evaluateCondition("x > 10", vars))
        assertFalse(FlowInterpreter.evaluateCondition("x < 10", vars))
        assertTrue(FlowInterpreter.evaluateCondition("x >= 20", vars))
        assertTrue(FlowInterpreter.evaluateCondition("y <= 5", vars))
        assertTrue(FlowInterpreter.evaluateCondition("x == 20", vars))
        assertTrue(FlowInterpreter.evaluateCondition("x = 20", vars)) // Soporte de = simple
        assertTrue(FlowInterpreter.evaluateCondition("x != 15", vars))
    }

    @Test
    fun testCompleteProgramExecution() {
        val nodes = listOf(
            FlowNode(1, FlowRole.TERMINAL, ParsedStatement.Terminal(isStart = true, raw = "inicio"), "Inicio", nextId = 2),
            FlowNode(2, FlowRole.INPUT, ParsedStatement.Input("n", "leer n"), "Leer n", nextId = 3),
            FlowNode(3, FlowRole.PROCESS, ParsedStatement.Assignment("doble", "n * 2", "doble = n * 2"), "doble = n * 2", nextId = 4),
            FlowNode(4, FlowRole.OUTPUT, ParsedStatement.Output("doble", "mostrar doble"), "Mostrar doble", nextId = 5),
            FlowNode(5, FlowRole.TERMINAL, ParsedStatement.Terminal(isStart = false, raw = "fin"), "Fin", nextId = null)
        )
        val program = FlowProgram(nodes, startId = 1)

        val inputs = mapOf("n" to "7")
        val result = FlowInterpreter.execute(program, inputs)

        assertTrue(result.completed)
        assertEquals(5, result.steps.size)
        assertEquals("14", result.variables["doble"])
        assertEquals(listOf("14"), result.output)
    }

    @Test
    fun testFlowCodeGenerator() {
        val nodes = listOf(
            FlowNode(1, FlowRole.TERMINAL, ParsedStatement.Terminal(isStart = true, raw = "inicio"), "Inicio", nextId = 2),
            FlowNode(2, FlowRole.INPUT, ParsedStatement.Input("x", "leer x"), "Leer x", nextId = 3),
            FlowNode(3, FlowRole.OUTPUT, ParsedStatement.Output("x", "imprimir x"), "Mostrar x", nextId = 4),
            FlowNode(4, FlowRole.TERMINAL, ParsedStatement.Terminal(isStart = false, raw = "fin"), "Fin", nextId = null)
        )
        val program = FlowProgram(nodes, startId = 1)

        val python = FlowCodeGenerator.toPython(program)
        assertTrue(python.contains("input("))
        assertTrue(python.contains("print(x)"))

        val pseudo = FlowCodeGenerator.toPseudocode(program)
        assertTrue(pseudo.contains("Algoritmo AlgoFichas"))
        assertTrue(pseudo.contains("Leer x"))
        assertTrue(pseudo.contains("Escribir x"))
        assertTrue(pseudo.contains("FinAlgoritmo"))
    }
}
