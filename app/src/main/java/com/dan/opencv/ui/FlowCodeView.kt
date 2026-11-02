package com.dan.opencv.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.opencv.interpreter.FlowCodeGenerator
import com.dan.opencv.interpreter.FlowProgram
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.IbmPlexMono
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio

enum class CodeLanguage(val title: String) {
    PYTHON("Python 3"),
    PSEUDOCODE("Pseudocódigo")
}

/**
 * Visor de código generado a partir del diagrama detectado.
 * Permite alternar entre Python 3 y Pseudocódigo estándar (PSeInt)
 * y copiar el código al portapapeles sin conexión.
 */
@Composable
fun FlowCodeView(
    program: FlowProgram,
    modifier: Modifier = Modifier
) {
    var selectedLanguage by remember { mutableStateOf(CodeLanguage.PYTHON) }
    val context = LocalContext.current

    val code = remember(program, selectedLanguage) {
        when (selectedLanguage) {
            CodeLanguage.PYTHON -> FlowCodeGenerator.toPython(program)
            CodeLanguage.PSEUDOCODE -> FlowCodeGenerator.toPseudocode(program)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Selector de Lenguaje + Botón Copiar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CodeLanguage.entries.forEach { lang ->
                    val isSelected = lang == selectedLanguage
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) VerdeFuerte else TarjetaFondo)
                            .border(1.dp, VerdeFuerte, RoundedCornerShape(8.dp))
                            .clickable { selectedLanguage = lang }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = lang.title,
                            color = if (isSelected) Fondo else VerdeFuerte,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brote)
                    .border(1.dp, VerdeMedio, RoundedCornerShape(8.dp))
                    .clickable {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("AlgoFichas Code", code)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "Código copiado al portapapeles", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "📋 Copiar",
                    color = VerdeFuerte,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Ventana de código con estilo consola
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(VerdeFuerte)
                .padding(14.dp)
        ) {
            val horizontalScrollState = rememberScrollState()
            Text(
                text = code,
                fontFamily = IbmPlexMono,
                fontSize = 12.sp,
                color = Brote,
                lineHeight = 18.sp,
                modifier = Modifier.horizontalScroll(horizontalScrollState)
            )
        }

        Text(
            text = if (selectedLanguage == CodeLanguage.PYTHON) {
                "💡 Puedes copiar este código y ejecutarlo en cualquier intérprete Python sin internet."
            } else {
                "💡 Formato compatible con PSeInt para clase de lógica y algoritmos."
            },
            fontSize = 11.sp,
            color = VerdeFuerte.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
