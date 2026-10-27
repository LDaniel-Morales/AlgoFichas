package com.dan.opencv.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Reconocedor de texto manuscrito e impreso on-device, 100% offline.
 * Utiliza el modelo empaquetado (bundled) de Google ML Kit para caracteres latinos.
 * No requiere servicios de red ni Google Play Services activos.
 */
object FichaOcrEngine {

    // Cliente ML Kit estático reutilizable para evitar reinicializaciones costosas
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Procesa de forma asíncrona un [Bitmap] de una ficha recortada y extrae su texto limpio.
     */
    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text.trim()
                val clean = cleanOcrResult(rawText)
                if (continuation.isActive) {
                    continuation.resume(clean)
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume("")
                }
            }
    }

    /**
     * Limpia caracteres parásitos habituales del OCR sobre bordes de papel o sombras
     * y normaliza a minúsculas para garantizar insensibilidad a mayúsculas/minúsculas.
     */
    private fun cleanOcrResult(text: String): String {
        return text
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("[_~`|^]+"), "")
            .trim()
            .lowercase()
    }
}
