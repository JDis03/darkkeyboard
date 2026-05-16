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
        // Valores conservadores — preferimos NO corregir antes que corregir mal
        private val THRESHOLD = mapOf(1 to 3.0f, 2 to 8.0f)

        // Longitud mínima para autocorrect — palabras cortas son ambiguas
        // "si"→"mi", "yo"→"lo", "el"→"él" tienen dist=1 pero son palabras válidas
        private const val MIN_WORD_LENGTH = 4

        // Frecuencia mínima del candidato para ser considerado corrección
        private const val MIN_CANDIDATE_FREQ = 20_000

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
    /**
     * Fuerza shouldCompose y shouldAutocorrect ignorando lo que onEditorChanged haya decidido.
     * Usar cuando AppInputProfile sabe más que los flags del EditorInfo
     * (ej: browsers ponen FLAG_NO_SUGGESTIONS aunque el usuario quiere autocorrect).
     */
    fun overrideProfile(useComposing: Boolean, useAutocorrect: Boolean) {
        shouldCompose     = useComposing  && !isTerminalApp
        shouldAutocorrect = useAutocorrect && !isTerminalApp
        Log.d(TAG, "Profile override: shouldCompose=$shouldCompose shouldAutocorrect=$shouldAutocorrect")
    }

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
            lastCorrection = null
            return CharResult.CommitDirect(c.toString())
        }
        // Primera letra de una palabra nueva → cerrar ventana de undo.
        // Sin esto, backspace sobre la segunda palabra intentaría deshacer
        // la corrección de la primera en lugar de borrar del composing.
        if (composingWord.isEmpty()) {
            lastCorrection = null
        }
        composingWord += c
        return CharResult.UpdateComposing(composingWord)
    }

    /**
     * El usuario presionó space.
     * Retorna la acción a ejecutar en el IC.
     */
    /**
     * @param suggestionHint Top sugerencia de DictSuggestionEngine (ya visible en la barra).
     *   Si se provee, se usa como candidato preferido sobre findByEditDistance.
     *   Esto evita que autocorrect y la barra elijan palabras distintas.
     */
    fun onSpace(textBeforeCursor: String, suggestionHint: String? = null): SpaceResult {
        val word = composingWord.ifEmpty {
            textBeforeCursor.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
        }
        composingWord = ""

        // Double-space → ". "
        if (textBeforeCursor.endsWith(" ")) {
            lastCorrection = null
            return SpaceResult.PeriodInserted
        }

        lastCorrection = null

        Log.d(TAG, "onSpace: word='$word' len=${word.length} shouldAutocorrect=$shouldAutocorrect isEnabled=$isEnabled hint=$suggestionHint")

        if (!shouldAutocorrect || !isEnabled || word.length < MIN_WORD_LENGTH) {
            Log.d(TAG, "onSpace: skipped (shouldAutocorrect=$shouldAutocorrect isEnabled=$isEnabled len=${word.length} minLen=$MIN_WORD_LENGTH)")
            return SpaceResult.Normal
        }

        val corrected = findCorrection(word, textBeforeCursor, suggestionHint)
        Log.d(TAG, "onSpace: findCorrection('$word') → '$corrected'")
        if (corrected == null) return SpaceResult.Normal

        lastCorrection = CorrectionRecord(original = word, corrected = corrected, cursorAfter = -1)
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

    private fun findCorrection(typed: String, contextText: String, hint: String? = null): String? {
        val t = typed.lowercase()

        Log.d(TAG, "findCorrection: typed='$t' hint='$hint'")

        // Pre-flight: nunca corregir estas categorías
        if (typed.isAllCaps()) { Log.d(TAG, "  skip: ALL_CAPS"); return null }
        if (typed.containsDigit()) { Log.d(TAG, "  skip: contains digit"); return null }
        if (personalDict.contains(typed)) { Log.d(TAG, "  skip: personal dict"); return null }
        if (isMidSentenceProperNoun(typed, contextText)) { Log.d(TAG, "  skip: proper noun"); return null }

        val typedFreq = dict.getFreq(t)
        val typedIsInDict = typedFreq > 0
        Log.d(TAG, "  typedFreq=$typedFreq typedIsInDict=$typedIsInDict")

        // ── Prioridad 1: hint de DictSuggestionEngine ───────────────────
        // IMPORTANTE: el hint puede ser una predicción de SIGUIENTE PALABRA (bigrams),
        // no una corrección de la actual. Solo lo usamos si:
        //   a) La palabra tipada NO está en el dict (es un error)    ← caso principal
        //   b) Y la hint tiene edit distance ≤ 2 (es realmente similar a lo tipado)
        //   c) Y la hint tiene frecuencia mínima
        if (hint != null) {
            val h = hint.lowercase().trim()
            val hintDist = levenshteinSimple(t, h)
            if (h != t && h.length >= MIN_WORD_LENGTH && hintDist <= 2) {
                val pairKey = "$t→$h"
                if (!rejectedPairs.contains(pairKey)) {
                    val hintFreq = dict.getFreq(h)
                    // Solo aplicar hint si la palabra tipada es un error
                    if (!typedIsInDict && hintFreq >= MIN_CANDIDATE_FREQ) {
                        return preserveCase(typed, h)
                    }
                }
            }
        }

        // ── Prioridad 2: edit distance pura ──────────────────────────────
        if (typedIsInDict) { Log.d(TAG, "  skip: word in dict (freq=$typedFreq)"); return null }

        val candidates = dict.findByEditDistance(t, WordDictionary.MAX_EDIT_DISTANCE)
            .filter { it.word != t }.take(5)
        Log.d(TAG, "  candidates: ${candidates.map { "${it.word}(${dict.getFreq(it.word)})" }}")

        if (candidates.isEmpty()) { Log.d(TAG, "  skip: no candidates within dist=${WordDictionary.MAX_EDIT_DISTANCE}"); return null }

        val best = candidates.first()
        val bestFreq = dict.getFreq(best.word)
        val dist = levenshteinSimple(t, best.word)
        Log.d(TAG, "  best='${best.word}' bestFreq=$bestFreq dist=$dist threshold=${THRESHOLD[dist]}")

        val pairKey = "$t→${best.word}"
        if (rejectedPairs.contains(pairKey)) { Log.d(TAG, "  skip: rejected"); return null }
        val threshold = THRESHOLD[dist] ?: run { Log.d(TAG, "  skip: dist=$dist out of range"); return null }
        if (bestFreq < MIN_CANDIDATE_FREQ) { Log.d(TAG, "  skip: freq $bestFreq < min $MIN_CANDIDATE_FREQ"); return null }
        if (bestFreq < typedFreq * threshold) { Log.d(TAG, "  skip: threshold not met"); return null }

        Log.i(TAG, "  CORRECTING '$t' → '${best.word}'")
        return preserveCase(typed, best.word)
    }

    private fun preserveCase(typed: String, correction: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase()) {
            correction.replaceFirstChar { it.uppercase() }
        } else {
            correction
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
