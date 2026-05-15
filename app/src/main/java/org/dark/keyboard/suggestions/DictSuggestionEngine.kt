package org.dark.keyboard.suggestions

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlin.math.ln
import kotlin.math.pow

/**
 * Motor de sugerencias multi-idioma con Frequency Overlay.
 *
 * Arquitectura (patrón GBoard/SwiftKey):
 *
 *   Fase 1 — Trie lookup: candidatos por prefijo (corpus freq)
 *   Fase 2 — Frequency Overlay: re-ranking por uso del usuario
 *     score_final = corpus_score * W_CORPUS
 *                 + user_freq_score * W_USER
 *                 + bigram_score * W_BIGRAM
 *
 *   Forgetting curve: frecuencias del usuario decaen con el tiempo
 *   (igual que HeliBoard/AOSP UserHistoryDictionary)
 *
 *   Persistencia: SharedPreferences por idioma
 */
class DictSuggestionEngine(private val context: Context) : SuggestionEngine {

    companion object {
        private const val TAG = "DictEngine"

        // Pesos del scoring — Fase 2
        private const val W_CORPUS = 1.0f    // peso del corpus estático
        private const val W_USER   = 8.0f    // peso del historial del usuario
        private const val W_BIGRAM = 15.0f   // peso de bigrams (contexto más fuerte)

        // Forgetting curve: cada 7 días las frecuencias se reducen un 20%
        private const val DECAY_DAYS    = 7L
        private const val DECAY_FACTOR  = 0.80f
        private const val MS_PER_DAY    = 86_400_000L

        private const val MAX_USER_WORDS  = 3000
        private const val MAX_BIGRAM_KEYS = 1000
        private const val MAX_BIGRAM_VALS = 30

        val SUPPORTED_LANGUAGES = linkedMapOf(
            "es" to "dict_es.txt",
            "en" to "dict_en.txt"
        )
        val LANGUAGE_NAMES = mapOf(
            "es" to "ES",
            "en" to "EN"
        )
    }

    override val engineName get() = "Dict+Overlay ($currentLang)"

    private var currentLang = "es"
    private var trie        = CompactTrie(context)
    private lateinit var prefs: SharedPreferences

    // user word frequency: lang → (word → score float con decay)
    private val userFreqByLang = mutableMapOf<String, MutableMap<String, Float>>()

    // bigrams: lang → (prevWord → (nextWord → score float))
    private val bigramsByLang = mutableMapOf<String, MutableMap<String, MutableMap<String, Float>>>()

    // timestamp de último decay por idioma
    private val lastDecayByLang = mutableMapOf<String, Long>()

    private var isReady = false

    override fun initialize() {
        prefs = context.getSharedPreferences("dict_engine_prefs", Context.MODE_PRIVATE)
        loadLanguage(currentLang)
        loadPersistedData(currentLang)
    }

    fun switchLanguage(lang: String) {
        if (!SUPPORTED_LANGUAGES.containsKey(lang)) return
        savePersistedData(currentLang)  // guardar antes de cambiar
        currentLang = lang
        loadLanguage(lang)
        loadPersistedData(lang)
        Log.i(TAG, "Switched to: $lang")
    }

    fun getCurrentLanguage() = currentLang

    fun nextLanguage(): String {
        val langs = SUPPORTED_LANGUAGES.keys.toList()
        return langs[(langs.indexOf(currentLang) + 1) % langs.size]
    }

    // ---- Sugerencias ----

    override fun getSuggestions(textBeforeCursor: String, maxResults: Int): List<String> {
        if (!isReady || textBeforeCursor.isBlank()) return emptyList()

        applyDecayIfNeeded(currentLang)

        val endsWithSpace = textBeforeCursor.endsWith(" ")
        val words     = textBeforeCursor.trimEnd().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val partial   = if (endsWithSpace) "" else words.lastOrNull()?.lowercase() ?: ""
        val prevWord  = when {
            endsWithSpace        -> words.lastOrNull()?.lowercase()
            words.size >= 2      -> words[words.size - 2].lowercase()
            else                 -> null
        }

        val scores = mutableMapOf<String, Float>()

        // === Fase 1: candidatos del trie (corpus) ===
        if (partial.isNotEmpty()) {
            trie.lookup(partial, maxResults = 12).forEach { entry ->
                // normalizar corpus freq (1-255) a 0.0-1.0
                val corpusScore = entry.freq / 255f
                scores[entry.word] = (scores[entry.word] ?: 0f) + corpusScore * W_CORPUS
            }
        }

        // === Fase 2: Frequency Overlay ===

        // 2a. Boost por user frequency (palabras que el usuario escribe seguido)
        val uf = userFreq()
        if (partial.isNotEmpty()) {
            uf.forEach { (word, score) ->
                if (word.startsWith(partial) && word != partial) {
                    // boost proporcional: palabras del usuario suben en el ranking
                    val boost = ln(1f + score) * W_USER
                    scores[word] = (scores[word] ?: 0f) + boost
                }
            }
        }

        // 2b. Bigram boost (predicción de siguiente palabra)
        if (prevWord != null) {
            bigrams()[prevWord]?.forEach { (nextWord, score) ->
                val bigramBoost = score * W_BIGRAM
                if (endsWithSpace || nextWord.startsWith(partial)) {
                    scores[nextWord] = (scores[nextWord] ?: 0f) + bigramBoost
                }
            }
        }

        // === Construir resultado final ===
        val seen = mutableSetOf<String>()
        return scores.entries
            .sortedByDescending { it.value }
            .mapNotNull { (word, _) ->
                val display = if (partial.isNotEmpty() && partial[0].isLowerCase()) {
                    word.lowercase()
                } else word
                display.takeIf { it.length >= 2 && it != partial && seen.add(it) }
            }
            .take(maxResults)
    }

