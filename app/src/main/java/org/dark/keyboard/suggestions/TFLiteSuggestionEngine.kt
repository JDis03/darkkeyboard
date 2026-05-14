package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.InputStreamReader
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
                useNNAPI = false
                addDelegate(FlexDelegate())
            }
            interpreter = Interpreter(modelBuffer, options)

            // Log tensor info para debugging
            val inputDetails = interpreter!!.getInputTensor(0)
            val outputDetails = interpreter!!.getOutputTensor(0)
            Log.i(TAG, "Input tensor: shape=${inputDetails.shape().toList()} dtype=${inputDetails.dataType()}")
            Log.i(TAG, "Output tensor: shape=${outputDetails.shape().toList()} dtype=${outputDetails.dataType()}")

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
            Log.d(TAG, "Tokenized '${textBeforeCursor.takeLast(20)}' -> ${tokenIds.toList()}")
            val probabilities = runInference(tokenIds)
            val results = topK(probabilities, maxResults)
            Log.d(TAG, "Suggestions: $results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Tokeniza texto usando el vocabulario BPE del modelo.
     * GPT-2 usa BPE donde las palabras se representan como subwords.
     * Aproximación: buscar coincidencias exactas y por prefijo en el vocab.
     */
    private fun tokenize(text: String): IntArray {
        val ids = mutableListOf<Int>()

        // Tokenización simple: cada "word" del texto como token
        // GPT-2 BPE usa Ġ como prefijo de espacio — buscar con y sin espacio
        val words = text.trim().split(Regex("\\s+")).takeLast(SEQ_LENGTH * 2)
        for (word in words) {
            // Intentar con espacio primero (Ġword) y luego sin espacio
            val tokenId = vocab[" $word"]
                ?: vocab[word.lowercase()]
                ?: vocab[" ${word.lowercase()}"]
                ?: UNK_TOKEN
            ids.add(tokenId)
            if (ids.size >= SEQ_LENGTH) break
        }

        // Padding al inicio si es necesario
        val padded = IntArray(SEQ_LENGTH) { PAD_TOKEN }
        val offset = maxOf(0, SEQ_LENGTH - ids.size)
        ids.takeLast(SEQ_LENGTH).forEachIndexed { i, id -> padded[offset + i] = id }
        return padded
    }

    /**
     * Corre inferencia usando el output tensor por índice (más robusto que por nombre).
     * Input shape:  [1, SEQ_LENGTH] int32
     * Output shape: [1, VOCAB_SIZE] float32
     */
    private fun runInference(tokenIds: IntArray): FloatArray {
        val input = Array(1) { tokenIds }

        // Obtener tamaño real del vocab del output tensor
        val vocabSize = interpreter!!.getOutputTensor(0).shape()[1]
        val outputBuffer = Array(1) { FloatArray(vocabSize) }

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
            .mapNotNull { (idx, _) ->
                reverseVocab[idx]?.trim()?.takeIf { it.length >= 2 && !it.startsWith("<") && it.all { c -> c.isLetter() || c == '\'' } }
            }
            .distinct()
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
                // Guardamos el token tal como está (con espacio si lo tiene)
                // El vocab.txt ya tiene los tokens limpios con espacio al inicio
                if (line.isNotEmpty()) {
                    vocab[line] = idx          // "Ġword" → idx  (con espacio ya procesado)
                    reverseVocab[idx] = line   // idx → "word"
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
