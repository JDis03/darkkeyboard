package org.dark.keyboard.suggestions

import android.content.Context
import timber.log.Timber
import java.io.InputStream

/**
 * Diccionario por binary search sobre array ordenado en memoria.
 *
 * Más simple y robusto que el trie binario corrupto.
 * 10000 palabras → ~14 búsquedas binarias por lookup.
 * Cada lookup por prefijo devuelve hasta 6 palabras completas.
 */
class CompactTrie(private val context: Context, private val file: String = "dict_es.txt") {

    companion object {

        private const val MAX_RESULTS = 6
    }

    data class Entry(val word: String, val freq: Int)

    // Array de palabras ordenadas alfabéticamente + frecuencias (para binary search)
    private val words = mutableListOf<Entry>()
    // HashMap para lookup exacto O(1) — el array se carga en orden de frecuencia, no alfabético
    private val freqMap = HashMap<String, Int>(12000)
    private var isLoaded = false

    fun load() {
        try {
            val stream: InputStream = context.assets.open(file)
            val reader = java.io.BufferedReader(stream.reader(java.nio.charset.StandardCharsets.UTF_8))
            reader.forEachLine { line ->
                val parts = line.trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    val word = parts[0]
                    val freq = parts[1].toIntOrNull() ?: 0
                    if (word.length >= 2) {
                        words.add(Entry(word, freq))
                        freqMap[word] = freq
                    }
                }
            }
            reader.close()
            // Ordenar alfabéticamente para que binary search funcione en lookup() y findByEditDistance()
            words.sortBy { it.word }
            isLoaded = true
            Timber.i("Loaded: ${words.size} words (sorted alphabetically, binary search)")
        } catch (e: Exception) {
            Timber.e("Failed to load dictionary: ${e.message}")
        }
    }

    fun isReady() = isLoaded

    /**
     * Obtiene la frecuencia de una palabra exacta.
     * @return frecuencia (0 si no existe en el diccionario)
     */
    fun getFreq(word: String): Int {
        if (!isLoaded || word.isEmpty()) return 0
        // HashMap O(1) — más confiable que binary search sobre array cargado por frecuencia
        return freqMap[word.lowercase()] ?: 0
    }

    /**
     * Busca todas las palabras que empiezan con [prefix].
     */
    fun lookup(prefix: String, maxResults: Int = MAX_RESULTS): List<Entry> {
        if (!isLoaded || prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()

        var lo = 0; var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (words[mid].word < p) lo = mid + 1 else hi = mid
        }

        val results = mutableListOf<Entry>()
        var i = lo
        while (i < words.size && words[i].word.startsWith(p)) {
            val w = words[i]
            if (w.word.length >= 3 && w.word != p) results.add(w)
            i++
        }
        results.sortByDescending { it.freq }
        return results.take(maxResults)
    }

    /**
     * Busca palabras similares a [typed] por edit distance (Damerau-Levenshtein).
     *
     * Para cada keyword, esto itera sobre las ~8000 palabras del diccionario
     * y calcula la distancia. Toma ~1-4ms.
     *
     * Filtra por:
     *   - maxDist (1-2 típicamente)
     *   - Primera letra compatible (misma o adyacente QWERTY)
     *   - Longitud similar (±2 chars)
     *
     * @return top-N candidatos ordenados por frecuencia descendente.
     */
    fun findByEditDistance(
        typed: String,
        maxDist: Int = 2,
        maxResults: Int = 12
    ): List<Entry> {
        if (!isLoaded || typed.length < 3) return emptyList()
        val t = typed.lowercase()

        val results = mutableListOf<Entry>()
        val window = maxOf(3, t.length - 2)..(t.length + 2)

        for (entry in words) {
            if (entry.word.length !in window) continue
            if (entry.word == t) continue
            if (!firstLetterCompat(t, entry.word)) continue

            val d = damerauLevenshtein(t, entry.word)
            if (d <= maxDist) results.add(entry)
        }

        results.sortByDescending { it.freq }
        return results.take(maxResults)
    }

    private fun firstLetterCompat(typed: String, correction: String): Boolean {
        val t = typed[0]; val c = correction[0]
        if (t == c) return true
        val adjacent = mapOf(
            'q' to "wa", 'w' to "qe", 'e' to "wr", 'r' to "et",
            't' to "ry", 'y' to "tu", 'u' to "yi", 'i' to "uo",
            'o' to "ip", 'p' to "o", 'a' to "qs", 's' to "aw",
            'd' to "se", 'f' to "dr", 'g' to "ft", 'h' to "gy",
            'j' to "hu", 'k' to "ji", 'l' to "ko", 'z' to "as",
            'x' to "zs", 'c' to "xd", 'v' to "cf", 'b' to "vg",
            'n' to "bh", 'm' to "nj"
        )
        return adjacent[t]?.contains(c) == true
    }

    /**
     * Damerau-Levenshtein: insert, delete, substitute, transpose = 1
     */
    private fun damerauLevenshtein(a: String, b: String): Int {
        val n = a.length; val m = b.length
        var prevRow = IntArray(m + 1) { it }
        var curRow  = IntArray(m + 1)
        var prevPrevRow = IntArray(m + 1)

        for (i in 1..n) {
            curRow[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curRow[j] = minOf(
                    prevRow[j] + 1,          // delete
                    curRow[j - 1] + 1,        // insert
                    prevRow[j - 1] + cost     // substitute
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    curRow[j] = minOf(curRow[j], prevPrevRow[j - 2] + cost)
                }
            }
            prevPrevRow = prevRow; prevRow = curRow
            curRow = IntArray(m + 1)
        }
        return prevRow[m]
    }
}
