package org.dark.keyboard.autocorrect

import android.content.Context
import timber.log.Timber

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
            Timber.i("Loaded ${entries.size} words for '$lang'")
        } catch (e: Exception) {
            Timber.e("Failed to load $file: ${e.message}")
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

    // ---- Damerau-Levenshtein ----
    // Soporta transposiciones: "teh"→"the" = dist 1 (no 2 como Levenshtein puro)
    // Operaciones: inserción, eliminación, sustitución, transposición adyacente

    /**
     * Damerau-Levenshtein con transposiciones adyacentes.
     * Más preciso que Levenshtein para errores de teclado comunes.
     *
     * Ejemplos:
     *   levenshtein("teh", "the") = 2 (sustituir e→h, h→e)
     *   damerauLevenshtein("teh", "the") = 1 (transponer eh→he)
     */
    internal fun levenshtein(a: String, b: String): Int {
        return damerauLevenshtein(a, b)
    }

    private fun damerauLevenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        // Matriz completa necesaria para transposiciones (no se puede optimizar a 2 filas)
        val d = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1

                d[i][j] = minOf(
                    d[i - 1][j] + 1,      // eliminación
                    d[i][j - 1] + 1,      // inserción
                    d[i - 1][j - 1] + cost // sustitución
                )

                // Transposición adyacente
                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + cost)
                }
            }
        }
        return d[m][n]
    }
}
