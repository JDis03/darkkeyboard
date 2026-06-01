package org.dark.keyboard.autocorrect

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
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


        // Threshold: score(corrección) debe ser N× mayor que score(typed)
        // Valores conservadores — preferimos NO corregir antes que corregir mal
        private val THRESHOLD = mapOf(1 to 3.0f, 2 to 8.0f)

        // Longitud mínima para autocorrect — palabras cortas son ambiguas
        // "si"→"mi", "yo"→"lo", "el"→"él" tienen dist=1 pero son palabras válidas
        private const val MIN_WORD_LENGTH = 4

        // Frecuencia mínima del candidato para ser considerado corrección
        private const val MIN_CANDIDATE_FREQ = 20_000
        
        // Frecuencia mínima para hints de SuggestionEngine (más permisivo porque
        // el hint ya fue rankeado por bigrams + frecuencia → más confiable)
        // Bajado de 5000 a 2000 porque palabras como "corrupción" (3306) y 
        // "calcetines" (4603) son válidas pero poco frecuentes
        private const val MIN_HINT_FREQ = 2_000

        // Abreviaciones comunes — NO capitalizar después de estas
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "vs", "etc",
            "fig", "dept", "est", "approx", "corp", "inc", "ltd"
        )

        private const val PREFS_REJECTED = "autocorrect_rejected"
        private const val MAX_REJECTED = 500
    }

    // ── Estado ──────────────────────────────────────────────────────────

    var isEnabled: Boolean = false  // Default OFF — set by initialize() from prefs
    var isTerminalApp: Boolean = false
    private var shouldAutocorrect: Boolean = true  // basado en EditorInfo
    private var shouldCompose: Boolean = true

    // Palabra en composición (lo que se muestra subrayado)
    private var composingWord = ""

    // Cursor esperado — para detectar movimiento externo
    private var expectedCursorPos = -1

    // Última corrección aplicada (para undo con backspace)
    private var lastCorrection: CorrectionRecord? = null
    
    // Última palabra antes de presionar espacio (para restaurar si borra el espacio)
    private var lastWordBeforeSpace: String = ""
    
    // Si true, NO autocorregir la palabra actual (usuario hizo undo y sigue escribiendo)
    private var skipCurrentWord: Boolean = false

    // Pares rechazados permanentemente (persisten en SharedPreferences)
    private val rejectedPairs = mutableSetOf<String>()
    
    // Pares rechazados para esta sesión (solo in-memory, se limpia en reset)
    private val sessionRejected = mutableSetOf<String>()

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
        
        // Leer preferencia de autocorrect (desactivado por defecto)
        val globalPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val prefValue = globalPrefs.getBoolean("autocorrect_enabled", false)
        isEnabled = prefValue
        
        Timber.e("=== AutocorrectEngine.initialize() ===")
        Timber.e("autocorrect_enabled pref = $prefValue, isEnabled = $isEnabled")
    }
    
    /**
     * Recargar preferencia de autocorrect (llamar cuando cambie en Settings)
     */
    fun reloadPreferences() {
        val globalPrefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        isEnabled = globalPrefs.getBoolean("autocorrect_enabled", false)
        Timber.i("Autocorrect preference reloaded: ${if (isEnabled) "ON" else "OFF"}")
    }

    /**
     * Llamar en onStartInput / onFinishInput.
     * Previene ghost-composing entre campos.
     */
    fun reset() {
        composingWord = ""
        lastCorrection = null
        lastWordBeforeSpace = ""
        skipCurrentWord = false
        expectedCursorPos = -1
        sessionRejected.clear()  // Nueva sesión → limpiar rechazos temporales
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
        Timber.d("EditorChanged: shouldAutocorrect=$shouldAutocorrect shouldCompose=$shouldCompose")
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
        Timber.d("Profile override: shouldCompose=$shouldCompose shouldAutocorrect=$shouldAutocorrect")
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
     * Verifica si el cursor está al final del composing word.
     * HeliBoard: isCursorFrontOrMiddleOfComposingWord()
     * Si el cursor NO está al final → hay que commitear antes de editar.
     */
    fun isCursorAtEndOfComposing(textBeforeCursor: String, cursorPos: Int): Boolean {
        if (composingWord.isEmpty()) return true  // no composing, OK
        val expectedEnd = textBeforeCursor.length
        // El cursor está al final si el texto antes del cursor termina 
        // con nuestro composingWord (o parte de él)
        return expectedEnd == cursorPos
    }
    
    /**
     * HeliBoard: cursor en medio del composing → revertir.
     * Si el texto antes del cursor NO contiene nuestro composingWord al final,
     * estamos desincronizados o el cursor se movió.
     */
    fun isComposingDesynced(textBeforeCursor: String): Boolean {
        if (composingWord.isEmpty()) return false
        return !textBeforeCursor.endsWith(composingWord)
    }

    /**
     * El usuario presionó una letra.
     * Retorna qué hacer con el IC.
     */
    fun onCharacter(c: Char, isSurrogate: Boolean = false): CharResult {
        // Si no debemos componer → commit directo
        if (!shouldCompose || !isEnabled || isSurrogate) {
            lastCorrection = null
            lastWordBeforeSpace = ""
            return CharResult.CommitDirect(c.toString())
        }
        // Primera letra de una palabra nueva → cerrar ventana de undo y restauración.
        // Sin esto, backspace sobre la segunda palabra intentaría deshacer
        // la corrección de la primera en lugar de borrar del composing.
        if (composingWord.isEmpty()) {
            lastCorrection = null
            lastWordBeforeSpace = ""  // Ya no se puede restaurar, usuario empezó palabra nueva
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
        Timber.d("onSpace called: textBeforeCursor='$textBeforeCursor', composingWord='$composingWord', skip=$skipCurrentWord")
        
        // ── CRITICAL FIX: Solo autocorregir palabras que fueron COMPUESTAS ────
        // Si composingWord está vacío, significa que:
        //   - El desync fix commiteó directo (cursor movido o campo desincronizado)
        //   - Selection safety commiteó directo (había texto seleccionado)
        //   - El cursor se movió externamente
        // En NINGUNO de esos casos debemos autocorregir — no tenemos certeza de qué
        // palabra "pertenece" al usuario ni si deleteSurroundingText borrará correctamente.
        // Bug reportado: "después de espacio empieza a borrar lo que quiero escribir"
        if (composingWord.isEmpty()) {
            Timber.d("onSpace: composing empty — skipping autocorrect (safety)")
            lastWordBeforeSpace = textBeforeCursor.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
            return SpaceResult.Normal
        }
        
        val word = composingWord
        
        // Guardar palabra antes de limpiar (para restaurar si borra el espacio)
        lastWordBeforeSpace = word
        composingWord = ""

        lastCorrection = null
        
        // Si el usuario hizo undo en esta palabra, NO autocorregir
        // El flag se resetea aquí para la próxima palabra
        if (skipCurrentWord) {
            Timber.d("Skipping autocorrect for '$word' (user rejected previous correction)")
            skipCurrentWord = false
            return SpaceResult.Normal
        }

        if (!shouldAutocorrect || !isEnabled || word.length < MIN_WORD_LENGTH) return SpaceResult.Normal

        val corrected = findCorrection(word, textBeforeCursor, suggestionHint) ?: return SpaceResult.Normal

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
            // Rechazar para esta sesión (no permanente) — evita re-corrección inmediata
            // pero permite que funcione en próximas sesiones/campos
            val pair = "${corr.original.lowercase()}→${corr.corrected.lowercase()}"
            sessionRejected.add(pair)
            skipCurrentWord = true
            Timber.d("Session reject: $pair, skipCurrentWord=true")
            return BackspaceResult.UndoCorrection(corr)
        }

        // 2) Borrar del composing
        if (composingWord.isNotEmpty()) {
            composingWord = composingWord.dropLast(1)
            return BackspaceResult.UpdateComposing(composingWord)
        }

        // 3) Espacio fue borrado — marcar skip y dejar que el backspace normal
        //    elimine el espacio. composingWord se queda vacío para que el 
        //    desyncFix en onCharacter adopte la palabra al escribir la siguiente letra.
        if (lastWordBeforeSpace.isNotEmpty()) {
            skipCurrentWord = true
            lastWordBeforeSpace = ""
            Timber.d("Space deleted, skipCurrentWord=true, word will be adopted on next char")
        }

        // 4) Backspace normal
        return BackspaceResult.Normal
    }

    /**
     * El usuario presionó Enter, Tab, flecha u otra tecla especial.
     * Finaliza composing sin corregir.
     */
    fun onFinishComposing() {
        composingWord = ""
        lastCorrection = null
        lastWordBeforeSpace = ""
    }
    
    /**
     * Restaura el composing word después de un undo.
     * Llamar desde DarkIME2 después de setComposingText(r.original).
     */
    fun restoreComposing(word: String) {
        composingWord = word
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

        // Pre-flight: nunca corregir estas categorías
        if (typed.isAllCaps()) return null
        if (typed.containsDigit()) return null
        if (personalDict.contains(typed)) return null
        if (isMidSentenceProperNoun(typed, contextText)) return null

        val typedFreq = dict.getFreq(t)
        val typedIsInDict = typedFreq > 0

        // ── Hint de DictSuggestionEngine ────────────────────────────────
        // El hint es la primera sugerencia de la barra. Puede ser:
        //   - Una corrección (lo que queremos usar)
        //   - Una predicción de siguiente palabra (bigram) - NO usar
        //   - Una completación de prefijo - usar si es corrección
        //
        // Para distinguir, verificamos que el hint sea SIMILAR a lo tecleado:
        //   - Primera letra compatible (igual o adyacente QWERTY)
        //   - Edit distance ≤ 3
        //   - Comparte prefijo (para palabras largas)
        if (hint != null) {
            val h = hint.lowercase().trim()
            val hintDist = levenshteinSimple(t, h)
            Timber.d("Hint check: '$t' → hint='$h' dist=$hintDist typedInDict=$typedIsInDict")
            
            // El hint debe ser una CORRECCIÓN, no una predicción.
            // Una predicción tendría dist muy alto (palabras completamente diferentes)
            val isLikelyCorrection = h != t && 
                                     h.length >= 2 && 
                                     hintDist <= 3 && 
                                     firstLetterCompatible(t, h) &&
                                     prefixCompatible(t, h)  // También verificar prefijo
            
            if (isLikelyCorrection) {
                Timber.d("  ✓ Hint looks like a correction (dist=$hintDist ≤ 3, prefix OK)")
                val pairKey = "$t→$h"
                
                // Chequear rechazos (permanentes + sesión)
                if (rejectedPairs.contains(pairKey)) {
                    Timber.d("  ✗ Hint rejected: in rejectedPairs")
                } else if (sessionRejected.contains(pairKey)) {
                    Timber.d("  ✗ Hint rejected: in sessionRejected")
                } else {
                    val hintFreq = dict.getFreq(h)
                    Timber.d("  Hint freq=$hintFreq, MIN=$MIN_HINT_FREQ, typedInDict=$typedIsInDict")
                    
                    // Solo aplicar hint si la palabra tipada es un error
                    if (!typedIsInDict && hintFreq >= MIN_HINT_FREQ) {
                        Timber.d("  ✓ USING HINT: '$t' → '$h'")
                        return preserveCase(typed, h)
                    } else if (typedIsInDict) {
                        Timber.d("  ✗ Hint skipped: typed word is in dict (freq=$typedFreq)")
                    } else {
                        Timber.d("  ✗ Hint skipped: hint freq too low ($hintFreq < $MIN_HINT_FREQ)")
                    }
                }
            } else {
                // El hint no parece ser una corrección (probablemente es una predicción)
                val reason = when {
                    h == t -> "hint equals typed"
                    h.length < 2 -> "hint too short"
                    hintDist > 3 -> "distance too high ($hintDist > 3)"
                    !firstLetterCompatible(t, h) -> "first letter not compatible"
                    !prefixCompatible(t, h) -> "prefix not compatible"
                    else -> "unknown"
                }
                Timber.d("  ✗ Hint is likely a prediction, not a correction: $reason")
            }
        }

        // ── Prioridad 2: edit distance - DESHABILITADO ──────────────────
        // El fallback a edit distance sin hint era demasiado agresivo y causaba
        // correcciones incorrectas como 'calcu' → 'calle', 'lite' → 'listo'.
        // 
        // La autocorrección ahora SOLO se aplica cuando:
        // 1. El hint de la barra de sugerencias es una corrección válida (dist ≤ 3)
        // 2. La palabra tecleada NO está en el diccionario
        // 3. El hint tiene frecuencia suficiente
        //
        // Si no hay hint válido, preferimos NO corregir antes que corregir mal.
        // El usuario puede seleccionar manualmente de la barra de sugerencias.
        Timber.d("No hint correction available for '$t', skipping autocorrect")
        return null
    }

    private fun preserveCase(typed: String, correction: String): String =
        if (typed.isNotEmpty() && typed[0].isUpperCase()) {
            correction.replaceFirstChar { it.uppercase() }
        } else {
            correction
        }

    private fun isMidSentenceProperNoun(word: String, textBefore: String): Boolean {
        if (word.isEmpty() || !word[0].isUpperCase()) return false
        // textBefore puede incluir la palabra tipada — removerla para obtener contexto real
        val context = textBefore.trimEnd().removeSuffix(word).trimEnd()
        val isStartOfSentence = context.isEmpty() ||
            context.endsWith(".") || context.endsWith("!") || context.endsWith("?") || context.endsWith("\n")
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

    /**
     * Damerau-Levenshtein: incluye transposiciones adyacentes.
     * "teh"→"the" = 1 (transposición), no 2 (dos sustituciones)
     */
    private fun levenshteinSimple(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val d = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) d[i][0] = i
        for (j in 0..n) d[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(
                    d[i - 1][j] + 1,
                    d[i][j - 1] + 1,
                    d[i - 1][j - 1] + cost
                )
                // Transposición adyacente
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + cost)
                }
            }
        }
        return d[m][n]
    }

    /**
     * Verifica si la primera letra de la corrección es compatible con la primera letra tipada.
     * Acepta: misma letra, o teclas adyacentes en el teclado QWERTY.
     * Esto evita correcciones como "Calce" → "Hace" (C y H no son adyacentes).
     */
    /**
     * Verifica que la primera letra sea compatible (igual o adyacente en QWERTY).
     */
    private fun firstLetterCompatible(typed: String, correction: String): Boolean {
        if (typed.isEmpty() || correction.isEmpty()) return false
        val t = typed[0].lowercaseChar()
        val c = correction[0].lowercaseChar()
        if (t == c) return true
        
        // Mapa de teclas adyacentes en QWERTY (incluyendo español)
        val adjacent = mapOf(
            'q' to setOf('w', 'a'),
            'w' to setOf('q', 'e', 'a', 's'),
            'e' to setOf('w', 'r', 's', 'd'),
            'r' to setOf('e', 't', 'd', 'f'),
            't' to setOf('r', 'y', 'f', 'g'),
            'y' to setOf('t', 'u', 'g', 'h'),
            'u' to setOf('y', 'i', 'h', 'j'),
            'i' to setOf('u', 'o', 'j', 'k'),
            'o' to setOf('i', 'p', 'k', 'l'),
            'p' to setOf('o', 'l', 'ñ'),
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('a', 'w', 'e', 'd', 'z', 'x'),
            'd' to setOf('s', 'e', 'r', 'f', 'x', 'c'),
            'f' to setOf('d', 'r', 't', 'g', 'c', 'v'),
            'g' to setOf('f', 't', 'y', 'h', 'v', 'b'),
            'h' to setOf('g', 'y', 'u', 'j', 'b', 'n'),
            'j' to setOf('h', 'u', 'i', 'k', 'n', 'm'),
            'k' to setOf('j', 'i', 'o', 'l', 'm'),
            'l' to setOf('k', 'o', 'p', 'ñ'),
            'ñ' to setOf('l', 'p'),
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )
        
        return adjacent[t]?.contains(c) == true
    }
    
    /**
     * Verifica que el prefijo sea compatible.
     * Para palabras ≥4 caracteres, la corrección debe compartir al menos 2 caracteres iniciales.
     * Esto previene correcciones como 'calcu' → 'calle' (solo comparten 'c').
     */
    private fun prefixCompatible(typed: String, correction: String): Boolean {
        val tLower = typed.lowercase()
        val cLower = correction.lowercase()
        
        // Para palabras cortas, solo verificar primera letra
        if (typed.length < 4) return true
        
        // Calcular caracteres iniciales compartidos
        val sharedPrefix = tLower.zip(cLower).takeWhile { (a, b) -> a == b }.count()
        
        // Palabras de 4-5 chars: requieren al menos 2 chars compartidos
        // Palabras de 6+ chars: requieren al menos 3 chars compartidos
        val minRequired = if (typed.length >= 6) 3 else 2
        
        return sharedPrefix >= minRequired
    }
    
    /**
     * No corregir a una palabra más corta si el usuario claramente está escribiendo algo más largo.
     * Ejemplo: 'calcu' (5 chars) → no corregir a 'calle' (5 chars) pero tampoco a 'cal' (3 chars)
     * La corrección debe ser al menos tan larga como lo tecleado, o como máximo 1 char más corta.
     */
    private fun lengthCompatible(typed: String, correction: String): Boolean {
        // La corrección no puede ser más de 1 caracter más corta que lo tecleado
        return correction.length >= typed.length - 1
    }

    private fun String.isAllCaps() =
        length > 1 && all { it.isUpperCase() || !it.isLetter() } && any { it.isLetter() }

    private fun String.containsDigit() = any { it.isDigit() }
}
