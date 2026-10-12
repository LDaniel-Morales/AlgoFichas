package com.dan.opencv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dan.opencv.R

/** Título (logo/wordmark, saludos grandes). */
val Literata = FontFamily(
    Font(R.font.literata_bold, FontWeight.Bold),
    Font(R.font.literata_extrabold, FontWeight.ExtraBold)
)

/** Texto general de la interfaz (cuerpo, botones, navegación). */
val Nunito = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold)
)

/** Estilo "manuscrito" usado en la ilustración de fichas de cartón. */
val Caveat = FontFamily(
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
    Font(R.font.caveat_bold, FontWeight.Bold)
)

/** Etiquetas técnicas en mayúsculas (metadatos, estado "sin red"). */
val IbmPlexMono = FontFamily(
    Font(R.font.ibmplexmono_regular, FontWeight.Normal),
    Font(R.font.ibmplexmono_medium, FontWeight.Medium)
)

val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.01).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Literata,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 26.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Nunito,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = IbmPlexMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.14f.em
    )
)
