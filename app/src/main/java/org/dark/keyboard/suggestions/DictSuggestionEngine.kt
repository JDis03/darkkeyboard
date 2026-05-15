package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log

/**
 * Motor de sugerencias multi-idioma.
 *
 * Soporta múltiples idiomas cargando el diccionario correspondiente.
 * Arquitectura: CompactTrie + Bigrams + UserFreq por idioma.
 */
class DictSuggestionEngine(private val context: Context) : SuggestionEngine {

    companion object {
        private const val TAG = "DictEngine"
        private const val MAX_BIGRAMS = 2000

        // Idiomas soportados: código → archivo de diccionario
        val SUPPORTED_LANGUAGES = linkedMapOf(
            "es" to "dict_es.txt",
            "en" to "dict_en.txt"
        )
        val LANGUAGE_NAMES = mapOf(
            "es" to "ES",
            "en" to "EN"
        )
    }

    override val engineName get() = "Dict ($currentLang)"

    private var currentLang = "es"
    private var trie = CompactTrie(context)

    // Bigrams y frecuencia por idioma
    private val bigramsByLang = mutableMapOf<String, LinkedHashMap<String, MutableMap<String, Int>>>()
    private val userFreqByLang = mutableMapOf<String, LinkedHashMap<String, Int>>()

    private var isReady = false

    override fun initialize() {
        loadLanguage(currentLang)
    }

    fun switchLanguage(lang: String) {
        if (lang == currentLang) return
        if (!SUPPORTED_LANGUAGES.containsKey(lang)) {
            Log.w(TAG, "Unsupported language: $lang")
            return
        }
        currentLang = lang
        loadLanguage(lang)
        Log.i(TAG, "Switched to language: $lang")
    }

    fun getCurrentLanguage() = currentLang

    fun nextLanguage(): String {
        val langs = SUPPORTED_LANGUAGES.keys.toList()
        val idx = langs.indexOf(currentLang)
        return langs[(idx + 1) % langs.size]
    }

    private fun loadLanguage(lang: String) {
        val file = SUPPORTED_LANGUAGES[lang] ?: return
        trie = CompactTrie(context, file)
        trie.load()
        isReady = trie.isReady()
        Log.i(TAG, "Loaded dict for '$lang': $file ready=$isReady")
    }

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (!isReady || textBeforeCursor.isBlank()) return emptyList()

        val endsWithSpace = textBeforeCursor.endsWith(" ")
        val words = textBeforeCursor.trimEnd().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val partialWord = if (endsWithSpace) "" else words.lastOrNull()?.lowercase() ?: ""
        val prevWord = when {
            endsWithSpace -> words.lastOrNull()?.lowercase()
            words.size >= 2 -> words[words.size - 2].lowercase()
            else -> null
        }

        val results = mutableListOf<Pair<String, Float>>()

        // Capa 1: completions del trie
        if (partialWord.isNotEmpty()) {
            trie.lookup(partialWord).forEach { entry ->
                results.add(entry.word to (entry.freq * 0.01f))
            }
        }

        // Capa 2: bigrams del idioma actual
        if (endsWithSpace && prevWord != null) {
            bigrams()[prevWord]?.forEach { (nextWord, freq) ->
                results.add(nextWord to (freq * 5.0f))
            }
        }

        // Capa 3: user frequency fallback
        if (partialWord.isNotEmpty() && results.isEmpty()) {
            userFreq().forEach { (word, freq) ->
                if (word.startsWith(partialWord)) {
                    results.add(word to (freq * 1.5f))
                }
            }
        }

        val seen = mutableSetOf<String>()
        return results
            .sortedByDescending { it.second }
            .mapNotNull { (word, _) ->
                val display = if (partialWord.isNotEmpty() && partialWord[0].isLowerCase()) {
                    word.lowercase()
                } else {
                    word
                }
                display.takeIf { it.length >= 2 && it != partialWord && seen.add(it) }
            }
            .take(maxResults)
    }

    override fun onSuggestionAccepted(suggestion: String, context: String) {
        val words = context.lowercase()
            .split(Regex("[\\s.,!?;:]+"))
            .filter { it.length >= 2 && it.all { c -> c.isLetter() } }

        for (i in 0 until words.size - 1) {
            val prev = words[i]
            val next = words[i + 1]
            bigrams().getOrPut(prev) {
                object : LinkedHashMap<String, Int>(64, 0.75f, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>) = size > 50
                }
            }.also { it[next] = (it[next] ?: 0) + 1 }
        }
        words.forEach { word ->
            userFreq()[word] = (userFreq()[word] ?: 0) + 1
        }
    }

    private fun bigrams() = bigramsByLang.getOrPut(currentLang) {
        object : LinkedHashMap<String, MutableMap<String, Int>>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MutableMap<String, Int>>) =
                size > MAX_BIGRAMS
        }
    }

    private fun userFreq() = userFreqByLang.getOrPut(currentLang) {
        object : LinkedHashMap<String, Int>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>) = size > 3000
        }
    }

    override fun close() { isReady = false }

    fun isReady() = isReady
}
