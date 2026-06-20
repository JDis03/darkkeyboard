package org.dark.keyboard.autocorrect

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Utilidad de safety checks para autocorrección.
 * 
 * Centraliza todas las reglas de "nunca corregir" para evitar correcciones
 * incorrectas en contextos especiales (passwords, URLs, terminales, etc).
 */
object AutocorrectGuards {

    /**
     * Contexto de autocorrección — información del campo actual.
     */
    data class AutocorrectContext(
        val isTerminalApp: Boolean,
        val shouldAutocorrect: Boolean,
        val editorInfo: EditorInfo? = null
    )

    /**
     * Verifica si la palabra debe ser excluida de autocorrección.
     * 
     * @param word Palabra a verificar
     * @param context Contexto del campo actual
     * @param personalDict Diccionario personal del usuario
     * @return true si la palabra NO debe ser autocorregida
     */
    fun shouldSkip(
        word: String,
        context: AutocorrectContext,
        personalDict: PersonalDictionary
    ): Boolean {
        // 1. Contexto global: terminal o autocorrect deshabilitado
        if (context.isTerminalApp) return true
        if (!context.shouldAutocorrect) return true

        // 2. Tipo de campo: password, URL, email
        context.editorInfo?.let { info ->
            val type = info.inputType
            val variation = type and InputType.TYPE_MASK_VARIATION
            val flags = type and InputType.TYPE_MASK_FLAGS

            when {
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD -> return true
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return true
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> return true
                variation == InputType.TYPE_TEXT_VARIATION_URI -> return true
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> return true
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return true
                variation == InputType.TYPE_TEXT_VARIATION_FILTER -> return true
                flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 -> return true
                flags and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0 -> return true
            }
        }

        // 3. Características de la palabra
        if (word.length < 4) return true  // palabras cortas son ambiguas
        if (word.isAllCaps()) return true  // acrónimos (NASA, HTTP)
        if (word.containsDigit()) return true  // wifi2g, 4k
        if (personalDict.contains(word)) return true  // palabras del usuario

        return false
    }

    private fun String.isAllCaps() =
        length > 1 && all { it.isUpperCase() || !it.isLetter() } && any { it.isLetter() }

    private fun String.containsDigit() = any { it.isDigit() }
}
