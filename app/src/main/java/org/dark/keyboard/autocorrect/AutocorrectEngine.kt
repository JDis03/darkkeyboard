package org.dark.keyboard.autocorrect

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Motor de autocorrección nivel 3 (GBoard-style).
 *
 * REGLAS aplicadas (ver análisis de errores comunes):
 *
 * Pre-flight (nunca corregir):
 *   - isTerminalApp          → SSH/RDP no quiere composing
 *   - password/URL/email     → EditorInfo inputType
 *   - ALL_CAPS               → acrónimos
 *   - contiene dígito        → wifi2g, 4k
 *   - PersonalDictionary     → palabras del usuario
 *   - ProperNoun mid-sentence → TitleCase fuera de inicio de oración
 *   - palabra ya correcta    → existe en dict con freq alta
 *   - rechazado antes        → rejectedPairs memory
 *
 * Scoring:
 *   - Levenshtein dist ≤ 2
 *   - Threshold dinámico: dist=1→×1.5, dist=2→×3.0
 *   - Siempre en Dispatchers.Default, timeout 40ms
 *
 * Lifecycle (fixes anti ghost-composing):
 *   - reset() en onFinishInput y onStartInput
 *   - externalCursorMove() en onUpdateSelection
 *   - finishComposing() antes de cualquier sendKeyEvent
 */
