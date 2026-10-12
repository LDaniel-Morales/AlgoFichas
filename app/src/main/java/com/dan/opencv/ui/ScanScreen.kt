package com.dan.opencv.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.dan.opencv.retos.RETOS
import com.dan.opencv.retos.Reto
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.IbmPlexMono
import com.dan.opencv.ui.theme.Literata
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio
import com.dan.opencv.vision.FrameResult
import com.dan.opencv.vision.PhotoAnalyzer
import com.dan.opencv.vision.PositionedShape
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

private val EstadoBarraOscuro = Color(0xFF1A2A20)

/** Estado del escaneo: ya no hay detección continua, es una foto que se toma y se procesa una vez. */
private sealed class ScanState {
    object Vacio : ScanState()
    object Procesando : ScanState()
    data class Listo(val resultado: FrameResult) : ScanState()
    data class ErrorCaptura(val mensaje: String) : ScanState()
}

/**
 * Pantalla de escaneo, basada en "01 Escanear" del diseño "AlgoFichas Vistas". Adaptación
 * deliberada: el mockup dibuja una mesa de madera y 4 fichas de cartón ilustradas porque la
 * herramienta de diseño no puede mostrar una cámara real -aquí esa ilustración se reemplaza
 * por la vista previa real de la cámara-.
 *
 * Funcionamiento (captura única, no detección continua): el usuario encuadra con la vista
 * previa en vivo, presiona el obturador, se toma UNA foto (`ImageCapture`), esa foto se
 * decodifica y se procesa una sola vez (`PhotoAnalyzer`/`ShapeDetector`), y el resultado
 * queda fijo en la hoja de abajo. "Otra vez" limpia el resultado para volver a disparar.
 * "Ejecutar" simula la ejecución: si se llegó desde un reto ([retoIndex]), reproduce paso a
 * paso la solución hardcodeada de ese reto ([Reto.solucion]) -todavía no existe un motor que
 * ejecute el algoritmo realmente detectado en la foto-.
 */
@Composable
fun ScanScreen(retoIndex: Int?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val reto = retoIndex?.let { RETOS.getOrNull(it) }
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
            ScannerContent(reto = reto, onBack = onBack, modifier = Modifier.fillMaxWidth().weight(1f))
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
private fun ScannerContent(reto: Reto?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }

    var scanState by remember { mutableStateOf<ScanState>(ScanState.Vacio) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var detalleTecnico by remember { mutableStateOf(false) }
    var ejecutando by remember { mutableStateOf(false) }

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
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture
                        )
                        imageCapture = capture
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
                },
                mostrarObturador = scanState !is ScanState.Procesando,
                onShutter = {
                    val capture = imageCapture ?: return@ScannerOverlay
                    scanState = ScanState.Procesando
                    ejecutando = false
                    capturarYAnalizar(capture, captureExecutor) { result ->
                        scanState = result
                    }
                }
            )
        }

        ResultSheet(
            scanState = scanState,
            reto = reto,
            ejecutando = ejecutando,
            detalleTecnico = detalleTecnico,
            onToggleDetalle = { detalleTecnico = !detalleTecnico },
            onOtraVez = { scanState = ScanState.Vacio; ejecutando = false },
            onEjecutar = { if (scanState is ScanState.Listo) ejecutando = true }
        )
    }
}

/** Toma una foto y la procesa una sola vez; entrega el resultado por [onDone] (hilo de fondo). */
private fun capturarYAnalizar(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onDone: (ScanState) -> Unit
) {
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                var bitmap: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val rotation = image.imageInfo.rotationDegrees
                val decoded = bitmap
                if (decoded != null && rotation != 0) {
                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                    bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                }
                val finalBitmap = bitmap
                onDone(
                    if (finalBitmap != null) {
                        ScanState.Listo(PhotoAnalyzer.analyze(finalBitmap))
                    } else {
                        ScanState.ErrorCaptura("No se pudo leer la foto tomada.")
                    }
                )
            } catch (t: Throwable) {
                onDone(ScanState.ErrorCaptura(t.message ?: "No se pudo procesar la foto."))
            } finally {
                image.close()
            }
        }

        override fun onError(exception: ImageCaptureException) {
            onDone(ScanState.ErrorCaptura(exception.message ?: "No se pudo tomar la foto."))
        }
    })
}

