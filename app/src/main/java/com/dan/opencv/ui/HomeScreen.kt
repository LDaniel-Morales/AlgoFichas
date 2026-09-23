package com.dan.opencv.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.opencv.R
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Caveat
import com.dan.opencv.ui.theme.Carton
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.Literata
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio
import com.dan.opencv.ui.theme.VerdeMedioPresionado

/**
 * Pantalla de inicio de AlgoFichas, basada en el proyecto de diseño "AlgoFichas App"
 * (claude.ai/design, paleta verde). Adaptaciones deliberadas respecto al mockup:
 *  - El mockup mostraba la pantalla dentro de un "marco de teléfono" con una barra de
 *    estado falsa (hora, batería) y un indicador de gestos al pie: eso es decoración del
 *    marco, no contenido de la app, así que aquí se usan los insets reales del sistema en
 *    su lugar (`WindowInsets.statusBars` / `navigationBars`).
 *  - El botón "Menú" y las pestañas "Retos"/"Grupo" quedan como marcadores visuales sin
 *    acción todavía: el diseño de origen solo cubre esta pantalla de Inicio.
 */
@Composable
fun HomeScreen(onScanClick: () -> Unit, modifier: Modifier = Modifier) {
    var modo by rememberSaveable { mutableStateOf(UserMode.DOCENTE) }

    Column(modifier = modifier.fillMaxSize().background(Fondo)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            HomeHeader()
            Spacer(Modifier.height(18.dp))
            ModeToggle(
                modo = modo,
                onDocente = { modo = UserMode.DOCENTE },
                onEstudiante = { modo = UserMode.ESTUDIANTE }
            )
            Spacer(Modifier.height(18.dp))
            GreetingBlock(modo)
            Spacer(Modifier.height(18.dp))
            FichaIllustration(modifier = Modifier.fillMaxWidth().height(260.dp))
            Spacer(Modifier.weight(1f, fill = true))
            ScanButton(onClick = onScanClick, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
        }
        BottomNavBar()
    }
}

private enum class UserMode { DOCENTE, ESTUDIANTE }

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(
                painter = painterResource(R.drawable.ic_algofichas_logo),
                contentDescription = null,
                colorFilter = ColorFilter.tint(VerdeMedio),
                modifier = Modifier.size(34.dp)
            )
            AlgoFichasWordmark(fontSize = 24.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OfflineBadge()
            MenuButton(onClick = { /* pendiente: menú lateral, no está en este diseño */ })
        }
    }
}

/** El logo "AlgoFichas": la "o" de "Algo" es un círculo con 3 fichas diminutas (proceso-decisión-proceso). */
@Composable
fun AlgoFichasWordmark(fontSize: TextUnit, color: Color = VerdeMedio, modifier: Modifier = Modifier) {
    val circleSize = (fontSize.value * 0.56f).dp
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text("Alg", fontFamily = Literata, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = color)
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .offset(y = -(circleSize * 0.18f))
                .size(circleSize)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(circleSize * 0.05f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = circleSize * 0.22f, height = circleSize * 0.15f).clip(RoundedCornerShape(1.dp)).background(Fondo))
                Box(Modifier.size(circleSize * 0.17f).graphicsLayer(rotationZ = 45f).background(Fondo))
                Box(Modifier.size(width = circleSize * 0.22f, height = circleSize * 0.15f).clip(RoundedCornerShape(1.dp)).background(Fondo))
            }
        }
        Text("Fichas", fontFamily = Literata, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = color)
    }
}

@Composable
private fun OfflineBadge(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brote)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(VerdeMedio))
        Text("SIN RED", style = MaterialTheme.typography.labelSmall, color = VerdeFuerte)
    }
}

@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(2.dp, VerdeFuerte, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(3) {
                Box(Modifier.width(16.dp).height(2.dp).clip(RoundedCornerShape(2.dp)).background(VerdeFuerte))
            }
        }
    }
}

