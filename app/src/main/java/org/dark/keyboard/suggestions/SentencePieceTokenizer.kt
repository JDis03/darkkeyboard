package org.dark.keyboard.suggestions

import android.content.Context
import timber.log.Timber
import java.io.File

/**
 * Tokenizador SentencePiece para T5 en Android.
 *
 * Carga el archivo spiece.model (vocabulary de t5_small_multi, ~800KB)
 * y tokeniza texto usando el algoritmo Unigram LM implementado en
 * SentencePieceProcessor (puro Kotlin, sin dependencias nativas).
 *
 * Uso:
 *   val tok = SentencePieceTokenizer(context)
 *   tok.load()
 *   val ids = tok.encodeToArray("hola mundo", maxLen = 32)  // IntArray
 */
class SentencePieceTokenizer(private val context: Context) {

    companion object {
        const val SPIECE_FILE = "spiece.model"
        const val SEQ_LEN     = 32
        const val PAD_ID      = SentencePieceProcessor.PAD_ID   // 0
        const val EOS_ID      = SentencePieceProcessor.EOS_ID   // 1
        const val UNK_ID      = SentencePieceProcessor.UNK_ID   // 2
    }

    private val processor = SentencePieceProcessor()

    var isReady: Boolean = false
        private set

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun load() {
        val spFile = File(ModelDownloader.modelsDir(context), SPIECE_FILE)

        if (!spFile.exists()) {
            Timber.w("spiece.model not found: ${spFile.absolutePath}")
            Timber.w("AI re-ranking disabled until model is downloaded via Settings")
            isReady = false
            return
        }

        processor.load(spFile)

        if (processor.isLoaded) {
            Timber.i("SentencePiece ready: ${processor.vocabSize} pieces")
            isReady = true
        } else {
            Timber.e("SentencePiece failed to load")
            isReady = false
        }
    }

    // ── Tokenización ──────────────────────────────────────────────────────────

    /**
     * Tokeniza [text] a una lista de IDs.
     */
    fun encode(text: String): List<Int> {
        if (!isReady) return emptyList()
        return processor.encode(text)
    }

    /**
     * Tokeniza [text] a IntArray de longitud [maxLen] (pad con PAD_ID).
     * EOS no se añade explícitamente (T5 encoder no lo necesita).
     */
    fun encodeToArray(text: String, maxLen: Int = SEQ_LEN): IntArray {
        if (!isReady) return IntArray(maxLen) { PAD_ID }
        return processor.encodeToArray(text, maxLen)
    }

    /**
     * Máscara de padding: 1 donde hay tokens reales, 0 donde hay PAD.
     */
    fun paddingMask(tokenIds: IntArray): IntArray {
        return IntArray(tokenIds.size) { i -> if (tokenIds[i] != PAD_ID) 1 else 0 }
    }

    /**
     * Decodifica IDs a texto (útil para debug).
     */
    fun decode(ids: List<Int>): String {
        if (!isReady) return ""
        return processor.decode(ids)
    }

    /**
     * Info de debug.
     */
    fun debugInfo(): String = buildString {
        append("SentencePieceTokenizer[")
        append("ready=$isReady, ")
        append("vocab=${if (isReady) processor.vocabSize else 0}, ")
        append("file=${File(ModelDownloader.modelsDir(context), SPIECE_FILE).exists()}")
        append("]")
    }
}
