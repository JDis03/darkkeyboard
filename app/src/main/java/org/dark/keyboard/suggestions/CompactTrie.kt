package org.dark.keyboard.suggestions

import android.content.Context
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Diccionario compacto basado en Trie.
 *
 * Arquitectura estilo GBoard/SwiftKey:
 *   Trie serializado en binario → lookup O(k) por prefijo → top-N por frecuencia
 *
 * Formato del archivo (trie_es.bin):
 *   [4B] Magic = 0x444B4553
 *   [4B] Word count
 *   [nodes...] Cada nodo:
 *     [1B] flags (bit 0 = isTerminal)
 *     [1B] frequency (0-255)
 *     [1B] num_children
 *     [num_children ×]:
 *       [2B] char (UTF-16 code unit)
 *       [3B] child offset (little-endian)
 *
 * 8000 palabras, 19742 nodos, ~154 KB en assets.
 */
class CompactTrie(private val context: Context) {

    companion object {
        private const val TAG  = "CompactTrie"
        private const val FILE = "trie_es.bin"
        private const val MAGIC = 0x444B4553
        private const val MAX_RESULTS = 6
    }

    data class Entry(val word: String, val score: Int)

    private lateinit var buf: ByteBuffer
    private var isLoaded = false

    fun load() {
        try {
            val stream: InputStream = context.assets.open(FILE)
            val data = stream.readBytes()
            stream.close()
            buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            val magic = buf.getInt(0)
            if (magic != MAGIC) {
                Log.e(TAG, "Invalid magic: ${magic.toString(16)}")
                return
            }
            val wordCount = buf.getInt(4)
            isLoaded = true
            Log.i(TAG, "Trie loaded: $wordCount words, ${data.size / 1024} KB")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load trie: ${e.message}")
        }
    }

    fun isReady() = isLoaded

    /**
     * Busca completions para un prefijo. Retorna top-N por frecuencia.
     * Ej: lookup("hol") → ["hola", "hollín", "holgar", ...]
     */
    fun lookup(prefix: String, maxResults: Int = MAX_RESULTS): List<Entry> {
        if (!isLoaded || prefix.isEmpty()) return emptyList()

        val results = mutableListOf<Entry>()

        // Navegar al nodo del prefijo
        var offset = 8  // skip header (4B magic + 4B count)
        for (ch in prefix) {
            offset = findChild(offset, ch)
            if (offset < 0) return emptyList()  // prefijo no existe
        }

        // Colectar todas las palabras desde este nodo
        collectWords(offset, prefix, results)
        // Ordenar por score descendente (frecuencia)
        results.sortByDescending { it.score }

        return results.take(maxResults)
    }

    /**
     * Colecta todas las palabras terminales desde un nodo del trie.
     */
    private fun collectWords(nodeOffset: Int, currentWord: String, results: MutableList<Entry>) {
        val flags = buf.get(nodeOffset).toInt() and 0xFF
        val freq  = buf.get(nodeOffset + 1).toInt() and 0xFF

        if ((flags and 1) != 0) {
            results.add(Entry(currentWord, freq))
        }

        val numChildren = buf.get(nodeOffset + 2).toInt() and 0xFF
        var childPtr = nodeOffset + 3

        for (i in 0 until numChildren) {
            val charCode = buf.getShort(childPtr).toInt() and 0xFFFF
            val childOffset = readUInt24(childPtr + 2)
            val ch = charCode.toChar()
            collectWords(childOffset, currentWord + ch, results)
            childPtr += 5
        }
    }

    /**
     * Busca un hijo por carácter en un nodo.
     * Retorna el offset del hijo o -1 si no existe.
     */
    private fun findChild(nodeOffset: Int, target: Char): Int {
        val numChildren = buf.get(nodeOffset + 2).toInt() and 0xFF
        var ptr = nodeOffset + 3

        for (i in 0 until numChildren) {
            val charCode = buf.getShort(ptr).toInt() and 0xFFFF
            if (charCode == target.code) {
                return readUInt24(ptr + 2)
            }
            ptr += 5
        }
        return -1
    }

    private fun readUInt24(offset: Int): Int {
        val b0 = buf.get(offset).toInt() and 0xFF
        val b1 = buf.get(offset + 1).toInt() and 0xFF
        val b2 = buf.get(offset + 2).toInt() and 0xFF
        return b0 or (b1 shl 8) or (b2 shl 16)
    }
}
