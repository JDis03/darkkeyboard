package org.dark.keyboard.suggestions

import timber.log.Timber
import java.io.File
import kotlin.math.exp
import kotlin.math.ln

/**
 * Tokenizador SentencePiece en Kotlin puro.
 *
 * Lee el archivo spiece.model (protobuf binario) sin librerías externas.
 * Implementa el algoritmo Unigram LM de SentencePiece para tokenizar texto.
 *
 * Compatible con el vocabulario de t5_small_multi (32,000 tokens).
 *
 * Referencia:
 *   Kudo, T. (2018). Subword Regularization: Improving Neural Network Translation
 *   Models with Multiple Subword Candidates. ACL 2018.
 */
class SentencePieceProcessor {

    // ── Vocabulario ───────────────────────────────────────────────────────────

    data class Piece(
        val piece: String,
        val score: Float,
        val type: Int         // 1=NORMAL, 2=UNKNOWN, 3=CONTROL, 4=USER_DEFINED, 6=BYTE
    )

    private val pieces = mutableListOf<Piece>()
    private val pieceToId = HashMap<String, Int>(40_000)

    // Tokens especiales T5
    private var unkId    = 2
    private var bosId    = -1   // T5 no tiene BOS explícito
    private var eosId    = 1
    private var padId    = 0

    var isLoaded = false
        private set

    val vocabSize: Int get() = pieces.size

    // ── Carga ─────────────────────────────────────────────────────────────────

    fun load(file: File) {
        if (!file.exists()) {
            Timber.w("spiece.model not found: ${file.absolutePath}")
            return
        }
        try {
            val bytes = file.readBytes()
            parseModelProto(bytes)
            buildIndex()
            isLoaded = true
            Timber.i("SentencePiece loaded: ${pieces.size} pieces from ${file.name}")
        } catch (e: Exception) {
            Timber.e(e, "SentencePiece load failed")
        }
    }

    // ── Tokenización ──────────────────────────────────────────────────────────

    /**
     * Tokeniza [text] y retorna una lista de IDs.
     * Normaliza a minúsculas y aplica el algoritmo Unigram LM (Viterbi).
     */
    fun encode(text: String): List<Int> {
        if (!isLoaded || text.isEmpty()) return emptyList()

        val normalized = normalize(text)
        if (normalized.isEmpty()) return emptyList()

        return unigramSegment(normalized).map { piece ->
            pieceToId[piece] ?: unkId
        }
    }

    /**
     * Tokeniza a Array<Int> de tamaño [maxLen] (pad con 0).
     */
    fun encodeToArray(text: String, maxLen: Int): IntArray {
        val ids  = encode(text)
        val out  = IntArray(maxLen) { padId }
        ids.forEachIndexed { i, id -> if (i < maxLen) out[i] = id }
        return out
    }

    /**
     * Decodifica una lista de IDs a texto.
     */
    fun decode(ids: List<Int>): String {
        return ids.mapNotNull { id ->
            pieces.getOrNull(id)?.piece?.let { p ->
                when {
                    p == "<unk>"          -> "⁇"
                    p.startsWith("\u2581") -> " " + p.drop(1)   // ▁ = espacio
                    p.startsWith("<0x")   -> byteToken(p)        // Byte tokens
                    else                   -> p
                }
            }
        }.joinToString("").trim()
    }

    // ── Algoritmo Unigram (Viterbi) ───────────────────────────────────────────

    private fun unigramSegment(text: String): List<String> {
        val chars = text.toList()
        val n = chars.size

        // dp[i] = (mejor score log acumulado hasta posición i, posición anterior)
        val score  = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val backp  = IntArray(n + 1) { -1 }
        val pieceAt = arrayOfNulls<String>(n + 1)

        score[0] = 0f

        for (i in 0 until n) {
            if (score[i] == Float.NEGATIVE_INFINITY) continue

            // Probar todos los prefijos desde posición i
            for (j in i + 1..minOf(i + MAX_PIECE_LEN, n)) {
                val sub = chars.subList(i, j).joinToString("")
                val pieceStr = if (i == 0) "\u2581$sub" else sub
                val id = pieceToId[pieceStr] ?: pieceToId[sub]
                if (id != null) {
                    val pieceScore = pieces[id].score
                    val newScore = score[i] + pieceScore
                    if (newScore > score[j]) {
                        score[j]   = newScore
                        backp[j]   = i
                        pieceAt[j] = pieces[id].piece
                    }
                }
            }

            // Fallback: byte token o UNK para este carácter
            if (score[i + 1] == Float.NEGATIVE_INFINITY) {
                val c = chars[i]
                val byteKey = "<0x%02X>".format(c.code and 0xFF)
                val fallback = pieceToId[byteKey]?.let { pieces[it].piece }
                    ?: "\u2581${c}"   // Último recurso

                score[i + 1] = score[i] + (pieces[unkId].score)
                backp[i + 1] = i
                pieceAt[i + 1] = fallback
            }
        }

        // Reconstruir la segmentación desde el final
        val result = mutableListOf<String>()
        var pos = n
        while (pos > 0) {
            result.add(pieceAt[pos] ?: "<unk>")
            pos = backp[pos]
        }
        result.reverse()
        return result
    }

