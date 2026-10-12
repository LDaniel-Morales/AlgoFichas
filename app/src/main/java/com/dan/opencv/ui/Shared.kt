package com.dan.opencv.ui

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.opencv.ui.theme.Carton
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedioPresionado

/**
 * El botón "físico" del diseño: una píldora de dos tonos que se aplana al presionar
 * (se usa para "Escanear fichas" en Inicio y "Escanear mi solución" en el detalle de reto).
 * El recorte redondeado se aplica una sola vez en el contenedor exterior -dos formas
 * redondeadas independientes generan un desfase visible en las esquinas-.
 */
@Composable
fun PhysicalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 67.dp,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val translateY by animateDpAsState(if (pressed) 3.dp else 0.dp, label = "physicalButtonTranslate")
    val borderHeight by animateDpAsState(if (pressed) 2.dp else 5.dp, label = "physicalButtonBorder")
    val shape = RoundedCornerShape(36.dp)

    Column(
        modifier = modifier
            .offset(y = translateY)
            .clip(shape)
            .background(VerdeMedioPresionado)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(VerdeFuerte)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(14.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, color = Fondo)
        }
        Spacer(Modifier.fillMaxWidth().height(borderHeight))
    }
}

/** El ícono de cámara simplificado que usan los botones de escanear. */
@Composable
fun CameraIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(26.dp).border(3.dp, Fondo, RoundedCornerShape(7.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Fondo))
    }
}

/** Tarjeta de aviso/pista sobre fondo "cartón" (usada en Retos y en el detalle de un reto). */
@Composable
fun NoteCard(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Carton)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, fontSize = 15.sp, color = VerdeFuerte)
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = VerdeFuerte)
    }
}