@Composable
private fun ScannerOverlay(
    onBack: () -> Unit,
    torchOn: Boolean,
    onToggleTorch: () -> Unit,
    mostrarObturador: Boolean,
    onShutter: () -> Unit,
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
                Text("Encuadra tus fichas", color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            RoundIconButton(onClick = onToggleTorch, highlighted = torchOn) {
                Text(if (torchOn) "⚡" else "⚡︎", color = Fondo, fontSize = 16.sp)
            }
        }

        if (mostrarObturador) {
            ShutterButton(onClick = onShutter, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp))
        } else {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).size(72.dp).clip(CircleShape).background(EstadoBarraOscuro.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Fondo, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** El obturador: un círculo blanco con un anillo, como en cualquier app de cámara. */
@Composable
private fun ShutterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Fondo.copy(alpha = 0.25f))
            .border(3.dp, Fondo, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Fondo))
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
    scanState: ScanState,
    reto: Reto?,
    ejecutando: Boolean,
    detalleTecnico: Boolean,
    onToggleDetalle: () -> Unit,
    onOtraVez: () -> Unit,
    onEjecutar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val positioned = (scanState as? ScanState.Listo)?.resultado?.positioned.orEmpty()
        .sortedWith(compareBy({ it.row }, { it.column }))

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
                when (scanState) {
                    is ScanState.Listo if ejecutando -> if (reto != null) "Ejecutando" else "Sin reto elegido"
                    is ScanState.Listo -> "${positioned.size} ${if (positioned.size == 1) "ficha detectada" else "fichas detectadas"}"
                    ScanState.Procesando -> "Analizando foto…"
                    is ScanState.ErrorCaptura -> "No se pudo escanear"
                    ScanState.Vacio -> "Lista para escanear"
                },
                fontFamily = Literata,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
                color = VerdeFuerte
            )
            if (scanState is ScanState.Listo) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Brote).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("Analizado", color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (scanState) {
                is ScanState.Listo if ejecutando -> if (reto != null) {
                    EjecucionSimulada(reto)
                } else {
                    Text(
                        "Entra a un reto desde la pestaña Retos y escanea tu solución para poder ejecutarla.",
                        color = VerdeFuerte.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }
                ScanState.Vacio -> Text(
                    "Encuadra tus fichas y toca el obturador para tomar la foto.",
                    color = VerdeFuerte.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                ScanState.Procesando -> Text(
                    "Un momento, estamos calculando las formas de la foto…",
                    color = VerdeFuerte.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )
                is ScanState.ErrorCaptura -> Text(
                    scanState.mensaje,
                    color = VerdeFuerte.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                is ScanState.Listo -> if (positioned.isEmpty()) {
                    Text(
                        "No se detectó ninguna ficha en la foto. Prueba con mejor luz, sobre una superficie lisa de otro color que las fichas.",
                        color = VerdeFuerte.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                } else {
                    scanState.resultado.aviso?.let { aviso ->
                        Text(
                            aviso,
                            color = VerdeFuerte,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brote)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    positioned.forEachIndexed { i, p -> DetectedRow(index = i + 1, positioned = p, detalleTecnico = detalleTecnico) }
                }
            }
        }

        if (scanState is ScanState.Listo && positioned.isNotEmpty() && !ejecutando) {
            Text(
                if (detalleTecnico) "Ocultar detalle técnico" else "Ver detalle técnico",
                color = VerdeMedio,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.clickable(onClick = onToggleDetalle).padding(vertical = 2.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(50))
                    .border(2.dp, VerdeFuerte, RoundedCornerShape(50))
                    .clickable(enabled = scanState !is ScanState.Vacio, onClick = onOtraVez),
                contentAlignment = Alignment.Center
            ) {
                Text("Otra vez", color = VerdeFuerte, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            PhysicalButton(
                text = "Ejecutar",
                onClick = onEjecutar,
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

/**
 * Reproduce la solución hardcodeada del reto como si se ejecutara el diagrama escaneado: los
 * pasos aparecen uno a uno y al final se muestra la salida esperada.
 */
@Composable
private fun EjecucionSimulada(reto: Reto) {
    var visibles by remember(reto) { mutableIntStateOf(0) }
    LaunchedEffect(reto) {
        while (visibles < reto.solucion.size) {
            delay(PASO_MS)
            visibles++
        }
    }

    Text(
        "${reto.titulo} · entra: ${reto.entra}",
        fontFamily = IbmPlexMono,
        fontSize = 12.sp,
        color = VerdeFuerte.copy(alpha = 0.72f)
    )
    reto.solucion.take(visibles).forEachIndexed { i, paso ->
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
                Text((i + 1).toString(), color = Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            FichaShapeIcon(paso.tipo)
            Column(modifier = Modifier.weight(1f)) {
                Text(paso.texto, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = VerdeFuerte)
                Text(paso.traza, fontFamily = IbmPlexMono, fontSize = 12.sp, color = VerdeFuerte.copy(alpha = 0.72f))
            }
        }
    }
    if (visibles == reto.solucion.size) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brote)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("Salida", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeFuerte.copy(alpha = 0.72f))
            Text(reto.sale, fontFamily = Literata, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = VerdeFuerte)
            Text("¡Reto resuelto!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VerdeMedio)
        }
    }
}

private const val PASO_MS = 600L
