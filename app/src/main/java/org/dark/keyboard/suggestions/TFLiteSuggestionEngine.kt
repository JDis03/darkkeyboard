package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.flex.FlexDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

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
    // token_string → id  (key incluye espacio inicial: " hola")
    private val vocab = mutableMapOf<String, Int>()
    // id → token_string limpio (sin espacio)
    private val reverseVocab = mutableMapOf<Int, String>()
    // marcar si el token empieza con espacio (= inicio de palabra)
    private val isWordStart = mutableMapOf<Int, Boolean>()

    private var isReady = false
    private val inferLock = Any()   // mutex para thread safety

    override fun initialize() {
        try {
            loadVocab()

            val modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                numThreads = 1          // 1 thread evita el XNNPACK concurrency error
                useNNAPI = false
                addDelegate(FlexDelegate())
            }
            interpreter = Interpreter(modelBuffer, options)

            val inShape  = interpreter!!.getInputTensor(0).shape().toList()
            val outShape = interpreter!!.getOutputTensor(0).shape().toList()
            Log.i(TAG, "Input: $inShape  Output: $outShape  Vocab: ${vocab.size}")

            isReady = true
        } catch (e: Exception) {
            Log.w(TAG, "TFLite init failed, using fallback: ${e.message}")
            isReady = false
        }
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
    private fun tokenize(text: String): IntArray {
        val words = text.trimEnd().split(Regex("\\s+")).filter { it.isNotEmpty() }.takeLast(SEQ_LENGTH)
        val ids = mutableListOf<Int>()

        words.forEachIndexed { i, word ->
            val isFirst = i == 0
            val id = lookupToken(word, withSpace = !isFirst)
            ids.add(id)
        }

        val padded = IntArray(SEQ_LENGTH) { PAD_TOKEN }
        val offset = maxOf(0, SEQ_LENGTH - ids.size)
        ids.takeLast(SEQ_LENGTH).forEachIndexed { i, id -> padded[offset + i] = id }
        return padded
    }

    /**
     * Busca el token ID de una palabra con varios fallbacks:
     * 1. Con espacio + original:    " Hola"
     * 2. Con espacio + minúscula:   " hola"
     * 3. Con espacio + capitalize:  " hola" → " Hola"
     * 4. Sin espacio + original:    "Hola"
     * 5. Sin espacio + minúscula:   "hola"
     * 6. UNK
     */
    private fun lookupToken(word: String, withSpace: Boolean): Int {
        val sp = if (withSpace) " " else ""
        return vocab["$sp$word"]
            ?: vocab["$sp${word.lowercase()}"]
            ?: vocab["$sp${word.replaceFirstChar { it.uppercase() }}"]
            ?: vocab[word]
            ?: vocab[word.lowercase()]
            ?: vocab[word.replaceFirstChar { it.uppercase() }]
            ?: UNK_TOKEN
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

    /**
     * Carga el vocabulario desde assets/suggestions_vocab.txt
     *
     * El vocab fue generado por el script Python con:
     *   token.replace("Ġ", " ")  — Ġ es el byte 0xC4 0xA0 que representa espacio en GPT-2
     *
     * Entonces tokens con espacio inicial = inicio de palabra.
     */
    private fun loadVocab() {
        vocab.clear()
        reverseVocab.clear()
        isWordStart.clear()

        context.assets.open(VOCAB_FILE).use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var idx = 0
                reader.forEachLine { line ->
                    val startsWithSpace = line.startsWith(" ")
                    val clean = line.trim()   // sin espacio para display

                    // Indexar con y sin espacio para lookup en tokenize()
                    vocab[line] = idx              // key original (puede tener espacio)
                    if (startsWithSpace) {
                        vocab[line.trimStart()] = idx  // también sin espacio
                    }

                    reverseVocab[idx] = clean
                    isWordStart[idx] = startsWithSpace

                    idx++
                }
            }
        }
        Log.i(TAG, "Vocab loaded: ${vocab.size} entries, ${reverseVocab.size} tokens")
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        isReady = false
    }

    fun isReady() = isReady
}
