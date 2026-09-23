package com.dan.opencv

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Carga la librería nativa de OpenCV (libopencv_java5.so) una sola vez,
 * al arrancar el proceso, antes de que cualquier Activity intente usarla.
 */
class OpenCvApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val loaded = OpenCVLoader.initLocal()
        if (loaded) {
            Log.i(TAG, "OpenCV ${OpenCVLoader.OPENCV_VERSION} cargado correctamente")
        } else {
            Log.e(TAG, "No se pudo inicializar OpenCV")
        }
    }

    companion object {
        private const val TAG = "OpenCvApplication"
    }
}
