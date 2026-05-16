package org.dark.keyboard.autocorrect

import android.content.Context
import android.util.Log

/**
 * Diccionario de palabras para autocorrección.
 *
 * Carga dict_es.txt / dict_en.txt (8000 palabras + frecuencia).
 *
 * Operaciones:
 *   getFreq(word)              → frecuencia en corpus (0 = no existe)
 *   findByPrefix(prefix)       → lista ordenada por freq
 *   findByEditDistance(word,n) → candidatos a distancia ≤ n
 */
class WordDictionary(private val context: Context) {

    companion object {
        private const val TAG = "WordDict"

        val DICT_FILES = mapOf(
            "es" to "dict_es.txt",
            "en" to "dict_en.txt"
        )

        // Máxima distancia de edición soportada en findByEditDistance
        const val MAX_EDIT_DISTANCE = 2
    }

    data class Entry(val word: String, val freq: Int)

    // Palabras ordenadas alfabéticamente (binary search)
    private val wordsByLang = mutableMapOf<String, List<Entry>>()
    // Índice rápido: word → freq
    private val freqIndexByLang = mutableMapOf<String, Map<String, Int>>()

    private var currentLang = "es"
    var isReady = false
        private set

    fun load(lang: String = "es") {
        currentLang = lang
        if (wordsByLang.containsKey(lang)) {
            isReady = true
            return
        }
        val file = DICT_FILES[lang] ?: return
        try {
            val entries = mutableListOf<Entry>()
            context.assets.open(file).bufferedReader().forEachLine { line ->
                val parts = line.trim().split(" ")
                if (parts.size == 2) {
                    val word = parts[0].lowercase()
                    val freq = parts[1].toIntOrNull() ?: 0
                    if (word.isNotBlank() && word.all { it.isLetter() }) {
                        entries.add(Entry(word, freq))
                    }
                }
            }
            // Ordenar alfabéticamente para binary search
            entries.sortBy { it.word }
            wordsByLang[lang] = entries
            freqIndexByLang[lang] = entries.associate { it.word to it.freq }
            isReady = true
            Log.i(TAG, "Loaded ${entries.size} words for '$lang'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $file: ${e.message}")
            isReady = false
        }
    }

    fun switchLang(lang: String) {
        if (lang == currentLang && isReady) return
        currentLang = lang
        load(lang)
    }

    /** Frecuencia de una palabra en el corpus. 0 = no existe. */
    fun getFreq(word: String): Int =
        freqIndexByLang[currentLang]?.get(word.lowercase()) ?: 0

    /** True si la palabra existe en el diccionario. */
    fun contains(word: String): Boolean = getFreq(word) > 0

    /**
     * Palabras que empiezan con [prefix], ordenadas por frecuencia desc.
     * Usa binary search → O(log n + k).
     */
    fun findByPrefix(prefix: String, maxResults: Int = 6): List<Entry> {
        val words = wordsByLang[currentLang] ?: return emptyList()
        val p = prefix.lowercase()

        // Binary search del primer índice que empieza con prefix
        var lo = 0; var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].word < p) lo = mid + 1 else hi = mid
        }

        val results = mutableListOf<Entry>()
        var i = lo
        while (i < words.size && words[i].word.startsWith(p)) {
            results.add(words[i++])
        }
        return results.sortedByDescending { it.freq }.take(maxResults)
    }

    /**
     * Candidatos a corrección de [typed] con distancia de edición ≤ [maxDist].
     *
     * Early-exit: si |typed| - |word| > maxDist → imposible, skip.
     * ~8000 Levenshtein en ~2ms (Cortex-A55), ~8ms (Cortex-A53 viejo).
     * Llamar siempre desde un hilo de background.
     */
    fun findByEditDistance(typed: String, maxDist: Int = MAX_EDIT_DISTANCE): List<Entry> {
        val words = wordsByLang[currentLang] ?: return emptyList()
        val t = typed.lowercase()
        val results = mutableListOf<Entry>()

        for (entry in words) {
            val w = entry.word
            // Early exit por diferencia de longitud
            if (kotlin.math.abs(w.length - t.length) > maxDist) continue
            // Skip la misma palabra
            if (w == t) continue
            val d = levenshtein(t, w)
            if (d in 1..maxDist) {
                // Score: penalizar distancia mayor
                results.add(Entry(w, entry.freq - d * 5000))
            }
        }
        return results.sortedByDescending { it.freq }
    }

    // ---- Levenshtein ----
    // Reutiliza arrays para no allocar en cada llamada
    private val dp1 = IntArray(32)
    private val dp2 = IntArray(32)

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        // Asegurar espacio suficiente (palabras hasta 31 chars)
        val prev = if (n < dp1.size) dp1 else IntArray(n + 1)
        val curr = if (n < dp2.size) dp2 else IntArray(n + 1)

        for (j in 0..n) prev[j] = j
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1]
                else 1 + minOf(prev[j], curr[j - 1], prev[j - 1])
            }
            for (j in 0..n) prev[j] = curr[j]
        }
        return prev[n]
    }
}
