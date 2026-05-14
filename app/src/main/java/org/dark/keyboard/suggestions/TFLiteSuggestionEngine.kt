package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.support.common.FileUtil

/**
 * Motor de sugerencias TFLite con BPE real (gpt2-spanish).
 *
 * Best practices aplicadas:
 * - BPE cache LRU (máx 1024 entradas, evita memory leak)
 * - topK strip leading space antes de isLetter() check
 * - getSuggestions recibe partialWord para filtrar substrings
 * - reverseVocab con cleaned strings (sin espacio inicial)
 */
class TFLiteSuggestionEngine(private val context: Context) : SuggestionEngine {

    override val engineName = "TFLite (gpt2-spanish)"

    companion object {
        private const val TAG        = "TFLiteEngine"
        private const val MODEL_FILE = "suggestions_model.tflite"
        private const val SEQ_LENGTH = 5
        private const val PAD_TOKEN  = 0
    }

    private var interpreter: Interpreter? = null
    private val bpeTokenizer = BpeTokenizer(context)

    // id → palabra limpia (sin espacio inicial, para display)
    private val reverseVocab = mutableMapOf<Int, String>()
    // id → true si el token original empieza con espacio (= inicio de palabra)
    private val isWordStart  = mutableMapOf<Int, Boolean>()

    private var isReady  = false
    private val inferLock = Any()  // TFLite Interpreter no es thread-safe

    override fun initialize() {
        try {
            bpeTokenizer.load()
            buildReverseVocab()

            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 1        // 1 thread evita XNNPACK concurrency error
                useNNAPI  = false
                addDelegate(FlexDelegate())
            }
            interpreter = Interpreter(modelBuffer, options)

            val inShape  = interpreter!!.getInputTensor(0).shape().toList()
            val outShape = interpreter!!.getOutputTensor(0).shape().toList()
            val wordStarts = isWordStart.values.count { it }
            Log.i(TAG, "Ready — input:$inShape output:$outShape wordStarts:$wordStarts")

            isReady = true
        } catch (e: Exception) {
            Log.w(TAG, "Init failed, using fallback: ${e.message}")
            isReady = false
        }
    }

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (!isReady || interpreter == null) return emptyList()
        if (textBeforeCursor.isBlank()) return emptyList()

        // Extraer la palabra parcial actual (última palabra incompleta)
        val endsWithSpace = textBeforeCursor.endsWith(" ")
        val partialWord = if (endsWithSpace) ""
            else textBeforeCursor.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""

        return try {
            val tokenIds = tokenize(textBeforeCursor)
            Log.d(TAG, "tokens=${tokenIds.toList()} partial='$partialWord'")
            val probs   = synchronized(inferLock) { runInference(tokenIds) }
            val results = topK(probs, maxResults, partialWord)
            Log.d(TAG, "suggestions=$results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "getSuggestions error: ${e.message}", e)
            emptyList()
        }
    }

    private fun tokenize(text: String): IntArray {
        val allIds = bpeTokenizer.encode(text)
        val ids    = allIds.takeLast(SEQ_LENGTH)
        val padded = IntArray(SEQ_LENGTH) { PAD_TOKEN }
        val offset = maxOf(0, SEQ_LENGTH - ids.size)
        ids.forEachIndexed { i, id -> padded[offset + i] = id }
        return padded
    }

    private fun runInference(tokenIds: IntArray): FloatArray {
        val vocabSize = interpreter!!.getOutputTensor(0).shape()[1]
        val input     = Array(1) { tokenIds }
        val output    = Array(1) { FloatArray(vocabSize) }
        interpreter!!.run(input, output)
        return output[0]
    }

    /**
     * Top-K con best practices:
     * 1. Solo tokens word-start (espacio inicial en BPE)
     * 2. Strip espacio ANTES de isLetter() check (fix crítico)
     * 3. Solo palabras de 2+ letras puras
     * 4. Filtra si ya está escrito o es substring de lo escrito
     * 5. Normaliza case al del usuario (si escribió minúscula, sugiere minúscula)
     */
    private fun topK(probs: FloatArray, k: Int, partialWord: String = ""): List<String> {
        val partialLower = partialWord.lowercase()

        return probs
            .mapIndexed { idx, prob -> idx to prob }
            .sortedByDescending { it.second }
            .asSequence()
            .filter { (idx, _) -> isWordStart[idx] == true }
            .mapNotNull { (idx, _) ->
                val clean = reverseVocab[idx] ?: return@mapNotNull null
                // clean ya NO tiene espacio inicial (ver buildReverseVocab)
                if (clean.length < 2) return@mapNotNull null
                if (!clean.all { c -> c.isLetter() }) return@mapNotNull null
                if (clean.startsWith("<")) return@mapNotNull null
                clean
            }
            .filter { word ->
                val wordLower = word.lowercase()
                // No sugerir si la sugerencia ES lo que ya se escribió
                wordLower != partialLower &&
                // No sugerir si es substring más corto de lo ya escrito
                !(partialLower.length >= word.length && partialLower.startsWith(wordLower))
            }
            .map { word ->
                // Normalizar case: si el usuario escribió minúscula → sugerir minúscula
                if (partialWord.isNotEmpty() && partialWord[0].isLowerCase()) {
                    word.replaceFirstChar { it.lowercase() }
                } else {
                    word
                }
            }
            .distinct()
            .take(k)
            .toList()
    }

    /**
     * Construye reverseVocab con strings LIMPIOS (sin espacio inicial).
     * isWordStart marca qué tokens son inicio de palabra.
     */
    private fun buildReverseVocab() {
        reverseVocab.clear()
        isWordStart.clear()
        val vocabSize = 50257
        for (id in 0 until vocabSize) {
            val decoded = bpeTokenizer.decode(id)
            if (decoded.isEmpty()) continue
            val startsWithSpace = decoded.startsWith(" ")
            // Guardar LIMPIO (sin espacio) para no romper isLetter() en topK
            reverseVocab[id] = if (startsWithSpace) decoded.trimStart() else decoded
            isWordStart[id]  = startsWithSpace
        }
        Log.i(TAG, "ReverseVocab: ${reverseVocab.size} tokens, ${isWordStart.values.count{it}} word-starts")
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }

    fun isReady() = isReady
}
