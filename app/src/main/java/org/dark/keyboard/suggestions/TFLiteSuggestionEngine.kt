package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Motor de sugerencias basado en TFLite.
 *
 * Nivel 1: modelo next-word prediction básico (~3-5MB)
 *   - Input:  secuencia de token IDs (últimas N palabras)
 *   - Output: distribución de probabilidad sobre vocabulario
 *   - Assets: suggestions_model.tflite + vocab.txt
 *
 * Para escalar a Nivel 2-4:
 *   - Cambiar el archivo .tflite en assets/
 *   - Ajustar SEQ_LENGTH y VOCAB_SIZE si es necesario
 *   - El resto del código no cambia
 *
 * Modelo esperado en: assets/suggestions_model.tflite
 * Vocabulario en:     assets/suggestions_vocab.txt
 */
class TFLiteSuggestionEngine(private val context: Context) : SuggestionEngine {

    override val engineName = "TFLite (next-word prediction)"

    companion object {
        private const val TAG = "TFLiteEngine"
        private const val MODEL_FILE = "suggestions_model.tflite"
        private const val VOCAB_FILE = "suggestions_vocab.txt"
        private const val SEQ_LENGTH = 5      // tokens de contexto que acepta el modelo
        private const val MAX_VOCAB = 10000   // tamaño del vocabulario
        private const val UNK_TOKEN = 1       // token para palabras desconocidas
        private const val PAD_TOKEN = 0       // token de padding
    }

    private var interpreter: Interpreter? = null
    private val vocab = mutableMapOf<String, Int>()       // palabra → token ID
    private val reverseVocab = mutableMapOf<Int, String>() // token ID → palabra
    private var isReady = false

    override fun initialize() {
        try {
            // Cargar vocabulario
            loadVocab()

            // Cargar modelo TFLite
            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 2
                useNNAPI = false  // NNAPI puede ser inestable en algunos devices
            }
            interpreter = Interpreter(modelBuffer, options)
            isReady = true
            Log.i(TAG, "TFLite engine ready — vocab=${vocab.size}, model=$MODEL_FILE")
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not available, falling back: ${e.message}")
            isReady = false
        }
    }

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (!isReady || interpreter == null) return emptyList()
        if (textBeforeCursor.isBlank()) return emptyList()

        return try {
            val tokenIds = tokenize(textBeforeCursor)
            val probabilities = runInference(tokenIds)
            topK(probabilities, maxResults)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Convierte texto en secuencia de token IDs con padding.
     * Toma las últimas SEQ_LENGTH palabras.
     */
    private fun tokenize(text: String): IntArray {
        val words = text.trim().lowercase().split(Regex("\\s+")).takeLast(SEQ_LENGTH)
        val ids = IntArray(SEQ_LENGTH) { PAD_TOKEN }
        val offset = SEQ_LENGTH - words.size
        words.forEachIndexed { i, word ->
            ids[offset + i] = vocab[word] ?: UNK_TOKEN
        }
        return ids
    }

    /**
     * Corre inferencia y retorna array de probabilidades sobre el vocabulario.
     * Input shape:  [1, SEQ_LENGTH]
     * Output shape: [1, MAX_VOCAB]
     */
    private fun runInference(tokenIds: IntArray): FloatArray {
        val inputBuffer = IntBuffer.wrap(tokenIds)
        val outputBuffer = Array(1) { FloatArray(MAX_VOCAB) }

        // Reshape input como array 2D [1, SEQ_LENGTH]
        val input = Array(1) { tokenIds }
        interpreter!!.run(input, outputBuffer)

        return outputBuffer[0]
    }

    /**
     * Retorna las top-K palabras con mayor probabilidad.
     * Filtra palabras muy cortas y tokens especiales.
     */
    private fun topK(probabilities: FloatArray, k: Int): List<String> {
        return probabilities
            .mapIndexed { idx, prob -> idx to prob }
            .sortedByDescending { it.second }
            .asSequence()
            .mapNotNull { (idx, _) -> reverseVocab[idx] }
            .filter { word -> word.length >= 2 && !word.startsWith("<") }
            .take(k)
            .toList()
    }

    /**
     * Carga vocabulario desde assets/suggestions_vocab.txt
     * Formato: una palabra por línea, el índice de línea = token ID
     */
    private fun loadVocab() {
        vocab.clear()
        reverseVocab.clear()
        val inputStream = context.assets.open(VOCAB_FILE)
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var idx = 0
            reader.forEachLine { line ->
                val word = line.trim()
                if (word.isNotEmpty()) {
                    vocab[word] = idx
                    reverseVocab[idx] = word
                    idx++
                }
            }
        }
        Log.i(TAG, "Vocab loaded: ${vocab.size} tokens")
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }

    /**
     * Retorna true si el modelo está cargado y listo.
     * Útil para que DarkIME2 decida si usar TFLite o Fallback.
     */
    fun isReady() = isReady
}
