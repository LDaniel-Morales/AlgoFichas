package com.dan.opencv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio

/** Las 3 pestañas de la barra inferior. Solo INICIO y RETOS tienen pantalla implementada. */
enum class NavTab { INICIO, RETOS, GRUPO }

/**
 * Barra de navegación inferior compartida entre pantallas (Inicio, Retos). Cada pestaña
 * conserva su forma propia (cuadrado/círculo/rombo) tanto activa como inactiva -solo cambia
 * entre relleno y contorno-, tal como en el diseño de origen.
 */
@Composable
fun AppBottomNavBar(active: NavTab, onSelect: (NavTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(TarjetaFondo)) {
        HorizontalDivider(color = VerdeFuerte.copy(alpha = 0.14f), thickness = 2.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            NavTabItem(label = "Inicio", selected = active == NavTab.INICIO, onClick = { onSelect(NavTab.INICIO) }) { selected ->
                if (selected) {
                    Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(VerdeMedio))
                } else {
                    Box(Modifier.size(20.dp).border(3.dp, VerdeFuerte.copy(alpha = 0.45f), RoundedCornerShape(5.dp)))
                }
            }
            NavTabItem(label = "Retos", selected = active == NavTab.RETOS, onClick = { onSelect(NavTab.RETOS) }) { selected ->
                if (selected) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(VerdeMedio))
                } else {
                    Box(Modifier.size(20.dp).border(3.dp, VerdeFuerte.copy(alpha = 0.45f), CircleShape))
                }
            }
            NavTabItem(label = "Grupo", selected = active == NavTab.GRUPO, onClick = { onSelect(NavTab.GRUPO) }) { selected ->
                if (selected) {
                    Box(Modifier.size(15.dp).graphicsLayer(rotationZ = 45f).background(VerdeMedio))
                } else {
                    Box(Modifier.size(15.dp).graphicsLayer(rotationZ = 45f).border(3.dp, VerdeFuerte.copy(alpha = 0.45f)))
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(label: String, selected: Boolean, onClick: () -> Unit, icon: @Composable (Boolean) -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        icon(selected)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) VerdeFuerte else VerdeFuerte.copy(alpha = 0.7f)
        )
    }
}
