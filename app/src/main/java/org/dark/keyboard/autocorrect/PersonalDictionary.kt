package org.dark.keyboard.autocorrect

import android.content.Context
import android.content.SharedPreferences

/**
 * Diccionario personal del usuario.
 *
 * Palabras aquí nunca serán autocorregidas.
 * Persiste en SharedPreferences.
 */
class PersonalDictionary(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("personal_dictionary", Context.MODE_PRIVATE)

    private val words = mutableSetOf<String>()

    init {
        words.addAll(prefs.getStringSet("words", emptySet()) ?: emptySet())
    }

    fun contains(word: String): Boolean = words.contains(word.lowercase())

    fun add(word: String) {
        words.add(word.lowercase())
        save()
    }

    fun remove(word: String) {
        words.remove(word.lowercase())
        save()
    }

    fun getAll(): List<String> = words.sorted()

    fun size() = words.size

    private fun save() {
        prefs.edit().putStringSet("words", words).apply()
    }
}
