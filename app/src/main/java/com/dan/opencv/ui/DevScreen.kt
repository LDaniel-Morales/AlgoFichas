package com.dan.opencv.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dan.opencv.dev.BatteryMetrics
import com.dan.opencv.dev.DevPerformanceMonitor
import com.dan.opencv.dev.MemoryMetrics
import com.dan.opencv.ui.theme.Brote
import com.dan.opencv.ui.theme.Carton
import com.dan.opencv.ui.theme.Fondo
import com.dan.opencv.ui.theme.IbmPlexMono
import com.dan.opencv.ui.theme.Literata
import com.dan.opencv.ui.theme.TarjetaFondo
import com.dan.opencv.ui.theme.VerdeFuerte
import com.dan.opencv.ui.theme.VerdeMedio
import com.dan.opencv.vision.FrameResult
import com.dan.opencv.vision.PhotoAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var memMetrics by remember { mutableStateOf(DevPerformanceMonitor.getMemoryMetrics()) }
    var batteryMetrics by remember { mutableStateOf(DevPerformanceMonitor.getBatteryMetrics(context)) }
    var benchmarks by remember { mutableStateOf(DevPerformanceMonitor.getRecentBenchmarks()) }

    var testRunning by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<FrameResult?>(null) }
    var testElapsedMs by remember { mutableStateOf<Long?>(null) }

    // Actualiza métricas de hardware cada segundo mientras está abierta la pantalla
    LaunchedEffect(Unit) {
        while (true) {
            memMetrics = DevPerformanceMonitor.getMemoryMetrics()
            batteryMetrics = DevPerformanceMonitor.getBatteryMetrics(context)
            benchmarks = DevPerformanceMonitor.getRecentBenchmarks()
            delay(1000)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(Fondo)) {
        // Cabecera
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .background(VerdeFuerte)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(2.dp, Fondo, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Text("‹", color = Fondo, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Panel de Rendimiento", fontFamily = Literata, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Fondo)
                    Text("Telemetría de hardware y banco de pruebas", fontFamily = IbmPlexMono, fontSize = 11.sp, color = Brote)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hardware y Memoria
            MetricSectionCard(title = "Memoria RAM (JVM + OpenCV)") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiBox("Heap Usado", "${memMetrics.heapUsedMb} MB", Brote)
                    KpiBox("Heap Asignado", "${memMetrics.heapAllocatedMb} MB", TarjetaFondo)
                    KpiBox("Heap Máx", "${memMetrics.heapMaxMb} MB", TarjetaFondo)
                    KpiBox("Nativo OpenCV", "${memMetrics.nativeHeapAllocatedMb} MB", Carton)
                }
            }

            // 2. Batería y Temperatura
            MetricSectionCard(title = "Batería y Térmica") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiBox("Nivel", "${batteryMetrics.levelPercent}%", if (batteryMetrics.levelPercent > 20) Brote else Color(0xFFFFB4A9))
                    KpiBox("Temperatura", "${"%.1f".format(batteryMetrics.temperatureCelsius)} °C", if (batteryMetrics.temperatureCelsius < 40f) Brote else Color(0xFFFFB4A9))
                    KpiBox("Voltaje", "${batteryMetrics.voltageMv} mV", TarjetaFondo)
                    KpiBox("Carga", if (batteryMetrics.isCharging) "Conectado" else "Batería", TarjetaFondo)
                }
            }

            // 3. Banco de prueba de detección (Accuracy Test Bench)
            MetricSectionCard(title = "Banco de Prueba OpenCV (Foto Real)") {
                Text(
                    "Ejecuta el detector multi-método sobre la colección de referencia estándar sin necesidad de apuntar la cámara.",
                    fontSize = 13.sp,
                    color = VerdeFuerte.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (testRunning) VerdeMedio.copy(alpha = 0.5f) else VerdeMedio)
                        .clickable(enabled = !testRunning) {
                            scope.launch {
                                testRunning = true
                                testResult = null
                                withContext(Dispatchers.Default) {
                                    try {
                                        context.assets.open("sample_fichas.jpeg").use { stream ->
                                            val bmp = BitmapFactory.decodeStream(stream)
                                            val t0 = System.currentTimeMillis()
                                            val result = PhotoAnalyzer.analyze(bmp)
                                            val t1 = System.currentTimeMillis()
                                            testResult = result
                                            testElapsedMs = t1 - t0
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                testRunning = false
                            }
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (testRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(color = Fondo, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Analizando muestra...", color = Fondo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Text("Ejecutar Test de Precisión", color = Fondo, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }

                testResult?.let { res ->
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(TarjetaFondo)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Resultado de evaluación:", fontWeight = FontWeight.Bold, color = VerdeFuerte, fontSize = 14.sp)
                            Text("• Tiempo de inferencia: ${testElapsedMs ?: 0} ms", fontFamily = IbmPlexMono, fontSize = 13.sp, color = VerdeFuerte)
                            Text("• Fichas reconocidas: ${res.positioned.size} de 11 figuras esperadas", fontFamily = IbmPlexMono, fontSize = 13.sp, color = VerdeFuerte)
                            val tasa = if (res.positioned.isNotEmpty()) (res.positioned.size.toFloat() / 11f * 100).toInt() else 0
                            Text("• Cobertura estimada: $tasa%", fontFamily = IbmPlexMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VerdeMedio)
                            res.aviso?.let {
                                Text("• Advertencia: $it", fontSize = 12.sp, color = Color(0xFFBA1A1A))
                            }
                        }
                    }
                }
            }

            // 4. Registro de latencias recientes
            MetricSectionCard(title = "Últimos Benchmarks Registrados") {
                if (benchmarks.isEmpty()) {
                    Text(
                        "Aún no hay capturas recientes. Toma una foto en 'Escanear' o corre el banco de pruebas arriba.",
                        fontSize = 13.sp,
                        color = VerdeFuerte.copy(alpha = 0.7f)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        benchmarks.take(8).forEach { rec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(TarjetaFondo)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rec.tag, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VerdeFuerte)
                                    if (rec.details.isNotEmpty()) {
                                        Text(rec.details, fontFamily = IbmPlexMono, fontSize = 11.sp, color = VerdeFuerte.copy(alpha = 0.7f))
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (rec.durationMs < 1000) Brote else Color(0xFFFFD8E4))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${rec.durationMs} ms", fontFamily = IbmPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VerdeFuerte)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MetricSectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, VerdeFuerte.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, fontFamily = Literata, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = VerdeFuerte)
        content()
    }
}

@Composable
private fun KpiBox(label: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(label, fontFamily = IbmPlexMono, fontSize = 11.sp, color = VerdeFuerte.copy(alpha = 0.8f))
            Text(value, fontFamily = IbmPlexMono, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = VerdeFuerte)
        }
    }
}
