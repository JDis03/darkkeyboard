package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.support.common.FileUtil


/**
 * Motor de sugerencias basado en TFLite (gpt2-spanish).
 *
 * Fixes aplicados:
 * - Thread safety: synchronized en runInference (XNNPACK no es thread-safe)
 * - Tokenización BPE correcta: busca " palabra" (con espacio) como lo hace GPT-2
 * - Vocab cargado con UTF-8 explícito para acentos españoles
 * - topK filtra subwords (tokens sin espacio inicial = continuación de palabra)
 */
class TFLiteSuggestionEngine(private val context: Context) : SuggestionEngine {

    override val engineName = "TFLite (gpt2-spanish)"

    companion object {
        private const val TAG = "TFLiteEngine"
        private const val MODEL_FILE = "suggestions_model.tflite"
        private const val VOCAB_FILE  = "suggestions_vocab.txt"
        private const val SEQ_LENGTH  = 5
        private const val UNK_TOKEN   = 1
        private const val PAD_TOKEN   = 0
    }

    private var interpreter: Interpreter? = null
    private val bpeTokenizer = BpeTokenizer(context)
    // id → token_string decodificado (UTF-8 limpio)
    private val reverseVocab = mutableMapOf<Int, String>()
    // marcar si el token empieza con espacio (= inicio de palabra)
    private val isWordStart = mutableMapOf<Int, Boolean>()

    private var isReady = false
    private val inferLock = Any()   // mutex para thread safety

    override fun initialize() {
        try {
            // Cargar tokenizador BPE real
            bpeTokenizer.load()

            // Construir reverseVocab y isWordStart desde el BPE tokenizer
            buildReverseVocab()

            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 1
                useNNAPI = false
                addDelegate(FlexDelegate())
            }
            interpreter = Interpreter(modelBuffer, options)

            val inShape  = interpreter!!.getInputTensor(0).shape().toList()
            val outShape = interpreter!!.getOutputTensor(0).shape().toList()
            Log.i(TAG, "Input: $inShape  Output: $outShape  ReverseVocab: ${reverseVocab.size}")

            isReady = true
        } catch (e: Exception) {
            Log.w(TAG, "TFLite init failed, using fallback: ${e.message}")
            isReady = false
        }
    }

    private fun buildReverseVocab() {
        reverseVocab.clear()
        isWordStart.clear()
        // Reconstruir desde el BPE tokenizer decodificando cada token
        val vocabSize = 50257
        for (id in 0 until vocabSize) {
            val decoded = bpeTokenizer.decode(id)
            if (decoded.isNotEmpty()) {
                reverseVocab[id] = decoded
                isWordStart[id] = decoded.startsWith(" ")
            }
        }
        Log.i(TAG, "ReverseVocab built: ${reverseVocab.size} tokens, ${isWordStart.values.count { it }} word-starts")
    }

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (!isReady || interpreter == null) return emptyList()
        if (textBeforeCursor.isBlank()) return emptyList()

        return try {
            val tokenIds = tokenize(textBeforeCursor)
            Log.d(TAG, "tokens=${tokenIds.toList()} for '${textBeforeCursor.takeLast(30)}'")
            val probs = synchronized(inferLock) { runInference(tokenIds) }
            val results = topK(probs, maxResults)
            Log.d(TAG, "suggestions=$results")
            results
        } catch (e: Exception) {
            Log.e(TAG, "getSuggestions error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Tokeniza el texto en IDs usando el vocabulario BPE de GPT-2.
     *
     * GPT-2 BPE representa palabras como " palabra" (con espacio inicial).
     * Solo tokenizamos las últimas SEQ_LENGTH palabras completas.
     * Para la palabra actual (última, incompleta) también la incluimos
     * para que el modelo prediga su completado o la siguiente palabra.
     */
    /**
     * Tokeniza usando BPE real — igual que el tokenizador de HuggingFace.
     * Toma los últimos SEQ_LENGTH tokens del texto.
     */
    private fun tokenize(text: String): IntArray {
        val allIds = bpeTokenizer.encode(text)
        val ids = allIds.takeLast(SEQ_LENGTH)
        val padded = IntArray(SEQ_LENGTH) { PAD_TOKEN }
        val offset = maxOf(0, SEQ_LENGTH - ids.size)
        ids.forEachIndexed { i, id -> padded[offset + i] = id }
        return padded
    }

    private fun runInference(tokenIds: IntArray): FloatArray {
        val vocabSize = interpreter!!.getOutputTensor(0).shape()[1]
        val input  = Array(1) { tokenIds }
        val output = Array(1) { FloatArray(vocabSize) }
        interpreter!!.run(input, output)
        return output[0]
    }

    /**
     * Top-K filtrando correctamente tokens BPE:
     * - Solo tokens que empiezan con espacio (= inicio de palabra completa)
     * - Solo letras (sin números, puntuación, tokens especiales)
     * - Mínimo 2 caracteres después de quitar el espacio
     */
    private fun topK(probabilities: FloatArray, k: Int): List<String> {
        return probabilities
            .mapIndexed { idx, prob -> idx to prob }
            .sortedByDescending { it.second }
            .asSequence()
            .filter { (idx, _) -> isWordStart[idx] == true }
            .mapNotNull { (idx, _) ->
                reverseVocab[idx]?.takeIf { word ->
                    word.length >= 2 &&
                    word.all { c -> c.isLetter() } &&
                    !word.startsWith("<")
                }
            }
            .distinct()
            .take(k)
            .toList()
    }


    override fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }

    fun isReady() = isReady
}
