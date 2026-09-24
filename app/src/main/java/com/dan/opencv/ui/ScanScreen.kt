package com.dan.opencv.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.IbmPlexMono
import com.dan.opencv.ui.theme.Literata
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio
import com.dan.opencv.vision.FrameAnalyzer
import com.dan.opencv.vision.FrameResult
import com.dan.opencv.vision.PositionedShape
import java.util.concurrent.Executors

private val EstadoBarraOscuro = Color(0xFF1A2A20)

/**
 * Pantalla de escaneo, basada en "01 Escanear" del diseño "AlgoFichas Vistas". Adaptación
 * deliberada: el mockup dibuja una mesa de madera y 4 fichas de cartón ilustradas (con
 * insignias 1-4 fijas en píxeles) porque la herramienta de diseño no puede mostrar una
 * cámara real -aquí esa ilustración se reemplaza por la vista previa real de la cámara y el
 * pipeline de detección ya existente (FrameAnalyzer/ShapeDetector); las insignias numeradas
 * quedan en la lista de la hoja de resultados en vez de superpuestas sobre la imagen, porque
 * ubicarlas sobre el punto exacto de cada ficha requeriría mapear coordenadas de píxel de
 * análisis a coordenadas de pantalla (pendiente, no es parte de este diseño).
 * "Otra vez" y "Ejecutar" quedan sin lógica de negocio real -no existe todavía un motor que
 * valide o ejecute el algoritmo armado-, igual que "Retos"/"Grupo" en pantallas anteriores.
 */
@Composable
fun ScanScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Column(modifier = modifier.fillMaxSize().background(VerdeFuerte)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(EstadoBarraOscuro)
        )
        if (hasCameraPermission) {
            ScannerContent(onBack = onBack, modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            Box(Modifier.fillMaxWidth().weight(1f).background(Fondo), contentAlignment = Alignment.Center) {
                Text(
                    "Se necesita permiso de cámara para detectar las fichas del diagrama.",
                    modifier = Modifier.padding(24.dp),
                    color = VerdeFuerte,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ScannerContent(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var frameResult by remember { mutableStateOf<FrameResult?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var detalleTecnico by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor, FrameAnalyzer { result ->
                                    frameResult = result
                                })
                            }
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            ScannerOverlay(
                onBack = onBack,
                torchOn = torchOn,
                onToggleTorch = {
                    val cam = camera ?: return@ScannerOverlay
                    if (cam.cameraInfo.hasFlashUnit()) {
                        torchOn = !torchOn
                        cam.cameraControl.enableTorch(torchOn)
                    }
                }
            )
        }

        ResultSheet(
            frameResult = frameResult,
            detalleTecnico = detalleTecnico,
            onToggleDetalle = { detalleTecnico = !detalleTecnico },
            onOtraVez = { frameResult = null }
        )
    }
}

@Composable
private fun ScannerOverlay(
    onBack: () -> Unit,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Guía de encuadre: 4 esquinas + línea central punteada.
        val guideColor = Brote
        CornerBracket(top = true, start = true, modifier = Modifier.align(Alignment.TopStart).padding(34.dp))
        CornerBracket(top = true, start = false, modifier = Modifier.align(Alignment.TopEnd).padding(34.dp))
        CornerBracket(top = false, start = true, modifier = Modifier.align(Alignment.BottomStart).padding(34.dp))
        CornerBracket(top = false, start = false, modifier = Modifier.align(Alignment.BottomEnd).padding(34.dp))
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .padding(vertical = 80.dp)
                .width(3.dp)
                .drawBehind {
                    drawLine(
                        color = guideColor.copy(alpha = 0.8f),
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                    )
                }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 12.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(onClick = onBack) { Text("‹", color = Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) }
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Brote).padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Text("Mantén el celular quieto", color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            RoundIconButton(onClick = onToggleTorch, highlighted = torchOn) {
                Text(if (torchOn) "⚡" else "⚡︎", color = Fondo, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, highlighted: Boolean = false, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (highlighted) Brote else EstadoBarraOscuro.copy(alpha = 0.75f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun CornerBracket(top: Boolean, start: Boolean, modifier: Modifier = Modifier) {
    val color = Brote
    Box(
        modifier
            .size(34.dp)
            .drawBehind {
                val strokeWidth = 7f
                val y = if (top) 0f else size.height
                val x = if (start) 0f else size.width
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth)
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth)
            }
    )
}

@Composable
private fun ResultSheet(
    frameResult: FrameResult?,
    detalleTecnico: Boolean,
    onToggleDetalle: () -> Unit,
    onOtraVez: () -> Unit,
    modifier: Modifier = Modifier
) {
    val positioned = frameResult?.positioned.orEmpty().sortedWith(compareBy({ it.row }, { it.column }))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(Fondo)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.width(40.dp).height(5.dp).clip(RoundedCornerShape(50)).background(VerdeFuerte.copy(alpha = 0.25f)).align(Alignment.CenterHorizontally))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${positioned.size} ${if (positioned.size == 1) "ficha detectada" else "fichas detectadas"}",
                fontFamily = Literata,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = VerdeFuerte
            )
            Box(Modifier.clip(RoundedCornerShape(50)).background(Brote).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("En vivo", color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (positioned.isEmpty()) {
                Text(
                    "Apunta la cámara a tus fichas para empezar a detectarlas.",
                    color = VerdeFuerte.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
            } else {
                positioned.forEachIndexed { i, p -> DetectedRow(index = i + 1, positioned = p, detalleTecnico = detalleTecnico) }
            }
        }

        Text(
            if (detalleTecnico) "Ocultar detalle técnico" else "Ver detalle técnico",
            color = VerdeMedio,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onToggleDetalle).padding(vertical = 2.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(50))
                    .border(2.dp, VerdeFuerte, RoundedCornerShape(50))
                    .clickable(onClick = onOtraVez),
                contentAlignment = Alignment.Center
            ) {
                Text("Otra vez", color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            PhysicalButton(
                text = "Ejecutar",
                onClick = { /* pendiente: aún no existe un motor que ejecute el algoritmo armado */ },
                modifier = Modifier.weight(1.6f),
                minHeight = 52.dp
            ) {
                Text("▶", color = Fondo, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DetectedRow(index: Int, positioned: PositionedShape, detalleTecnico: Boolean) {
    val shape = positioned.shape
    val tipo = shape.type.toFichaTipoOrNull()
    val nombre = tipo?.nombre ?: shape.type.label.replaceFirstChar { it.uppercase() }
    val linea = if (detalleTecnico) {
        "${shape.colorName} · fila ${positioned.row} · col ${positioned.column}"
    } else {
        shape.colorName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TarjetaFondo)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(VerdeMedio),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        if (tipo != null) FichaShapeIcon(tipo) else Box(Modifier.size(18.dp).clip(CircleShape).background(VerdeFuerte.copy(alpha = 0.3f)))
        Column(modifier = Modifier.weight(1f)) {
            Text(nombre, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = VerdeFuerte)
            Text(linea, fontFamily = IbmPlexMono, fontSize = 12.sp, color = VerdeFuerte.copy(alpha = 0.72f))
        }
    }
}
