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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextRange
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
import com.dan.opencv.vision.ShapeType
import com.dan.opencv.ocr.FichaInstructionParser
import com.dan.opencv.interpreter.ExecutionResult
import com.dan.opencv.interpreter.FlowInterpreter
import com.dan.opencv.interpreter.FlowProgram
import com.dan.opencv.interpreter.FlowProgramBuilder
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
 * "Ejecutar" construye un programa con las fichas detectadas y muestra su traza de ejecución.
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
    val scope = rememberCoroutineScope()

    var scanState by remember { mutableStateOf<ScanState>(ScanState.Vacio) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var detalleTecnico by remember { mutableStateOf(false) }
    var ejecutando by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var activeNodeId by remember { mutableStateOf<Int?>(null) }
    var editingShapeId by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf(TextFieldValue()) }
    var editingType by remember { mutableStateOf<ShapeType?>(null) }
    var pendingProgram by remember { mutableStateOf<FlowProgram?>(null) }
    var inputValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun beginEdit(shapeId: Int) {
        val shape = (scanState as? ScanState.Listo)?.resultado?.positioned
            ?.firstOrNull { it.shape.id == shapeId }?.shape ?: return
        editingShapeId = shapeId
        val currentText = shape.recognizedText ?: shape.parsedInstruction.orEmpty()
        editingText = TextFieldValue(currentText, TextRange(currentText.length))
        editingType = shape.type
    }

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
                    executionResult = null
                    activeNodeId = null
                    capturarYAnalizar(capture, captureExecutor, scope) { result ->
                        scanState = result
                    }
                }
            )
        }

        ResultSheet(
            scanState = scanState,
            ejecutando = ejecutando,
            executionResult = executionResult,
            detalleTecnico = detalleTecnico,
            activeNodeId = activeNodeId,
            onActiveNodeChange = { activeNodeId = it },
            onEditShape = ::beginEdit,
            onToggleDetalle = { detalleTecnico = !detalleTecnico },
            onOtraVez = {
                scanState = ScanState.Vacio
                ejecutando = false
                executionResult = null
                pendingProgram = null
                inputValues = emptyMap()
                activeNodeId = null
            },
            onEjecutar = {
                (scanState as? ScanState.Listo)?.resultado?.let { result ->
                    val program = FlowProgramBuilder.build(result.positioned)
                    val requiredInputs = FlowInterpreter.requiredInputs(program)
                    activeNodeId = null
                    if (requiredInputs.isEmpty()) {
                        executionResult = FlowInterpreter.execute(program)
                        ejecutando = true
                    } else {
                        pendingProgram = program
                        inputValues = requiredInputs.associateWith { "" }
                    }
                }
            }
        )

        pendingProgram?.let { program ->
            val variables = FlowInterpreter.requiredInputs(program)
            InputValuesDialog(
                variables = variables,
                values = inputValues,
                onValueChange = { name, value -> inputValues = inputValues + (name to value) },
                onDismiss = {
                    pendingProgram = null
                    inputValues = emptyMap()
                },
                onRun = {
                    activeNodeId = null
                    executionResult = FlowInterpreter.execute(
                        program,
                        inputValues.filterValues { it.isNotBlank() }
                    )
                    pendingProgram = null
                    inputValues = emptyMap()
                    ejecutando = true
                }
            )
        }

        val shapeId = editingShapeId
        val selectedType = editingType
        if (shapeId != null && selectedType != null) {
            ShapeEditorDialog(
                initialType = selectedType,
                text = editingText,
                onTextChange = { editingText = it },
                onInsertOperator = { operator ->
                    val source = editingText.text
                    val start = minOf(editingText.selection.start, editingText.selection.end)
                    val end = maxOf(editingText.selection.start, editingText.selection.end)
                    val insertion = if (source.isBlank()) operator else " $operator "
                    val updated = source.substring(0, start) + insertion + source.substring(end)
                    editingText = TextFieldValue(updated, TextRange(start + insertion.length))
                },
                onTypeChange = { editingType = it },
                onDismiss = {
                    editingShapeId = null
                    editingType = null
                },
                onSave = {
                    val current = (scanState as? ScanState.Listo)?.resultado
                    if (current != null) {
                        val cleanedText = editingText.text.trim()
                        val updated = current.copy(
                            positioned = current.positioned.map { positioned ->
                                if (positioned.shape.id != shapeId) positioned else {
                                    val parsed = FichaInstructionParser.parse(cleanedText, selectedType.role)
                                    positioned.copy(
                                        shape = positioned.shape.copy(
                                            type = selectedType,
                                            recognizedText = cleanedText.ifEmpty { null },
                                            parsedInstruction = cleanedText.ifEmpty {
                                                FichaInstructionParser.formatDisplay(parsed).ifEmpty { null }
                                            }
                                        )
                                    )
                                }
                            }
                        )
                        scanState = ScanState.Listo(updated)
                        executionResult = null
                        ejecutando = false
                    }
                    editingShapeId = null
                    editingType = null
                }
            )
        }
    }
}

