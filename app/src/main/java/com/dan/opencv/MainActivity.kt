package com.dan.opencv

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dan.opencv.ui.theme.OpencvTheme
import com.dan.opencv.vision.FrameAnalyzer
import com.dan.opencv.vision.FrameResult
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpencvTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CameraLogicScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * Pantalla sin diseño propio (componentes por defecto de Material3): gestiona el permiso
 * de cámara y, una vez concedido, muestra la vista previa junto con el listado de fichas
 * detectadas en el frame más reciente.
 */
@Composable
fun CameraLogicScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraWithDetection(modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            Text(
                text = "Se necesita permiso de cámara para detectar las fichas del diagrama.",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun CameraWithDetection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    var frameResult by remember { mutableStateOf<FrameResult?>(null) }

    Column(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(2f),
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

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageAnalysis
                    )
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        DetectionList(frameResult = frameResult, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
fun DetectionList(frameResult: FrameResult?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(8.dp)) {
        val count = frameResult?.positioned?.size ?: 0
        Text(text = "Fichas detectadas: $count")

        LazyColumn {
            items(frameResult?.positioned.orEmpty()) { positioned ->
                val s = positioned.shape
                val next = positioned.inferredNextId?.toString() ?: "-"
                Text(
                    text = "#${s.id} ${s.type.label} (${s.colorName}) · " +
                        "fila ${positioned.row}, col ${positioned.column} · " +
                        "pos (${s.centerX.toInt()}, ${s.centerY.toInt()}) · siguiente: $next"
                )
            }
        }
    }
}
