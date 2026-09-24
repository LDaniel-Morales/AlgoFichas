package com.dan.opencv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dan.opencv.retos.PiezaRequerida
import com.dan.opencv.retos.RETOS
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Caveat
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.IbmPlexMono
import com.dan.opencv.ui.theme.Literata
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio

/**
 * Detalle de un reto, basado en la pantalla "03 Detalle del reto" del diseño
 * "AlgoFichas Vistas". Igual que en Retos, el contenido es desplazable en vez de fijo a un
 * alto de lienzo, y el botón final queda fuera del scroll (fijo) como en el mockup.
 */
@Composable
fun RetoDetailScreen(
    retoIndex: Int,
    onBack: () -> Unit,
    onScanSolution: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reto = RETOS.getOrNull(retoIndex) ?: RETOS.first()

    Column(modifier = modifier.fillMaxSize().background(Fondo)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(VerdeMedio)
                .padding(horizontal = 22.dp)
                .padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .border(2.dp, Fondo, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Fondo, fontSize = 22.sp)
                }
                Text(
                    "Reto ${retoIndex + 1} de ${RETOS.size}",
                    color = Fondo.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Text(
                reto.titulo,
                fontFamily = Literata,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                color = Fondo
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Brote).padding(horizontal = 11.dp, vertical = 5.dp)) {
                    Text(reto.nivel, color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                }
                OutlineTagOnGreen(reto.fichasTxt)
                OutlineTagOnGreen(reto.tiempo)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SeccionLabel("Enunciado")
                Text(reto.enunciado, fontSize = 17.sp, lineHeight = 26.sp, color = VerdeFuerte)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EntradaSalidaCard(
                    label = "Si entra",
                    value = reto.entra,
                    background = TarjetaFondo,
                    outlined = true,
                    modifier = Modifier.weight(1f)
                )
                EntradaSalidaCard(
                    label = "Debe salir",
                    value = reto.sale,
                    background = Brote,
                    outlined = false,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SeccionLabel("Fichas que necesitas")
                Column {
                    reto.piezas.forEach { pieza -> PiezaRow(pieza) }
                }
            }

            NoteCard(title = "Pista", body = reto.pista)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Fondo)
                .padding(horizontal = 22.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            PhysicalButton(
                text = "Escanear mi solución",
                onClick = onScanSolution,
                modifier = Modifier.fillMaxWidth()
            ) { CameraIcon() }
        }
    }
}

@Composable
private fun SeccionLabel(text: String) {
    Text(
        text.uppercase(),
        fontFamily = IbmPlexMono,
        fontSize = 11.sp,
        letterSpacing = 0.12f.em,
        color = VerdeMedio
    )
}

@Composable
private fun OutlineTagOnGreen(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(1.5.dp, Fondo.copy(alpha = 0.7f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(text, color = Fondo, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun EntradaSalidaCard(
    label: String,
    value: String,
    background: Color,
    outlined: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (outlined) Modifier.border(2.dp, VerdeFuerte.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                else Modifier
            )
            .background(background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeFuerte.copy(alpha = 0.72f))
        Text(value, fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = VerdeFuerte)
    }
}

@Composable
private fun PiezaRow(pieza: PiezaRequerida) {
    val dashColor = VerdeFuerte.copy(alpha = 0.2f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .drawBehind {
                drawLine(
                    color = dashColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FichaShapeIcon(pieza.tipo)
        Text(pieza.tipo.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = VerdeFuerte, modifier = Modifier.weight(1f))
        Text("×${pieza.cantidad}", fontFamily = IbmPlexMono, fontSize = 14.sp, color = VerdeFuerte.copy(alpha = 0.75f))
    }
}
