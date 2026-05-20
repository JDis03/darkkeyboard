package org.dark.keyboard.suggestions

import android.content.Context
import timber.log.Timber

/**
 * Motor de sugerencias híbrido — el patrón GBoard/SwiftKey.
 *
 * Estrategia de 3 capas:
 *   1. Trie lookup → completions por prefijo (rápido, O(k))
 *   2. Bigrams locales → siguiente palabra frecuente (aprende del usuario)
 *   3. Corrección ortográfica → si el trie no encuentra nada
 *
 * Sin JNI, sin TFLite, sin dependencias externas.
 * Diccionario: 8000 palabras más frecuentes del español (~154 KB).
 */
class SpanishDictEngine(private val context: Context) : SuggestionEngine {

    override val engineName = "Spanish Trie + Bigrams"

    companion object {

        private const val MAX_RESULTS = 3
        private const val MAX_BIGRAMS = 2000
    }

    private val trie = CompactTrie(context)

    // Bigrams aprendidos del usuario: "palabra" → (siguiente → frecuencia)
    private val bigrams = object : LinkedHashMap<String, MutableMap<String, Int>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableMap<String, Int>>) =
            size > MAX_BIGRAMS
    }

    // Frecuencia de palabras del usuario
    private val userFreq = object : LinkedHashMap<String, Int>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>) = size > 3000
    }

    private var isReady = false

    override fun initialize() {
        trie.load()
        isReady = trie.isReady()
        Timber.i("Engine ready: ${if (isReady) "OK" else "FAILED"}")
    }

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (!isReady || textBeforeCursor.isBlank()) return emptyList()

        val endsWithSpace = textBeforeCursor.endsWith(" ")
        val words = textBeforeCursor.trimEnd().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val partialWord = if (endsWithSpace) "" else words.lastOrNull()?.lowercase() ?: ""
        val prevWord = if (endsWithSpace) words.lastOrNull()?.lowercase() 
                       else if (words.size >= 2) words[words.size - 2].lowercase() 
                       else null

        val results = mutableListOf<Pair<String, Float>>()

        // === Capa 1: completions del trie ===
        if (partialWord.isNotEmpty()) {
            trie.lookup(partialWord).forEach { entry ->
                results.add(entry.word to (entry.freq * 0.01f))
            }
        }

        // === Capa 2: bigrams locales (siguiente palabra) ===
        if (endsWithSpace && prevWord != null) {
            bigrams[prevWord]?.forEach { (nextWord, freq) ->
                results.add(nextWord to (freq * 5.0f))
            }
        }

        // === Capa 3: corrección por user freq si trie no encontró nada ===
        if (partialWord.isNotEmpty() && results.isEmpty()) {
            userFreq.forEach { (word, freq) ->
                if (word.startsWith(partialWord)) {
                    results.add(word to (freq * 1.5f))
                }
            }
        }

        // Ordenar, normalizar case, deduplicar
        val seen = mutableSetOf<String>()
        return results
            .sortedByDescending { it.second }
            .mapNotNull { (word, _) ->
                val display = if (partialWord.isNotEmpty() && partialWord[0].isLowerCase()) {
                    word.lowercase()
                } else {
                    word.lowercase().replaceFirstChar { it.uppercase() }
                }
                display.takeIf { it.length >= 2 && it != partialWord && seen.add(it) }
            }
            .take(maxResults)
    }

    override fun onSuggestionAccepted(suggestion: String, context: String) {
        val words = context.lowercase()
            .split(Regex("[\\s.,!?;:]+"))
            .filter { it.length >= 2 && it.all { c -> c in 'a'..'z' || c in "áéíóúüñ" } }

        // Actualizar bigrams
        for (i in 0 until words.size - 1) {
            val prev = words[i]
            val next = words[i + 1]
            bigrams.getOrPut(prev) {
                object : LinkedHashMap<String, Int>(64, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>) = size > 50
                }
            }.also { it[next] = (it[next] ?: 0) + 1 }
        }

        // Actualizar userFreq
        words.forEach { word ->
            userFreq[word] = (userFreq[word] ?: 0) + 1
        }
    }

    override fun close() {
        isReady = false
    }

    fun isReady() = isReady
}