    // ── Normalización ─────────────────────────────────────────────────────────

    private fun normalize(text: String): String {
        return buildString {
            var prevWasSpace = true
            for (c in text.lowercase()) {
                when {
                    c == ' ' || c == '\t' || c == '\n' -> {
                        if (!prevWasSpace) {
                            append(' ')
                            prevWasSpace = true
                        }
                    }
                    else -> {
                        append(c)
                        prevWasSpace = false
                    }
                }
            }
        }.trim()
    }

    private fun byteToken(piece: String): String {
        // <0xXX> → byte
        return try {
            val hex = piece.removePrefix("<0x").removeSuffix(">").toInt(16)
            hex.toChar().toString()
        } catch (_: Exception) { "" }
    }

    // ── Parser protobuf binario ───────────────────────────────────────────────

    /**
     * Parsea el ModelProto de SentencePiece.
     *
     * Estructura simplificada:
     *   ModelProto {
     *     repeated SentencePiece pieces = 1;
     *     TrainerSpec trainer_spec = 2;
     *     NormalizerSpec normalizer_spec = 3;
     *   }
     *   SentencePiece {
     *     string piece = 1;
     *     float  score = 2;
     *     uint32 type  = 3;
     *   }
     */
    private fun parseModelProto(data: ByteArray) {
        var pos = 0

        while (pos < data.size) {
            val (tag, wireType, newPos) = readTag(data, pos)
            pos = newPos

            when {
                tag == 1 && wireType == 2 -> {
                    // SentencePiece piece (repeated)
                    val (len, p2) = readVarint(data, pos)
                    pos = p2
                    val pieceBytes = data.copyOfRange(pos, pos + len.toInt())
                    pos += len.toInt()
                    parsePiece(pieceBytes)
                }
                wireType == 2 -> {
                    // Skip other length-delimited fields
                    val (len, p2) = readVarint(data, pos)
                    pos = p2 + len.toInt()
                }
                wireType == 0 -> {
                    // Skip varint
                    val (_, p2) = readVarint(data, pos)
                    pos = p2
                }
                wireType == 5 -> {
                    // Skip 32-bit
                    pos += 4
                }
                wireType == 1 -> {
                    // Skip 64-bit
                    pos += 8
                }
                else -> break  // Unknown wire type → stop
            }
        }
    }

    private fun parsePiece(data: ByteArray) {
        var pos = 0
        var piece = ""
        var score = 0f
        var type  = 1

        while (pos < data.size) {
            val (tag, wireType, newPos) = readTag(data, pos)
            pos = newPos

            when {
                tag == 1 && wireType == 2 -> {
                    val (len, p2) = readVarint(data, pos)
                    pos = p2
                    piece = String(data, pos, len.toInt(), Charsets.UTF_8)
                    pos += len.toInt()
                }
                tag == 2 && wireType == 5 -> {
                    score = java.lang.Float.intBitsToFloat(
                        (data[pos].toInt() and 0xFF) or
                        ((data[pos+1].toInt() and 0xFF) shl 8) or
                        ((data[pos+2].toInt() and 0xFF) shl 16) or
                        ((data[pos+3].toInt() and 0xFF) shl 24)
                    )
                    pos += 4
                }
                tag == 3 && wireType == 0 -> {
                    val (v, p2) = readVarint(data, pos)
                    type = v.toInt()
                    pos = p2
                }
                wireType == 2 -> {
                    val (len, p2) = readVarint(data, pos)
                    pos = p2 + len.toInt()
                }
                wireType == 0 -> {
                    val (_, p2) = readVarint(data, pos)
                    pos = p2
                }
                wireType == 5 -> pos += 4
                wireType == 1 -> pos += 8
                else -> break
            }
        }

        pieces.add(Piece(piece, score, type))
    }

    private fun buildIndex() {
        pieces.forEachIndexed { id, p ->
            pieceToId[p.piece] = id
            when (p.piece) {
                "<pad>"  -> padId = id
                "<unk>"  -> unkId = id
                "</s>"   -> eosId = id
            }
        }

        // Calcular la longitud máxima de un piece para la ventana del Viterbi
        updateMaxPieceLen()
    }

    private fun updateMaxPieceLen() {
        var max = 0
        for (p in pieces) {
            val len = p.piece
                .removePrefix("\u2581")   // quitar ▁
                .length
            if (len > max) max = len
        }
        MAX_PIECE_LEN = max.coerceAtMost(32)  // límite razonable
    }

    // ── Protobuf primitivas ───────────────────────────────────────────────────

    private fun readTag(data: ByteArray, pos: Int): Triple<Int, Int, Int> {
        val (v, newPos) = readVarint(data, pos)
        return Triple((v shr 3).toInt(), (v and 7L).toInt(), newPos)
    }

    private fun readVarint(data: ByteArray, startPos: Int): Pair<Long, Int> {
        var pos = startPos
        var result = 0L
        var shift  = 0
        while (pos < data.size) {
            val b = data[pos++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            shift += 7
            if (b and 0x80 == 0) break
        }
        return result to pos
    }

    companion object {
        private var MAX_PIECE_LEN = 16

        // IDs especiales T5
        const val PAD_ID = 0
        const val EOS_ID = 1
        const val UNK_ID = 2
    }
}
