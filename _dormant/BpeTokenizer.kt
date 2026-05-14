package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Tokenizador BPE (Byte Pair Encoding) para GPT-2.
 *
 * Implementación fiel al tokenizador original de OpenAI/HuggingFace.
 * Carga vocab.json y merges.txt desde assets.
 *
 * El tokenizador GPT-2 usa un mapeo especial de bytes → unicode chars
 * donde cada byte 0-255 se representa con un char unicode específico.
 */
class BpeTokenizer(private val context: Context) {

    companion object {
        private const val TAG = "BpeTokenizer"
        private const val VOCAB_FILE  = "bpe_vocab.json"
        private const val MERGES_FILE = "bpe_merges.txt"
    }

    private val encoder = mutableMapOf<String, Int>()     // token_str → id
    private val decoder = mutableMapOf<Int, String>()     // id → token_str
    private val bpeRanks = mutableMapOf<Pair<String, String>, Int>()  // merge → rank
    private val byteEncoder = buildByteEncoder()          // byte → char
    private val byteDecoder: Map<Char, Int> = byteEncoder.entries.associate { (k, v) -> v[0] to k }
    // LRU cache con máximo 1024 entradas (evita unbounded memory growth)
    private val cache = object : LinkedHashMap<String, List<String>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>) = size > 1024
    }

    private var isLoaded = false

    fun load() {
        try {
            loadVocab()
            loadMerges()
            isLoaded = true
            Log.i(TAG, "BPE tokenizer loaded: ${encoder.size} tokens, ${bpeRanks.size} merges")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load BPE tokenizer: ${e.message}")
        }
    }

    fun isReady() = isLoaded

    /**
     * Convierte texto a lista de token IDs.
     */
    fun encode(text: String): List<Int> {
        if (!isLoaded) return emptyList()
        val tokens = mutableListOf<Int>()
        // GPT-2 tokeniza por palabras con espacio prefijo
        val words = splitToWords(text)
        for (word in words) {
            val bpeTokens = bpe(word)
            for (token in bpeTokens) {
                val id = encoder[token]
                if (id != null) tokens.add(id)
            }
        }
        return tokens
    }

    /**
     * Convierte un token ID a string.
     */
    fun decode(id: Int): String {
        val token = decoder[id] ?: return ""
        return try {
            val bytes = ByteArray(token.length) { i -> (byteDecoder[token[i]] ?: token[i].code and 0xFF).toByte() }
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            token
        }
    }

    /**
     * Divide texto en palabras con prefijo de espacio (estilo GPT-2).
     * "hola como" → ["hola", " como"]
     */
    private fun splitToWords(text: String): List<String> {
        val words = mutableListOf<String>()
        val pattern = Regex("'s|'t|'re|'ve|'m|'ll|'d| ?[a-záéíóúüñA-ZÁÉÍÓÚÜÑ]+| ?[0-9]+| ?[^\\s\\w]+|\\s+")
        for (match in pattern.findAll(text)) {
            words.add(match.value)
        }
        return words
    }

    /**
     * Aplica BPE a una palabra.
     * Retorna lista de subword tokens.
     */
    private fun bpe(word: String): List<String> {
        cache[word]?.let { return it }

        // Convertir cada char del word a su representación GPT-2
        var chars = word.map { c ->
            val bytes = c.toString().toByteArray(StandardCharsets.UTF_8)
            bytes.map { byteEncoder[it.toInt() and 0xFF] ?: c.toString() }.joinToString("")
        }.toMutableList()

        if (chars.size <= 1) {
            cache[word] = chars
            return chars
        }

        while (true) {
            // Encontrar el par con menor rank
            var bestPair: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE

            for (i in 0 until chars.size - 1) {
                val pair = Pair(chars[i], chars[i + 1])
                val rank = bpeRanks[pair] ?: Int.MAX_VALUE
                if (rank < bestRank) {
                    bestRank = rank
                    bestPair = pair
                }
            }

            if (bestPair == null || bestRank == Int.MAX_VALUE) break

            // Fusionar el mejor par
            val (first, second) = bestPair
            val merged = mutableListOf<String>()
            var i = 0
            while (i < chars.size) {
                if (i < chars.size - 1 && chars[i] == first && chars[i + 1] == second) {
                    merged.add(first + second)
                    i += 2
                } else {
                    merged.add(chars[i])
                    i++
                }
            }
            chars = merged
            if (chars.size == 1) break
        }

        cache[word] = chars
        return chars
    }

    private fun loadVocab() {
        val json = context.assets.open(VOCAB_FILE).bufferedReader().readText()
        val obj = JSONObject(json)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = obj.getInt(key)
            encoder[key] = id
            decoder[id] = key
        }
    }

    private fun loadMerges() {
        context.assets.open(MERGES_FILE).use { stream ->
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                var rank = 0
                reader.forEachLine { line ->
                    if (!line.startsWith("#") && line.isNotBlank()) {
                        val parts = line.trim().split(" ")
                        if (parts.size == 2) {
                            bpeRanks[Pair(parts[0], parts[1])] = rank++
                        }
                    }
                }
            }
        }
    }

    /**
     * Mapeo de bytes GPT-2: cada byte 0-255 → char unicode específico.
     * Bytes imprimibles (33-126, 161-172, 174-255) se mapean a sí mismos.
     * Bytes de control se mapean a chars en rango 256+.
     */
    private fun buildByteEncoder(): Map<Int, String> {
        val bs = mutableListOf<Int>()
        bs.addAll(('!' .code)..('~' .code))
        bs.addAll(('¡'.code)..('¬'.code))
        bs.addAll(('®'.code)..('ÿ'.code))

        val cs = bs.map { it }.toMutableList()
        var n = 0
        for (b in 0..255) {
            if (b !in bs) {
                bs.add(b)
                cs.add(256 + n)
                n++
            }
        }
        return bs.zip(cs.map { it.toChar().toString() }).toMap()
    }
}