/** Toma una foto y la procesa una sola vez; entrega el resultado por [onDone] (hilo de fondo). */
private fun capturarYAnalizar(
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    coroutineScope: CoroutineScope,
    onDone: (ScanState) -> Unit
) {
    imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            coroutineScope.launch(Dispatchers.Default) {
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
                    if (finalBitmap != null) {
                        val result = PhotoAnalyzer.analyze(finalBitmap)
                        onDone(ScanState.Listo(result))
                    } else {
                        onDone(ScanState.ErrorCaptura("No se pudo leer la foto tomada."))
                    }
                } catch (t: Throwable) {
                    onDone(ScanState.ErrorCaptura(t.message ?: "No se pudo procesar la foto."))
                } finally {
                    image.close()
                }
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

private enum class ScanResultTab(val label: String, val icon: String) {
    DIAGRAMA("Diagrama", "⬡"),
    FICHAS("Fichas", "☰"),
    CODIGO("Código", "</>"),
    EJECUCION("Ejecutar", "▶")
}

@Composable
private fun ResultSheet(
    scanState: ScanState,
    ejecutando: Boolean,
    executionResult: ExecutionResult?,
    detalleTecnico: Boolean,
    activeNodeId: Int?,
    onActiveNodeChange: (Int?) -> Unit,
    onEditShape: (Int) -> Unit,
    onToggleDetalle: () -> Unit,
    onOtraVez: () -> Unit,
    onEjecutar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val positioned = (scanState as? ScanState.Listo)?.resultado?.positioned.orEmpty()
        .sortedWith(compareBy({ it.row }, { it.column }))
    val program = remember(positioned) { FlowProgramBuilder.build(positioned) }
    var selectedTab by remember(scanState) { mutableStateOf(ScanResultTab.DIAGRAMA) }

    LaunchedEffect(ejecutando) {
        if (ejecutando) {
            selectedTab = ScanResultTab.EJECUCION
        }
    }

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
                    is ScanState.Listo if ejecutando -> "Ejecutando diagrama"
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

        if (scanState is ScanState.Listo && positioned.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(VerdeFuerte.copy(alpha = 0.08f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ScanResultTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) VerdeFuerte else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tab.icon} ${tab.label}",
                            color = if (isSelected) Fondo else VerdeFuerte,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            when (scanState) {
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

                    when (selectedTab) {
                        ScanResultTab.DIAGRAMA -> {
                            FlowchartVisualizer(
                                program = program,
                                activeNodeId = activeNodeId,
                                onEditNode = onEditShape
                            )
                        }
                        ScanResultTab.FICHAS -> {
                            positioned.forEachIndexed { i, p ->
                                DetectedRow(
                                    index = i + 1,
                                    positioned = p,
                                    detalleTecnico = detalleTecnico,
                                    onEdit = { onEditShape(p.shape.id) }
                                )
                            }
                            Text(
                                if (detalleTecnico) "Ocultar detalle técnico" else "Ver detalle técnico",
                                color = VerdeMedio,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable(onClick = onToggleDetalle).padding(vertical = 4.dp)
                            )
                        }
                        ScanResultTab.CODIGO -> {
                            FlowCodeView(program = program)
                        }
                        ScanResultTab.EJECUCION -> {
                            if (executionResult != null) {
                                EjecucionInteractiva(
                                    result = executionResult,
                                    onActiveNodeChange = onActiveNodeChange
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        "Diagrama listo para ejecutar.",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = VerdeFuerte
                                    )
                                    Text(
                                        "Toca el botón 'Ejecutar' abajo para simular paso a paso las variables y la salida.",
                                        fontSize = 13.sp,
                                        color = VerdeFuerte.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                text = if (ejecutando) "Reejecutar" else "Ejecutar",
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
private fun DetectedRow(
    index: Int,
    positioned: PositionedShape,
    detalleTecnico: Boolean,
    onEdit: () -> Unit
) {
    val shape = positioned.shape
    val tipo = shape.type.toFichaTipoOrNull()
    val nombre = shape.parsedInstruction ?: (tipo?.nombre ?: shape.role.title)
    val linea = if (detalleTecnico) {
        val ocrPart = shape.recognizedText?.let { "texto: \"$it\" · " } ?: ""
        "${shape.type.label} · ${shape.colorName} · ${ocrPart}fila ${positioned.row} · col ${positioned.column}"
    } else {
        if (shape.parsedInstruction != null && shape.recognizedText != null) {
            "${shape.type.label} · \"${shape.recognizedText}\""
        } else {
            "${shape.type.label} (${shape.colorName})"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TarjetaFondo)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(VerdeMedio),
            contentAlignment = Alignment.Center
        ) {
            Text(index.toString(), color = Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
        }
        if (tipo != null) FichaShapeIcon(tipo) else Box(Modifier.size(18.dp).clip(CircleShape).background(VerdeFuerte.copy(alpha = 0.3f)))
        Column(modifier = Modifier.weight(1f)) {
            Text(nombre, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = VerdeFuerte)
            Text(linea, fontFamily = IbmPlexMono, fontSize = 11.sp, color = VerdeFuerte.copy(alpha = 0.72f))
        }
        TextButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
            Text("Editar", fontSize = 11.sp, color = VerdeFuerte)
        }
        shape.roiBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Recorte de ficha",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 46.dp, height = 32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, VerdeFuerte.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun ShapeEditorDialog(
    initialType: ShapeType,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onInsertOperator: (String) -> Unit,
    onTypeChange: (ShapeType) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.94f),
        shape = RoundedCornerShape(24.dp),
        containerColor = Fondo,
        titleContentColor = VerdeFuerte,
        textContentColor = VerdeFuerte,
        title = { Text("Corregir ficha", fontWeight = FontWeight.ExtraBold, color = VerdeFuerte) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Texto reconocido", fontSize = 13.sp, color = VerdeFuerte.copy(alpha = 0.75f))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    placeholder = { Text("Escribe la instrucción de la ficha") },
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VerdeFuerte,
                        unfocusedBorderColor = VerdeFuerte.copy(alpha = 0.35f),
                        cursorColor = VerdeFuerte
                    )
                )
                if (initialType.role == com.dan.opencv.vision.FlowRole.PROCESS) {
                    Text("Atajos de operación", fontSize = 13.sp, color = VerdeFuerte.copy(alpha = 0.75f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("=", "+", "-", "*", "/").forEach { operator ->
                            TextButton(
                                onClick = { onInsertOperator(operator) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.dp, VerdeFuerte.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text(operator, fontWeight = FontWeight.Bold, color = VerdeFuerte)
                            }
                        }
                    }
                }
                Text("Figura", fontSize = 13.sp, color = VerdeFuerte.copy(alpha = 0.75f))
                ShapeType.entries.filter { it != ShapeType.UNKNOWN }.chunked(2).forEach { rowTypes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowTypes.forEach { type ->
                            Button(
                                onClick = { onTypeChange(type) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = if (type == initialType) VerdeMedio else TarjetaFondo,
                                    contentColor = VerdeFuerte
                                )
                            ) {
                                ShapeTypeButtonContent(type)
                            }
                        }
                        if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Guardar", color = VerdeFuerte, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = VerdeFuerte) }
        }
    )
}

@Composable
private fun ShapeTypeButtonContent(type: ShapeType) {
    val fichaType = type.toFichaTipoOrNull()
    if (fichaType != null) {
        FichaShapeIcon(fichaType, Modifier.padding(end = 5.dp))
    } else {
        Text(shapeSymbol(type), fontSize = 17.sp, modifier = Modifier.padding(end = 5.dp))
    }
    Text(type.label, fontSize = 10.sp, maxLines = 1)
}

private fun shapeSymbol(type: ShapeType): String = when (type) {
    ShapeType.CIRCLE -> "○"
    ShapeType.OVAL -> "⬭"
    ShapeType.CONNECTOR_PAGE -> "⬟"
    ShapeType.TRIANGLE -> "△"
    ShapeType.LOOP -> "⬢"
    ShapeType.KEYBOARD_INPUT -> "▤"
    ShapeType.DOCUMENT -> "▱"
    ShapeType.DISPLAY -> "⬟"
    ShapeType.PREDEFINED_PROCESS -> "▣"
    ShapeType.STADIUM -> "▭"
    ShapeType.PARALLELOGRAM -> "▱"
    ShapeType.RECTANGLE, ShapeType.SQUARE -> "□"
    ShapeType.DIAMOND -> "◇"
    ShapeType.UNKNOWN -> "?"
}

@Composable
private fun InputValuesDialog(
    variables: List<String>,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onRun: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.92f),
        shape = RoundedCornerShape(24.dp),
        containerColor = Fondo,
        titleContentColor = VerdeFuerte,
        textContentColor = VerdeFuerte,
        title = { Text("Valores de entrada", fontWeight = FontWeight.ExtraBold, color = VerdeFuerte) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Escribe los valores que necesita el diagrama antes de ejecutarlo.",
                    fontSize = 13.sp,
                    color = VerdeFuerte.copy(alpha = 0.75f)
                )
                variables.forEach { variable ->
                    OutlinedTextField(
                        value = values[variable].orEmpty(),
                        onValueChange = { onValueChange(variable, it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(variable) },
                        placeholder = { Text("Valor de $variable") },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VerdeFuerte,
                            unfocusedBorderColor = VerdeFuerte.copy(alpha = 0.35f),
                            cursorColor = VerdeFuerte
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRun) {
                Text("Ejecutar", color = VerdeFuerte, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = VerdeFuerte) }
        }
    )
}

@Composable
private fun EjecucionInteractiva(
    result: ExecutionResult,
    onActiveNodeChange: ((Int?) -> Unit)? = null
) {
    var visibles by remember(result) { mutableIntStateOf(0) }
    LaunchedEffect(result) {
        visibles = 0
        while (visibles < result.steps.size) {
            delay(PASO_MS)
            visibles++
            val currentNodeId = result.steps.getOrNull(visibles - 1)?.nodeId
            onActiveNodeChange?.invoke(currentNodeId)
        }
    }

    Text(
        "Traza del diagrama detectado",
        fontFamily = IbmPlexMono,
        fontSize = 12.sp,
        color = VerdeFuerte.copy(alpha = 0.72f)
    )
    result.steps.take(visibles).forEachIndexed { i, step ->
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
            Column(modifier = Modifier.weight(1f)) {
                Text(step.label, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = VerdeFuerte)
                Text(step.detail, fontFamily = IbmPlexMono, fontSize = 12.sp, color = VerdeFuerte.copy(alpha = 0.72f))
            }
        }
    }
    if (visibles == result.steps.size) {
        result.warnings.forEach { warning ->
            Text(
                warning,
                color = VerdeFuerte.copy(alpha = 0.75f),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brote)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Inspector de Variables
        if (result.variables.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TarjetaFondo)
                    .border(1.dp, VerdeFuerte.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Variables en memoria",
                    fontFamily = IbmPlexMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VerdeFuerte.copy(alpha = 0.7f)
                )
                result.variables.forEach { (name, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            fontFamily = IbmPlexMono,
                            fontSize = 13.sp,
                            color = VerdeFuerte,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = value,
                            fontFamily = IbmPlexMono,
                            fontSize = 13.sp,
                            color = VerdeMedio,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Salida en pantalla
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brote)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("Salida", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VerdeFuerte.copy(alpha = 0.72f))
            Text(
                if (result.output.isNotEmpty()) result.output.joinToString("\n") else "Sin salida producida",
                fontFamily = Literata,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = VerdeFuerte
            )
            Text(
                if (result.completed) "Diagrama terminado" else "Ejecución incompleta",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = VerdeMedio
            )
        }
    }
}

private const val PASO_MS = 600L
