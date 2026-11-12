package com.dan.opencv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.opencv.interpreter.FlowNode
import com.dan.opencv.interpreter.FlowProgram
import com.dan.opencv.ocr.ParsedStatement
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Carton
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.IbmPlexMono
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio
import com.dan.opencv.vision.FlowRole

/**
 * Diagrama de flujo visual e interactivo.
 * Representa los bloques estándar de algoritmos con sus formas canónicas,
 * flechas de conexión y resaltado en tiempo real durante la ejecución paso a paso.
 */
@Composable
fun FlowchartVisualizer(
    program: FlowProgram,
    activeNodeId: Int? = null,
    onEditNode: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (program.nodes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No hay fichas para mostrar en el diagrama.",
                color = VerdeFuerte.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        program.nodes.forEachIndexed { index, node ->
            val isActive = node.id == activeNodeId
            FlowchartNodeItem(
                node = node,
                stepNumber = index + 1,
                isActive = isActive,
                onClick = { onEditNode(node.id) }
            )

            // Conector con flecha hacia el siguiente nodo si no es el último
            if (index < program.nodes.size - 1) {
                FlowchartArrow(label = if (node.role == FlowRole.DECISION) "flujo" else null)
            }
        }
    }
}

@Composable
private fun FlowchartNodeItem(
    node: FlowNode,
    stepNumber: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) VerdeMedio else VerdeFuerte.copy(alpha = 0.85f),
        label = "nodeBorder"
    )
    val containerBg by animateColorAsState(
        targetValue = if (isActive) Brote else TarjetaFondo,
        label = "nodeBg"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        // Encabezado de paso con distintivo si está activo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isActive) VerdeMedio else VerdeFuerte),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$stepNumber",
                    color = Fondo,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = node.role.title,
                fontFamily = IbmPlexMono,
                fontSize = 11.sp,
                color = VerdeFuerte.copy(alpha = 0.75f),
                fontWeight = FontWeight.Bold
            )

            if (isActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VerdeMedio)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "▶ Activo",
                        color = Fondo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Toca para editar ✎",
                fontSize = 10.sp,
                color = VerdeMedio,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Bloque visual según la forma canónica de diagrama de flujo
        when (node.role) {
            FlowRole.TERMINAL -> {
                // Stadium / Pill para Inicio y Fin
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .clip(RoundedCornerShape(50))
                        .background(containerBg)
                        .border(if (isActive) 3.dp else 2.dp, borderColor, RoundedCornerShape(50))
                        .padding(vertical = 12.dp, horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.label.ifBlank { if ((node.statement as? ParsedStatement.Terminal)?.isStart == false) "Fin" else "Inicio" },
                        color = VerdeFuerte,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            FlowRole.INPUT -> {
                // Entrada (Paralelogramo / Romboide)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerBg)
                        .border(if (isActive) 3.dp else 2.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "▱ ENTRADA",
                            fontFamily = IbmPlexMono,
                            fontSize = 10.sp,
                            color = VerdeMedio,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = node.label,
                            color = VerdeFuerte,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            FlowRole.PROCESS -> {
                // Rectángulo clásico para Operaciones / Procesos
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(containerBg)
                        .border(if (isActive) 3.dp else 2.dp, borderColor, RoundedCornerShape(4.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "□ PROCESO",
                            fontFamily = IbmPlexMono,
                            fontSize = 10.sp,
                            color = Carton,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = node.label,
                            color = VerdeFuerte,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            FlowRole.DECISION -> {
                // Rombo para Decisión / Condicional
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(containerBg)
                        .border(if (isActive) 3.dp else 2.dp, borderColor, RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "◇ DECISIÓN",
                            fontFamily = IbmPlexMono,
                            fontSize = 10.sp,
                            color = VerdeFuerte,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (node.label.startsWith("¿")) node.label else "¿${node.label}?",
                            color = VerdeFuerte,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            FlowRole.OUTPUT -> {
                // Salida / Impresión
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.82f)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 6.dp))
                        .background(containerBg)
                        .border(
                            if (isActive) 3.dp else 2.dp,
                            borderColor,
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 6.dp)
                        )
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⬟ SALIDA",
                            fontFamily = IbmPlexMono,
                            fontSize = 10.sp,
                            color = VerdeMedio,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = node.label,
                            color = VerdeFuerte,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            FlowRole.CONNECTOR -> {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(containerBg)
                        .border(if (isActive) 3.dp else 2.dp, borderColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.label.ifBlank { "○" },
                        color = VerdeFuerte,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            FlowRole.UNKNOWN -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(containerBg)
                        .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(vertical = 10.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.label,
                        color = VerdeFuerte,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Flecha conectora entre bloques de diagrama de flujo.
 */
@Composable
private fun FlowchartArrow(label: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height(18.dp)
                .background(VerdeFuerte.copy(alpha = 0.5f))
        )
        Text(
            text = "▼",
            color = VerdeFuerte.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 0.dp)
        )
        if (label != null) {
            Text(
                text = label,
                fontFamily = IbmPlexMono,
                fontSize = 9.sp,
                color = VerdeFuerte.copy(alpha = 0.6f)
            )
        }
    }
}
