package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import java.io.InputStream

/**
 * Diccionario por binary search sobre array ordenado en memoria.
 *
 * Más simple y robusto que el trie binario corrupto.
 * 10000 palabras → ~14 búsquedas binarias por lookup.
 * Cada lookup por prefijo devuelve hasta 6 palabras completas.
 */
class CompactTrie(private val context: Context) {

    companion object {
        private const val TAG  = "CompactTrie"
        private const val FILE = "dict_es.txt"
        private const val MAX_RESULTS = 6
    }

    data class Entry(val word: String, val freq: Int)

    // Array de palabras ordenadas alfabéticamente + frecuencias
    private val words = mutableListOf<Entry>()
    private var isLoaded = false

    fun load() {
        try {
            val stream: InputStream = context.assets.open(FILE)
            val reader = java.io.BufferedReader(stream.reader(java.nio.charset.StandardCharsets.UTF_8))
            reader.forEachLine { line ->
                val parts = line.trim().split(" ", limit = 2)
                if (parts.size == 2) {
                    val word = parts[0]
                    val freq = parts[1].toIntOrNull() ?: 0
                    if (word.length >= 2) words.add(Entry(word, freq))
                }
            }
            reader.close()
            isLoaded = true
            Log.i(TAG, "Loaded: ${words.size} words (sorted text, binary search)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary: ${e.message}")
        }
    }

    fun isReady() = isLoaded

    /**
     * Busca todas las palabras que empiezan con [prefix].
     * Binary search para encontrar el rango, luego colectar dentro de ese rango.
     * Ordenado por frecuencia (mayor primero).
     */
    fun lookup(prefix: String, maxResults: Int = MAX_RESULTS): List<Entry> {
        if (!isLoaded || prefix.isEmpty()) return emptyList()
        val p = prefix.lowercase()

        // Binary search: encontrar primer match
        var lo = 0
        var hi = words.size
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (words[mid].word < p) lo = mid + 1
            else hi = mid
        }

        // Colectar todas las palabras que empiezan con el prefijo
        val results = mutableListOf<Entry>()
        var i = lo
        while (i < words.size && words[i].word.startsWith(p)) {
            val w = words[i]
            if (w.word.length >= 3 && w.word != p) {
                results.add(w)
            }
            i++
        }

        // Ordenar por frecuencia descendente
        results.sortByDescending { it.freq }
        return results.take(maxResults)
    }
}
