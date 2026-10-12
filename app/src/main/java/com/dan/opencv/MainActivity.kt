package com.dan.opencv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dan.opencv.ui.HomeScreen
import com.dan.opencv.ui.NavTab
import com.dan.opencv.ui.RetoDetailScreen
import com.dan.opencv.ui.RetosScreen
import com.dan.opencv.ui.ScanScreen
import com.dan.opencv.ui.theme.OpencvTheme

/** Las pantallas de la app y cómo se navega entre ellas (sin Navigation Compose: solo 4 destinos). */
private sealed class AppScreen : java.io.Serializable {
    data object Home : AppScreen()
    /** [retoIndex] es el reto cuya solución se escanea, o null si se entró desde Inicio. */
    data class Scan(val retoIndex: Int? = null) : AppScreen()
    data object Retos : AppScreen()
    data class RetoDetail(val retoIndex: Int) : AppScreen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpencvTheme {
                var screen by rememberSaveable { mutableStateOf<AppScreen>(AppScreen.Home) }

                fun onNavTab(tab: NavTab) {
                    screen = when (tab) {
                        NavTab.INICIO -> AppScreen.Home
                        NavTab.RETOS -> AppScreen.Retos
                        NavTab.GRUPO -> AppScreen.Home // "Grupo" no tiene pantalla propia todavía
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (val s = screen) {
                        AppScreen.Home -> HomeScreen(
                            onScanClick = { screen = AppScreen.Scan() },
                            onNavigate = ::onNavTab,
                            modifier = Modifier.padding(innerPadding)
                        )
                        is AppScreen.Scan -> ScanScreen(
                            retoIndex = s.retoIndex,
                            onBack = { screen = s.retoIndex?.let { AppScreen.RetoDetail(it) } ?: AppScreen.Home },
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.Retos -> RetosScreen(
                            onOpenReto = { index -> screen = AppScreen.RetoDetail(index) },
                            onNavigate = ::onNavTab,
                            modifier = Modifier.padding(innerPadding)
                        )
                        is AppScreen.RetoDetail -> RetoDetailScreen(
                            retoIndex = s.retoIndex,
                            onBack = { screen = AppScreen.Retos },
                            onScanSolution = { screen = AppScreen.Scan(s.retoIndex) },
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
