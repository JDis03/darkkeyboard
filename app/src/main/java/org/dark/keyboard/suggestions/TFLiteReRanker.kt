package org.dark.keyboard.suggestions

import android.content.Context
import timber.log.Timber
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Re-ranker T5 — Fase 3 del pipeline de sugerencias de DarkKeyboard.
 *
 * Modelo: t5_small_multi encoder mean-pool, TFLite INT8 (34MB).
 * Tokenizador: SentencePiece puro Kotlin (spiece.model, ~800KB).
 * Aceleración: XNNPack (built-in en TFLite 2.16+, recomendado para Android 15+).
 *
 * ── Arquitectura ─────────────────────────────────────────────────────────────
 *
 *   Trie → top-20 candidatos [~1ms]
 *       ↓
 *   T5 embed(context)       → ctxEmb  [float[512]]
 *   T5 embed(word)          → wordEmb [float[512]]
 *       ↓
 *   score_i = cosine_similarity(ctxEmb, wordEmb)
 *       ↓
 *   Top-3 reordenados
 *
 * ── Modelo TFLite ────────────────────────────────────────────────────────────
 *
 *   Signature : "serving_default"
 *   Inputs    : encoder_token_ids [1,32] int32
 *               encoder_padding_mask [1,32] int32
 *   Output    : output_0 [1,512] float32
 */
class TFLiteReRanker(private val context: Context) : ReRanker {

    override val name = "T5 Encoder Re-ranker (t5_small_multi)"
    override var isAvailable = false
        private set

    companion object {
        private const val MODEL_FILE    = "suggestions_model.tflite"
        private const val SIGNATURE_KEY = "serving_default"
        private const val SEQ_LEN       = 32
        private const val HIDDEN_SIZE   = 512
        private const val IN_TOKEN_IDS  = "encoder_token_ids"
        private const val IN_MASK       = "encoder_padding_mask"
        private const val OUT_EMBEDDING = "output_0"
    }

    private var interpreter : Interpreter? = null
    private var tokenizer   : SentencePieceTokenizer? = null
    private val lock = Any()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun initialize() {
        val modelFile = ModelDownloader.modelFile(context)
        if (!modelFile.exists()) {
            Timber.i("T5 model not found — download via Settings")
            return
        }

        try {
            tokenizer = SentencePieceTokenizer(context).also { it.load() }
            if (tokenizer?.isReady != true) {
                Timber.w("SentencePiece not ready: ${tokenizer?.debugInfo()}")
                return
            }

            initInterpreter(modelFile)
            isAvailable = true

            Timber.i(
                "T5 ReRanker ready | ${modelFile.length() / 1_048_576}MB " +
                "| ${tokenizer?.debugInfo()} | XNNPack | seq=$SEQ_LEN"
            )
        } catch (e: Exception) {
            Timber.e(e, "T5 ReRanker init failed")
            close()
        }
    }

    private fun initInterpreter(modelFile: File) {
        val modelBuffer: MappedByteBuffer = FileInputStream(modelFile).channel.use { ch ->
            ch.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }

        val options = Interpreter.Options().apply {
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            // XNNPack está habilitado por defecto en TFLite 2.16+
            // NNAPI está deprecated desde Android 14 — no necesitamos añadir delegates
        }

        interpreter = Interpreter(modelBuffer, options)

        val interp = interpreter!!
        Timber.i("Interpreter: inputs=${interp.inputTensorCount}, outputs=${interp.outputTensorCount}, delegate=XNNPack")
        repeat(interp.inputTensorCount) { i ->
            val t = interp.getInputTensor(i)
            Timber.d("Input[$i] '${t.name()}' shape=${t.shape().toList()} dtype=${t.dataType()}")
        }
    }

    // ── Re-ranking ────────────────────────────────────────────────────────────

    override fun rerank(candidates: List<String>, context: String): List<String> {
        if (!isAvailable || interpreter == null || candidates.size <= 1) return candidates

        return try {
            val ctxEmb = embed(context) ?: return candidates

            val scored = candidates.mapNotNull { word ->
                val wordEmb = embed(word) ?: return@mapNotNull null
                val score   = cosineSimilarity(ctxEmb, wordEmb)
                Timber.v("T5 '$word' score=${"%.4f".format(score)}")
                word to score
            }

            if (scored.isEmpty()) candidates
            else scored.sortedByDescending { it.second }.map { it.first }

        } catch (e: Exception) {
            Timber.e(e, "Rerank failed")
            candidates
        }
    }

    // ── Inferencia ────────────────────────────────────────────────────────────

    fun embed(text: String): FloatArray? {
        val tok = tokenizer ?: return null
        val tokenIds    = tok.encodeToArray(text, SEQ_LEN)
        val paddingMask = tok.paddingMask(tokenIds)
        return runInference(tokenIds, paddingMask)
    }

    private fun runInference(tokenIds: IntArray, paddingMask: IntArray): FloatArray? {
        val interp = interpreter ?: return null
        return synchronized(lock) {
            try {
                val inputMap: Map<String, Any> = mapOf(
                    IN_TOKEN_IDS to Array(1) { tokenIds },
                    IN_MASK      to Array(1) { paddingMask }
                )
                val outputBuf = Array(1) { FloatArray(HIDDEN_SIZE) }
                val outputMap: MutableMap<String, Any> = mutableMapOf(OUT_EMBEDDING to outputBuf)

                try {
                    interp.runSignature(inputMap, outputMap, SIGNATURE_KEY)
                } catch (_: Exception) {
                    interp.runForMultipleInputsOutputs(
                        arrayOf(Array(1) { tokenIds }, Array(1) { paddingMask }),
                        mapOf(0 to outputBuf)
                    )
                }

                outputBuf[0]
            } catch (e: Exception) {
                Timber.e(e, "T5 inference error")
                null
            }
        }
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = Math.sqrt(na * nb)
        return if (denom > 1e-9) (dot / denom).toFloat() else 0f
    }

    override fun close() {
        try { interpreter?.close() } catch (_: Exception) {}
        interpreter = null
        tokenizer   = null
        isAvailable = false
        Timber.d("T5 ReRanker closed")
    }
}