@Composable
private fun ModeToggle(modo: UserMode, onDocente: () -> Unit, onEstudiante: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, VerdeFuerte, RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModePill("Docente", selected = modo == UserMode.DOCENTE, onClick = onDocente, modifier = Modifier.weight(1f))
        ModePill("Estudiante", selected = modo == UserMode.ESTUDIANTE, onClick = onEstudiante, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) VerdeMedio else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) Fondo else VerdeFuerte
        )
    }
}

@Composable
private fun GreetingBlock(modo: UserMode, modifier: Modifier = Modifier) {
    val titulo: String
    val subtitulo: String
    when (modo) {
        UserMode.DOCENTE -> {
            titulo = "Bienvenido, construyamos algoritmos juntos"
            subtitulo = "Todo funciona sin internet. Su grupo está listo."
        }
        UserMode.ESTUDIANTE -> {
            titulo = "¡Hola! Arma tu algoritmo"
            subtitulo = "Acomoda tus fichas y la cámara las lee."
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(titulo, style = MaterialTheme.typography.headlineLarge, color = VerdeFuerte)
        Text(subtitulo, style = MaterialTheme.typography.bodyLarge, color = VerdeFuerte.copy(alpha = 0.8f))
    }
}

/** Ilustración decorativa: fichas de cartón "sobre la mesa", como en el mockup. */
@Composable
private fun FichaIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(VerdeMedio)
    ) {
        FichaCard(
            text = "Inicio",
            background = TarjetaFondo,
            rotation = -7f,
            modifier = Modifier
                .offset(x = 26.dp, y = 22.dp)
                .size(width = 128.dp, height = 72.dp)
        )
        FichaCard(
            text = "Leer X",
            background = Carton,
            rotation = 4f,
            modifier = Modifier
                .offset(x = 78.dp, y = 90.dp)
                .size(width = 150.dp, height = 90.dp)
        )
        FichaCard(
            text = "Fin",
            background = TarjetaFondo,
            rotation = -3f,
            modifier = Modifier
                .offset(x = 140.dp, y = 172.dp)
                .size(width = 118.dp, height = 68.dp)
        )
    }
}

@Composable
private fun FichaCard(text: String, background: Color, rotation: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .graphicsLayer(rotationZ = rotation)
            .shadow(8.dp, RoundedCornerShape(6.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = VerdeFuerte)
    }
}

/** Botón principal, con el efecto "botón físico" del diseño: se aplana al presionar. */
@Composable
private fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val translateY by animateDpAsState(if (pressed) 3.dp else 0.dp, label = "scanButtonTranslate")
    val borderHeight by animateDpAsState(if (pressed) 2.dp else 5.dp, label = "scanButtonBorder")
    val shape = RoundedCornerShape(36.dp)

    Box(
        modifier = modifier
            .offset(y = translateY)
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(VerdeMedioPresionado)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = borderHeight)
                .clip(shape)
                .background(VerdeFuerte)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(26.dp).border(3.dp, Fondo, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Fondo))
                }
                Spacer(Modifier.width(14.dp))
                Text("Escanear fichas", style = MaterialTheme.typography.labelLarge, color = Fondo)
            }
        }
    }
}

@Composable
private fun BottomNavBar(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(TarjetaFondo)) {
        HorizontalDivider(color = VerdeFuerte.copy(alpha = 0.14f), thickness = 2.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavTabItem(label = "Inicio", selected = true) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(VerdeMedio))
            }
            NavTabItem(label = "Retos", selected = false) {
                Box(Modifier.size(20.dp).border(3.dp, VerdeFuerte.copy(alpha = 0.45f), CircleShape))
            }
            NavTabItem(label = "Grupo", selected = false) {
                Box(
                    Modifier
                        .size(15.dp)
                        .graphicsLayer(rotationZ = 45f)
                        .border(3.dp, VerdeFuerte.copy(alpha = 0.45f))
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(label: String, selected: Boolean, icon: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) VerdeFuerte else VerdeFuerte.copy(alpha = 0.7f)
        )
    }
}
