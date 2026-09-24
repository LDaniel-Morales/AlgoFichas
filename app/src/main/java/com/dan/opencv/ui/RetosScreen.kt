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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.opencv.retos.RETOS
import com.dan.opencv.retos.Reto
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.Literata
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio

/**
 * Biblioteca de retos, basada en la pantalla "02 Retos" del diseño "AlgoFichas Vistas".
 * Adaptación: en el mockup la tarjeta de "Antes de empezar" queda fija al fondo (margin-top:
 * auto) porque el lienzo tiene alto fijo; aquí el contenido es desplazable (verticalScroll)
 * para que quepa en pantallas reales de cualquier tamaño, así que esa tarjeta simplemente
 * queda después de la lista en vez de forzada al fondo.
 * Los chips "Todos/Sin hacer/Hechos" son decorativos -el diseño de origen tampoco les
 * conecta una acción-, no hay todavía una noción de reto "hecho" que filtrar.
 */
@Composable
fun RetosScreen(onOpenReto: (Int) -> Unit, onNavigate: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().background(Fondo)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text("Retos", fontFamily = Literata, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = VerdeFuerte)
            Spacer(Modifier.height(4.dp))
            Text(
                "Elige un ejercicio y arma su diagrama con tus fichas.",
                style = MaterialTheme.typography.bodyLarge,
                color = VerdeFuerte.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(18.dp))
            FilterChipsRow()
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RETOS.forEachIndexed { index, reto ->
                    RetoCard(index = index, reto = reto, isGreen = index % 2 == 0, onClick = { onOpenReto(index) })
                }
            }
            Spacer(Modifier.height(16.dp))
            NoteCard(
                title = "Antes de empezar",
                body = "Ten a la mano las fichas de Inicio y Fin: todos los retos las usan."
            )
            Spacer(Modifier.height(14.dp))
        }
        AppBottomNavBar(active = NavTab.RETOS, onSelect = onNavigate)
    }
}

@Composable
private fun FilterChipsRow(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(VerdeFuerte)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text("Todos · ${RETOS.size}", color = Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        FilterChipOutline("Sin hacer")
        FilterChipOutline("Hechos")
    }
}

@Composable
private fun FilterChipOutline(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .border(2.dp, VerdeFuerte.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(text, color = VerdeFuerte, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun RetoCard(index: Int, reto: Reto, isGreen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isGreen) VerdeMedio else VerdeFuerte)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "%02d".format(index + 1),
            fontFamily = Literata,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 30.sp,
            color = Brote
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                reto.titulo,
                fontFamily = Literata,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                color = Fondo
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Brote).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text(reto.nivel, color = VerdeFuerte, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.5.dp, Fondo.copy(alpha = 0.6f), RoundedCornerShape(50))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(reto.fichasTxt, color = Fondo, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Text("›", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Fondo)
    }
}