    // ---- Aprendizaje ----

    override fun onSuggestionAccepted(suggestion: String, context: String) {
        learnFromText("$context $suggestion")
    }

    /**
     * Aprende de cualquier texto escrito por el usuario.
     * Llamar al completar una frase (Enter, sugerencia aceptada).
     */
    fun learnFromText(text: String) {
        val words = text.lowercase()
            .split(Regex("[\\s.,!?;:\"']+"))
            .filter { it.length >= 2 && it.all { c -> c.isLetter() } }

        val uf = userFreq()
        val bg = bigrams()

        words.forEach { word ->
            uf[word] = (uf[word] ?: 0f) + 1f
        }

        for (i in 0 until words.size - 1) {
            val prev = words[i]
            val next = words[i + 1]
            val bigramMap = bg.getOrPut(prev) { mutableMapOf() }
            bigramMap[next] = (bigramMap[next] ?: 0f) + 1f
            // Limitar bigrams por key
            if (bigramMap.size > MAX_BIGRAM_VALS) {
                val minKey = bigramMap.minByOrNull { it.value }?.key
                if (minKey != null) bigramMap.remove(minKey)
            }
        }

        // Limitar user freq size
        if (uf.size > MAX_USER_WORDS) {
            val minKey = uf.minByOrNull { it.value }?.key
            if (minKey != null) uf.remove(minKey)
        }

        // Limitar bigram keys size
        if (bg.size > MAX_BIGRAM_KEYS) {
            val minKey = bg.minByOrNull { it.value.values.sum() }?.key
            if (minKey != null) bg.remove(minKey)
        }
    }

    // ---- Forgetting curve ----

    /**
     * Aplica el decaimiento de frecuencias si han pasado DECAY_DAYS.
     * Las palabras usadas recientemente decaen menos que las antiguas.
     * Patrón idéntico a AOSP UserHistoryDictionary.
     */
    private fun applyDecayIfNeeded(lang: String) {
        val now = System.currentTimeMillis()
        val last = lastDecayByLang[lang] ?: now.also { lastDecayByLang[lang] = it }
        val daysPassed = (now - last) / MS_PER_DAY
        if (daysPassed < DECAY_DAYS) return

        val factor = DECAY_FACTOR.pow(daysPassed.toFloat() / DECAY_DAYS)
        Log.d(TAG, "Applying decay factor=$factor for $lang (${daysPassed}d passed)")

        userFreq().replaceAll { _, v -> v * factor }
        bigrams().values.forEach { bg -> bg.replaceAll { _, v -> v * factor } }

        // Eliminar entradas casi irrelevantes
        userFreq().entries.removeIf { it.value < 0.1f }
        bigrams().values.forEach { bg -> bg.entries.removeIf { it.value < 0.1f } }
        bigrams().entries.removeIf { it.value.isEmpty() }

        lastDecayByLang[lang] = now
        savePersistedData(lang)
    }

    // ---- Persistencia ----

    /**
     * Guarda historial del usuario en SharedPreferences.
     * Formato: "word:score,word:score,..."
     */
    fun savePersistedData(lang: String = currentLang) {
        val uf = userFreqByLang[lang] ?: return
        val bg = bigramsByLang[lang] ?: return

        val ufStr = uf.entries
            .sortedByDescending { it.value }
            .take(500)
            .joinToString(",") { "${it.key}:${it.value}" }

        val bgStr = bg.entries
            .take(200)
            .joinToString(";") { (prev, nexts) ->
                "$prev|" + nexts.entries.take(10).joinToString(",") { "${it.key}:${it.value}" }
            }

        prefs.edit()
            .putString("uf_$lang", ufStr)
            .putString("bg_$lang", bgStr)
            .putLong("decay_$lang", lastDecayByLang[lang] ?: System.currentTimeMillis())
            .apply()

        Log.d(TAG, "Saved: ${uf.size} words, ${bg.size} bigrams for $lang")
    }

    private fun loadPersistedData(lang: String) {
        val uf = userFreq()
        val bg = bigrams()

        // Cargar user freq
        prefs.getString("uf_$lang", "")?.split(",")?.forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val word  = parts[0]
                val score = parts[1].toFloatOrNull() ?: return@forEach
                if (word.isNotBlank() && score > 0f) uf[word] = score
            }
        }

        // Cargar bigrams
        prefs.getString("bg_$lang", "")?.split(";")?.forEach { entry ->
            if (entry.isBlank()) return@forEach
            val parts = entry.split("|")
            if (parts.size == 2) {
                val prev = parts[0]
                val bigramMap = bg.getOrPut(prev) { mutableMapOf() }
                parts[1].split(",").forEach { pair ->
                    val kv = pair.split(":")
                    if (kv.size == 2) {
                        val next  = kv[0]
                        val score = kv[1].toFloatOrNull() ?: return@forEach
                        if (next.isNotBlank() && score > 0f) bigramMap[next] = score
                    }
                }
            }
        }

        lastDecayByLang[lang] = prefs.getLong("decay_$lang", System.currentTimeMillis())
        Log.i(TAG, "Loaded: ${uf.size} words, ${bg.size} bigrams for $lang")
    }

    // ---- Helpers ----

    private fun loadLanguage(lang: String) {
        val file = SUPPORTED_LANGUAGES[lang] ?: return
        trie = CompactTrie(context, file)
        trie.load()
        isReady = trie.isReady()
    }

    private fun userFreq() = userFreqByLang.getOrPut(currentLang) { mutableMapOf() }
    private fun bigrams()  = bigramsByLang.getOrPut(currentLang)  { mutableMapOf() }

    override fun close() {
        savePersistedData(currentLang)
        isReady = false
    }

    fun isReady() = isReady
}