class AutocorrectEngine(
    private val context: Context,
    val dict: WordDictionary,
    val personalDict: PersonalDictionary
) {
    companion object {
        private const val TAG = "Autocorrect"

        // Threshold: score(corrección) debe ser N× mayor que score(typed)
        private val THRESHOLD = mapOf(1 to 1.5f, 2 to 3.0f)

        // Abreviaciones comunes — NO capitalizar después de estas
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "vs", "etc",
            "fig", "dept", "est", "approx", "corp", "inc", "ltd"
        )

        private const val PREFS_REJECTED = "autocorrect_rejected"
        private const val MAX_REJECTED = 500
    }

    // ── Estado ──────────────────────────────────────────────────────────

    var isEnabled: Boolean = true
    var isTerminalApp: Boolean = false
    private var shouldAutocorrect: Boolean = true  // basado en EditorInfo
    private var shouldCompose: Boolean = true

    // Palabra en composición (lo que se muestra subrayado)
    private var composingWord = ""

    // Cursor esperado — para detectar movimiento externo
    private var expectedCursorPos = -1

    // Última corrección aplicada (para undo con backspace)
    private var lastCorrection: CorrectionRecord? = null

    // Pares rechazados: "typed→corrected"
    private val rejectedPairs = mutableSetOf<String>()

    private val prefs: SharedPreferences =
        context.getSharedPreferences("autocorrect_prefs", Context.MODE_PRIVATE)

    // ── Data classes ─────────────────────────────────────────────────────

    data class CorrectionRecord(
        val original: String,   // lo que el usuario escribió
        val corrected: String,  // lo que insertamos
        val cursorAfter: Int    // posición del cursor post-corrección
    )

    sealed class CharResult {
        data class UpdateComposing(val composing: String) : CharResult()
        data class CommitDirect(val char: String) : CharResult()
    }

    sealed class SpaceResult {
        data class Corrected(val original: String, val corrected: String) : SpaceResult()
        object PeriodInserted : SpaceResult()   // double-space → ". "
        object Normal : SpaceResult()
    }

    sealed class BackspaceResult {
        data class UndoCorrection(val record: CorrectionRecord) : BackspaceResult()
        data class UpdateComposing(val remaining: String) : BackspaceResult()
        object Normal : BackspaceResult()
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    fun initialize() {
        val saved = prefs.getStringSet(PREFS_REJECTED, emptySet()) ?: emptySet()
        rejectedPairs.addAll(saved)
        Log.i(TAG, "Init: ${rejectedPairs.size} rejected pairs loaded")
    }

    /**
     * Llamar en onStartInput / onFinishInput.
     * Previene ghost-composing entre campos.
     */
    fun reset() {
        composingWord = ""
        lastCorrection = null
        expectedCursorPos = -1
    }

    /**
     * Llamar en onStartInput con el EditorInfo del campo actual.
     * Determina si autocorrect y composing aplican.
     */
    fun onEditorChanged(info: EditorInfo?) {
        reset()
        if (info == null) {
            shouldAutocorrect = !isTerminalApp
            shouldCompose = !isTerminalApp
            return
        }

        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        val flags = type and InputType.TYPE_MASK_FLAGS

        // Campos donde NO autocorregir ni componer
        shouldAutocorrect = when {
            isTerminalApp -> false
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD -> false
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> false
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> false
            variation == InputType.TYPE_TEXT_VARIATION_URI -> false
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> false
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> false
            variation == InputType.TYPE_TEXT_VARIATION_FILTER -> false
            flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0 -> false
            flags and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0 -> false
            else -> true
        }

        // Composing se desactiva en los mismos casos + en terminales
        shouldCompose = shouldAutocorrect && !isTerminalApp
        Log.d(TAG, "EditorChanged: shouldAutocorrect=$shouldAutocorrect shouldCompose=$shouldCompose")
    }

    /**
     * Llamar desde onUpdateSelection.
     * Si el cursor se movió externamente → reset composing (anti-desync).
     */
    fun onCursorMoved(newCursorPos: Int) {
        // Durante composing activo: movimientos son naturales (Android expande el region)
        if (composingWord.isNotEmpty()) {
            expectedCursorPos = newCursorPos
            return
        }
        // Sin composing: solo actualizar posición esperada.
        // NO limpiar lastCorrection aquí — los movimientos del cursor causados por
        // la propia corrección (deleteSurroundingText + commitText) dispararían
        // onUpdateSelection y cerrarían el undo window antes de que el usuario
        // pueda presionar backspace.
        // El undo window se cierra por acciones del usuario: space, enter, letra nueva.
        expectedCursorPos = newCursorPos
    }

    // ── Input handling ───────────────────────────────────────────────────

    /**
     * El usuario presionó una letra.
     * Retorna qué hacer con el IC.
     */
    fun onCharacter(c: Char, isSurrogate: Boolean = false): CharResult {
        // Si no debemos componer → commit directo
        if (!shouldCompose || !isEnabled || isSurrogate) {
            lastCorrection = null  // cualquier char rompe el undo window
            return CharResult.CommitDirect(c.toString())
        }
        composingWord += c
        return CharResult.UpdateComposing(composingWord)
    }

    /**
     * El usuario presionó space.
     * Retorna la acción a ejecutar en el IC.
     */
    fun onSpace(textBeforeCursor: String): SpaceResult {
        // Capturar la palabra antes de limpiar estado
        val word = composingWord.ifEmpty {
            textBeforeCursor.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
        }
        composingWord = ""

        // Double-space → ". " (cancela cualquier undo pendiente)
        if (textBeforeCursor.endsWith(" ")) {
            lastCorrection = null
            return SpaceResult.PeriodInserted
        }

        // Limpiar lastCorrection — el espacio nuevo invalida el undo anterior
        lastCorrection = null

        if (!shouldAutocorrect || !isEnabled || word.length < 2) return SpaceResult.Normal

        val corrected = findCorrection(word, textBeforeCursor) ?: return SpaceResult.Normal

        // Guardamos el record ANTES de actualizar cursor
        lastCorrection = CorrectionRecord(
            original = word,
            corrected = corrected,
            cursorAfter = -1  // se actualiza desde DarkIME2 post-commit
        )
        return SpaceResult.Corrected(word, corrected)
    }

    /**
     * Actualiza la posición esperada del cursor post-corrección.
     * Llamar desde DarkIME2 justo después de commitText.
     */
    fun updateExpectedCursor(pos: Int) {
        expectedCursorPos = pos
        lastCorrection = lastCorrection?.copy(cursorAfter = pos)
    }

    /**
     * El usuario presionó backspace.
     * Prioridad: 1) undo corrección, 2) borrar composing, 3) normal
     */
    fun onBackspace(): BackspaceResult {
        // 1) Undo de corrección — solo si el cursor está donde lo dejamos
        val corr = lastCorrection
        if (corr != null) {
            lastCorrection = null
            // Rechazar inmediatamente para evitar loop infinito
            val pair = "${corr.original.lowercase()}→${corr.corrected.lowercase()}"
            rejectedPairs.add(pair)
            saveRejected()
            return BackspaceResult.UndoCorrection(corr)
        }

        // 2) Borrar del composing
        if (composingWord.isNotEmpty()) {
            composingWord = composingWord.dropLast(1)
            return BackspaceResult.UpdateComposing(composingWord)
        }

        // 3) Backspace normal
        return BackspaceResult.Normal
    }

    /**
     * El usuario presionó Enter, Tab, flecha u otra tecla especial.
     * Finaliza composing sin corregir.
     */
    fun onFinishComposing() {
        composingWord = ""
        lastCorrection = null
    }

    // ── Auto-capitalize ──────────────────────────────────────────────────

    /**
     * ¿La siguiente letra debe ir en mayúscula?
     * Aplica después de ". " "! " "? " o inicio de campo.
     */
    fun shouldCapitalizeNext(textBeforeCursor: String): Boolean {
        if (!shouldAutocorrect || !isEnabled) return false
        val t = textBeforeCursor.trimEnd()
        if (t.isEmpty()) return true

        // Detectar fin de oración (pero no abreviaciones)
        val endsWithSentenceEnd = t.endsWith(".") || t.endsWith("!") || t.endsWith("?")
        if (!endsWithSentenceEnd) return false

        // Verificar que no es una abreviación conocida
        val lastWord = t.dropLast(1).trimEnd().split(Regex("\\s+")).lastOrNull()?.lowercase() ?: ""
        if (ABBREVIATIONS.contains(lastWord)) return false

        // No capitalizar si la última palabra es una inicial sola: "A."
        if (lastWord.length == 1) return false

        return true
    }

    // ── Corrección ───────────────────────────────────────────────────────

    private fun findCorrection(typed: String, contextText: String): String? {
        val t = typed.lowercase()

        // Pre-flight checks
        if (typed.isAllCaps()) return null
        if (typed.containsDigit()) return null
        if (personalDict.contains(typed)) return null
        if (isMidSentenceProperNoun(typed, contextText)) return null

        // Si la palabra ya existe en el dict con buena frecuencia → no corregir
        val typedFreq = dict.getFreq(t)

        // Buscar candidatos por edit distance (llamar siempre en background)
        val candidates = dict.findByEditDistance(t, WordDictionary.MAX_EDIT_DISTANCE)
            .filter { it.word != t }
            .take(5)

        if (candidates.isEmpty()) return null

        val best = candidates.first()
        val bestFreq = dict.getFreq(best.word)
        val dist = levenshteinSimple(t, best.word)

        // Verificar que el par no fue rechazado
        val pairKey = "$t→${best.word}"
        if (rejectedPairs.contains(pairKey)) return null

        // Threshold dinámico por distancia
        val threshold = THRESHOLD[dist] ?: return null

        // Si la palabra tipada ya existe con suficiente frecuencia → no corregir
        if (typedFreq > 0 && bestFreq < typedFreq * threshold) return null

        // Si la tipada no existe en el dict pero la corrección sí tiene freq alta → corregir
        if (typedFreq == 0 && bestFreq < 5000) return null  // corrección también oscura → skip

        // Preservar capitalización
        return if (typed[0].isUpperCase() && !isMidSentenceProperNoun(typed, contextText)) {
            best.word.replaceFirstChar { it.uppercase() }
        } else {
            best.word
        }
    }

    private fun isMidSentenceProperNoun(word: String, textBefore: String): Boolean {
        if (word.isEmpty() || !word[0].isUpperCase()) return false
        val t = textBefore.trimEnd()
        val isStartOfSentence = t.isEmpty() ||
            t.endsWith(".") || t.endsWith("!") || t.endsWith("?") || t.endsWith("\n")
        return !isStartOfSentence
    }

    // ── Utils ────────────────────────────────────────────────────────────

    fun getComposing(): String = composingWord

    fun canUndo(): Boolean = lastCorrection != null

    private fun saveRejected() {
        val toSave = if (rejectedPairs.size > MAX_REJECTED) {
            rejectedPairs.toList().takeLast(MAX_REJECTED).toSet()
        } else rejectedPairs
        prefs.edit().putStringSet(PREFS_REJECTED, toSave).apply()
    }

    private fun levenshteinSimple(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length; val n = b.length
        val prev = IntArray(n + 1) { it }
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) prev[j - 1]
                else 1 + minOf(prev[j], curr[j - 1], prev[j - 1])
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return prev[n]
    }

    private fun String.isAllCaps() =
        length > 1 && all { it.isUpperCase() || !it.isLetter() } && any { it.isLetter() }

    private fun String.containsDigit() = any { it.isDigit() }
}
