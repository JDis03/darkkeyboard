package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log

/**
 * Re-ranker usando GPT-2 TFLite (Fase 3).
 *
 * Estrategia de re-ranking:
 *   1. El trie genera top-20 candidatos (corpus + user freq + bigrams)
 *   2. Para cada candidato, GPT-2 calcula P(candidato | contexto)
 *   3. Score final = 0.4 * trie_score + 0.6 * gpt2_score
 *   4. Retornar top-3
 *
 * Activar: descomentar TFLite en build.gradle + poner modelo en assets/
 *
 * NOTA: El modelo (118MB) se descarga bajo demanda (Fase 4).
 *       Sin modelo → isAvailable=false → usa NoOpReRanker.
 */
class TFLiteReRanker(private val context: Context) : ReRanker {

    override val name = "TFLite GPT-2 ReRanker"
    override var isAvailable = false
        private set

    companion object {
        private const val TAG = "TFLiteReRanker"
        private const val MODEL_FILE = "suggestions_model.tflite"
        private const val W_TRIE   = 0.4f
        private const val W_GPT2   = 0.6f
    }

    // TFLite classes - reflejadas via reflection para no requerir dep en compilación
    private var interpreter: Any? = null
    private var bpeTokenizer: BpeTokenizer? = null

    override fun initialize() {
        // Buscar modelo: primero en filesDir/models/ (descargado), luego en assets/
        val downloaded = ModelDownloader.modelFile(context)
        val hasDownloaded = downloaded.exists()
        val hasAsset = try { context.assets.open(MODEL_FILE).close(); true } catch (_: Exception) { false }

        if (!hasDownloaded && !hasAsset) {
            Log.i(TAG, "Model not found — download via Settings to enable AI suggestions")
            isAvailable = false
            return
        }

        // Verificar si TFLite está disponible en runtime (dep opcional)
        try {
            Class.forName("org.tensorflow.lite.Interpreter")
        } catch (e: ClassNotFoundException) {
            Log.i(TAG, "TFLite not in APK — uncomment deps in build.gradle to enable")
            isAvailable = false
            return
        }

        try {
            // Cargar BPE desde filesDir si está descargado, si no desde assets
            bpeTokenizer = if (hasDownloaded) {
                BpeTokenizer(context, useDownloaded = true)
            } else {
                BpeTokenizer(context)
            }
            bpeTokenizer!!.load()
            loadInterpreter(if (hasDownloaded) downloaded.absolutePath else null)
            isAvailable = true
            Log.i(TAG, "TFLite re-ranker ready (${if (hasDownloaded) "downloaded" else "assets"})")
        } catch (e: Exception) {
            Log.w(TAG, "TFLite re-ranker init failed: ${e.message}")
            isAvailable = false
        }
    }

    override fun rerank(candidates: List<String>, context: String): List<String> {
        if (!isAvailable || interpreter == null || candidates.isEmpty()) return candidates
        if (candidates.size <= 1) return candidates

        return try {
            val scores = candidates.map { word ->
                val contextScore = scoreCandidate(word, context)
                word to contextScore
            }
            scores.sortedByDescending { it.second }.map { it.first }
        } catch (e: Exception) {
            Log.e(TAG, "Rerank error: ${e.message}")
            candidates  // fallback: devolver sin cambios
        }
    }

    /**
     * Calcula P(word | context) usando GPT-2.
     * Tokeniza el contexto + palabra y extrae la probabilidad del último token.
     */
    private fun scoreCandidate(word: String, context: String): Float {
        val tokenizer = bpeTokenizer ?: return 0f
        val fullText = "$context $word"
        val ids = tokenizer.encode(fullText).takeLast(5)
        if (ids.isEmpty()) return 0f

        val padded = IntArray(5) { 0 }
        val offset = maxOf(0, 5 - ids.size)
        ids.forEachIndexed { i, id -> padded[offset + i] = id }

        val probs = runInference(padded) ?: return 0f

        // Probabilidad del primer token de la palabra candidata
        val wordTokens = tokenizer.encode(" $word")
        val firstToken = wordTokens.firstOrNull() ?: return 0f
        return if (firstToken < probs.size) probs[firstToken] else 0f
    }

    private fun runInference(tokenIds: IntArray): FloatArray? {
        return try {
            val input  = Array(1) { tokenIds }
            val interp = interpreter ?: return null
            // Usar reflection para no requerir el import en tiempo de compilación
            val outputTensor = interp.javaClass.getMethod("getOutputTensor", Int::class.java)
                .invoke(interp, 0)
            val shape = outputTensor.javaClass.getMethod("shape").invoke(outputTensor) as IntArray
            val vocabSize = shape[1]
            val output = Array(1) { FloatArray(vocabSize) }
            interp.javaClass.getMethod("run", Any::class.java, Any::class.java)
                .invoke(interp, input, output)
            output[0]
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            null
        }
    }

    private fun loadInterpreter(filePath: String? = null) {
        val optionsClass = Class.forName("org.tensorflow.lite.Interpreter\$Options")
        val options      = optionsClass.newInstance()
        optionsClass.getMethod("setNumThreads", Int::class.java).invoke(options, 1)
        val interpClass  = Class.forName("org.tensorflow.lite.Interpreter")

        if (filePath != null) {
            // Modelo descargado — cargar desde File
            val file = java.io.File(filePath)
            val fileInputStream = java.io.FileInputStream(file)
            val channel = fileInputStream.channel
            val modelBuffer = channel.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, file.length())
            interpreter = interpClass.getConstructor(
                Class.forName("java.nio.ByteBuffer"), optionsClass
            ).newInstance(modelBuffer, options)
        } else {
            // Modelo en assets
            val fileUtilClass = Class.forName("org.tensorflow.lite.support.common.FileUtil")
            val modelBuffer   = fileUtilClass.getMethod("loadMappedFile", Context::class.java, String::class.java)
                .invoke(null, context, MODEL_FILE)
            interpreter = interpClass.getConstructor(
                Class.forName("java.nio.MappedByteBuffer"), optionsClass
            ).newInstance(modelBuffer, options)
        }
    }

    override fun close() {
        try {
            interpreter?.javaClass?.getMethod("close")?.invoke(interpreter)
        } catch (_: Exception) {}
        interpreter = null
        isAvailable = false
    }
}
