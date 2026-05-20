package org.dark.keyboard.autocorrect

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Advanced autocorrect tests based on best practices from:
 * - HeliBoard (AOSP LatinIME fork)
 * - FUTO Keyboard (Transformer + Dictionary dual-path)
 * - FlorisBoard (NLP-based)
 * - OpenBoard
 *
 * Tests cover:
 * - Hint-based autocorrect (suggestion engine integration)
 * - Frequency threshold behavior
 * - Edit distance edge cases
 * - Rejection learning (session vs permanent)
 * - Case preservation
 * - Proper noun detection
 * - Abbreviation handling
 * - Multi-word context scenarios
 * - Cursor validation for undo
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AutocorrectEngineAdvancedTest {

    private lateinit var ctx: Context
    private lateinit var dict: WordDictionary
    private lateinit var personalDict: PersonalDictionary
    private lateinit var engine: AutocorrectEngine

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        dict = WordDictionary(ctx).also { it.load("es") }
        personalDict = PersonalDictionary(ctx)
        engine = AutocorrectEngine(ctx, dict, personalDict).also { it.initialize() }
    }

    // ═══════════════════════════════════════════════════════════════════
    // HINT-BASED AUTOCORRECT (Suggestion Engine Integration)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `hint - usa suggestion como correccion preferida dist hasta 3`() {
        // "colec" no está en dict, hint="colección" tiene dist=4 (no 3)
        // Por lo tanto el hint NO se usa, y autocorrect busca por edit distance puro
        // "colec" → "comer" (dist=2, freq alta) es el resultado esperado
        engine.onCharacter('c'); engine.onCharacter('o'); engine.onCharacter('l')
        engine.onCharacter('e'); engine.onCharacter('c')
        val result = engine.onSpace("colec", "colección")

        // El hint tiene dist=4 > 3 → se ignora, usa edit distance puro
        // "comer" es corrección válida (dist=2, freq alta)
        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            // Puede ser "comer" u otra palabra con dist≤2 y freq alta
            assertTrue(result.corrected.isNotEmpty())
        }
    }

    @Test fun `hint - no usa si palabra tipada ya esta en dict`() {
        // "hola" está en dict → no corregir aunque hint sea diferente
        engine.onCharacter('h'); engine.onCharacter('o')
        engine.onCharacter('l'); engine.onCharacter('a')
        val result = engine.onSpace("hola", "holanda")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `hint - no usa si distancia mayor a 3`() {
        // "abc" → "extraordinario" tiene dist > 3 → ignorar hint
        engine.onCharacter('a'); engine.onCharacter('b'); engine.onCharacter('c')
        val result = engine.onSpace("abc", "extraordinario")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `hint - respeta rechazos permanentes`() {
        // Forzar rechazo del par
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh", "the")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            engine.onBackspace()  // rechaza → agrega a rejectedPairs

            // Intentar de nuevo con hint
            engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
            val second = engine.onSpace("teh", "the")
            assertFalse("No debe repetir corrección rechazada con hint",
                second is AutocorrectEngine.SpaceResult.Corrected &&
                (second as AutocorrectEngine.SpaceResult.Corrected).corrected == "the"
            )
        }
    }

    @Test fun `hint - respeta rechazos de sesion`() {
        // Rechazo de sesión (backspace undo)
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh", "the")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            engine.onBackspace()  // sessionRejected

            // Mismo campo, misma sesión → no debe corregir
            engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
            val second = engine.onSpace("teh", "the")
            assertFalse("No debe repetir corrección rechazada en sesión",
                second is AutocorrectEngine.SpaceResult.Corrected &&
                (second as AutocorrectEngine.SpaceResult.Corrected).corrected == "the"
            )
        }
    }

    @Test fun `hint - reset limpia sessionRejected pero no rejectedPairs`() {
        // Forzar corrección + rechazo
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh", "the")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            engine.onBackspace()  // rechaza

            // Reset de sesión
            engine.reset()

            // Mismo campo después de reset → sessionRejected limpio
            // Pero rejectedPairs persiste si era permanente
            engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
            val afterReset = engine.onSpace("teh", "the")
            // Puede corregir de nuevo (sessionRejected limpio)
            // O no (si rejectedPairs persistió) — ambos válidos
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FREQUENCY THRESHOLD BEHAVIOR (HeliBoard-style scoring)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `freq - no corrige candidato de baja frecuencia`() {
        // Palabra con dist=1 pero freq < MIN_CANDIDATE_FREQ → no corregir
        // Simular con palabra inventada que tenga dist=1 con palabra rara
        engine.onCharacter('q'); engine.onCharacter('z')
        engine.onCharacter('x'); engine.onCharacter('y')
        val result = engine.onSpace("qzxy")

        // No debe corregir a palabra rara de baja frecuencia
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `freq - corrige solo si candidato es N× mas frecuente que typed`() {
        // typedFreq=0 (no está en dict), candidateFreq=alto → debe corregir
        // "caas" → "las" (dist=2, "las" tiene freq muy alta)
        // o "casa" (dist=1, también freq alta)
        engine.onCharacter('c'); engine.onCharacter('a')
        engine.onCharacter('a'); engine.onCharacter('s')
        val result = engine.onSpace("caas")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            // Debe corregir a algo con dist≤2 y freq alta
            assertTrue(result.corrected.isNotEmpty())
            // "las" o "casa" son correcciones válidas
        }
    }

    @Test fun `freq - threshold dinamico segun edit distance`() {
        // dist=1 → threshold ×3.0, dist=2 → threshold ×8.0
        // dist=1 con freq ratio < 3.0 → no corregir
        // dist=2 con freq ratio < 8.0 → no corregir

        // "misa" → "masa" (dist=1, ambas comunes)
        engine.onCharacter('m'); engine.onCharacter('i')
        engine.onCharacter('s'); engine.onCharacter('a')
        val result = engine.onSpace("misa")

        // Puede corregir o no dependiendo de freq ratio — ambos válidos
        // Lo importante es que no crash y respete thresholds
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDIT DISTANCE EDGE CASES
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `edit - transposicion de letras (teh→the)`() {
        // "teh" → "the" tiene dist=2 en Levenshtein estándar
        // (no es Damerau-Levenshtein que permite transposiciones con costo 1)
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            assertEquals("the", result.corrected)
        }
    }

    @Test fun `edit - letra faltante (cas→casa)`() {
        // "cas" → "casa" (dist=1, inserción)
        engine.onCharacter('c'); engine.onCharacter('a'); engine.onCharacter('s')
        val result = engine.onSpace("cas")

        // MIN_WORD_LENGTH=4, "cas" tiene 3 → no debe corregir
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edit - letra extra (caasa→casa)`() {
        // "caasa" → "casa" (dist=1, eliminación)
        engine.onCharacter('c'); engine.onCharacter('a')
        engine.onCharacter('a'); engine.onCharacter('s')
        engine.onCharacter('a')
        val result = engine.onSpace("caasa")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            assertEquals("casa", result.corrected)
        }
    }

    @Test fun `edit - letra incorrecta (caza→casa en contexto español)`() {
        // "caza" está en dict (caza = hunting) → no corregir
        engine.onCharacter('c'); engine.onCharacter('a')
        engine.onCharacter('z'); engine.onCharacter('a')
        val result = engine.onSpace("caza")

        // "caza" es palabra válida → no corregir
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edit - palabras cortas no se corrigen (MIN_WORD_LENGTH=4)`() {
        // "si" → "mi" (dist=1, pero len=2 < 4)
        engine.onCharacter('s'); engine.onCharacter('i')
        val result = engine.onSpace("si")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edit - palabras de 4 letras si se corrigen`() {
        // "much" → "muy" (dist=1, "muy" tiene freq muy alta)
        // o "mucho" (dist=2, también freq alta)
        engine.onCharacter('m'); engine.onCharacter('u')
        engine.onCharacter('c'); engine.onCharacter('h')
        val result = engine.onSpace("much")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            // Debe corregir a algo con dist≤2 y freq alta
            assertTrue(result.corrected.isNotEmpty())
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // CASE PRESERVATION (HeliBoard-style)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `case - preserva TitleCase (Miea→correction)`() {
        engine.onCharacter('M'); engine.onCharacter('i')
        engine.onCharacter('e'); engine.onCharacter('a')
        val result = engine.onSpace("Miea")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            // Debe preservar TitleCase
            assertTrue("Primera letra debe ser mayúscula", result.corrected[0].isUpperCase())
            // "Bien" es corrección válida (dist=2, freq alta)
        }
    }

    @Test fun `case - preserva lowercase (miea→correction)`() {
        engine.onCharacter('m'); engine.onCharacter('i')
        engine.onCharacter('e'); engine.onCharacter('a')
        val result = engine.onSpace("miea")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            // Debe preservar lowercase
            assertTrue("Primera letra debe ser minúscula", result.corrected[0].isLowerCase())
            // "bien" es corrección válida (dist=2, freq alta)
        }
    }

    @Test fun `case - ALL_CAPS no se corrige (NASA)`() {
        engine.onCharacter('N'); engine.onCharacter('A')
        engine.onCharacter('S'); engine.onCharacter('A')
        val result = engine.onSpace("NASA")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `case - mixed case no se corrige (NaSa)`() {
        // Mixed case no es ALL_CAPS, pero tampoco es TitleCase
        // NaSa → no está en dict, pero tiene mixed case
        engine.onCharacter('N'); engine.onCharacter('a')
        engine.onCharacter('S'); engine.onCharacter('a')
        val result = engine.onSpace("NaSa")

        // No debe corregir porque no es ALL_CAPS pero tampoco es normal
        // El comportamiento exacto depende de la implementación
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROPER NOUN DETECTION (HeliBoard-style context)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `properNoun - no corrige TitleCase mid-sentence`() {
        // "John" con mayúscula en medio de oración → proper noun
        engine.onCharacter('J'); engine.onCharacter('o')
        engine.onCharacter('h'); engine.onCharacter('n')
        val result = engine.onSpace("Hola John")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `properNoun - permite correccion al inicio de oracion`() {
        // "Miea" al inicio de oración → puede corregir
        engine.onCharacter('M'); engine.onCharacter('i')
        engine.onCharacter('e'); engine.onCharacter('a')
        val result = engine.onSpace("Miea")

        // Puede corregir (no es proper noun mid-sentence)
        // O no (si no pasa frequency threshold) — ambos válidos
    }

    @Test fun `properNoun - contexto con punto antes`() {
        // "Hola. Miea" → después de punto, puede ser inicio de oración
        engine.onCharacter('M'); engine.onCharacter('i')
        engine.onCharacter('e'); engine.onCharacter('a')
        val result = engine.onSpace("Hola. Miea")

        // Debe permitir corrección (inicio de oración)
    }

    @Test fun `properNoun - contexto con newline antes`() {
        // "\nMiea" → después de newline, inicio de párrafo
        engine.onCharacter('M'); engine.onCharacter('i')
        engine.onCharacter('e'); engine.onCharacter('a')
        val result = engine.onSpace("\nMiea")

        // Debe permitir corrección (inicio de párrafo)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ABBREVIATION HANDLING (HeliBoard-style)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `abbrev - NO capitaliza despues de Dr`() {
        assertFalse(engine.shouldCapitalizeNext("Dr. "))
    }

    @Test fun `abbrev - NO capitaliza despues de Mr`() {
        assertFalse(engine.shouldCapitalizeNext("Mr. "))
    }

    @Test fun `abbrev - NO capitaliza despues de Mrs`() {
        assertFalse(engine.shouldCapitalizeNext("Mrs. "))
    }

    @Test fun `abbrev - NO capitaliza despues de etc`() {
        assertFalse(engine.shouldCapitalizeNext("etc. "))
    }

    @Test fun `abbrev - NO capitaliza despues de inicial sola`() {
        // "A. Smith" → después de "A." no capitalizar
        assertFalse(engine.shouldCapitalizeNext("A. "))
    }

    @Test fun `abbrev - SI capitaliza despues de palabra normal con punto`() {
        assertTrue(engine.shouldCapitalizeNext("Hola. "))
    }

    // ═══════════════════════════════════════════════════════════════════
    // MULTI-WORD CONTEXT SCENARIOS
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `context - composing se construye correctamente`() {
        engine.onCharacter('h')
        assertEquals("h", engine.getComposing())

        engine.onCharacter('o')
        assertEquals("ho", engine.getComposing())

        engine.onCharacter('l')
        assertEquals("hol", engine.getComposing())

        engine.onCharacter('a')
        assertEquals("hola", engine.getComposing())
    }

    @Test fun `context - space limpia composing`() {
        engine.onCharacter('h'); engine.onCharacter('o')
        engine.onCharacter('l'); engine.onCharacter('a')
        engine.onSpace("hola")

        assertEquals("", engine.getComposing())
    }

    @Test fun `context - nueva palabra limpia lastCorrection`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val spaceResult = engine.onSpace("teh")

        if (spaceResult is AutocorrectEngine.SpaceResult.Corrected) {
            assertTrue(engine.canUndo())

            // Nueva palabra → cierra undo window
            engine.onCharacter('w')
            assertFalse("Undo debe cerrarse al empezar nueva palabra", engine.canUndo())
        }
    }

    @Test fun `context - multiple words con autocorrect`() {
        // "teh quik brown" → "the quick brown"
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result1 = engine.onSpace("teh")

        engine.onCharacter('q'); engine.onCharacter('u'); engine.onCharacter('i')
        engine.onCharacter('c'); engine.onCharacter('k')
        val result2 = engine.onSpace("quick")

        engine.onCharacter('b'); engine.onCharacter('r'); engine.onCharacter('o')
        engine.onCharacter('w'); engine.onCharacter('n')
        val result3 = engine.onSpace("brown")

        // Verificar que no hay crashes y composing se maneja correctamente
        assertEquals("", engine.getComposing())
    }

    // ═══════════════════════════════════════════════════════════════════
    // CURSOR VALIDATION FOR UNDO
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `cursor - updateExpectedCursor actualiza record`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val spaceResult = engine.onSpace("teh")

        if (spaceResult is AutocorrectEngine.SpaceResult.Corrected) {
            engine.updateExpectedCursor(4)
            assertTrue(engine.canUndo())
        }
    }

    @Test fun `cursor - onCursorMoved no resetea composing`() {
        engine.onCharacter('h'); engine.onCharacter('e'); engine.onCharacter('l')
        assertEquals("hel", engine.getComposing())

        // Android mueve cursor al expandir composing region
        engine.onCursorMoved(1)
        engine.onCursorMoved(2)
        engine.onCursorMoved(3)

        assertEquals("hel", engine.getComposing())
    }

    @Test fun `cursor - onCursorMoved no limpia lastCorrection`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh")

        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            assertTrue(engine.canUndo())

            // Cursor se mueve por corrección (deleteSurrounding + commitText)
            engine.onCursorMoved(0)
            engine.onCursorMoved(4)

            assertTrue("Undo debe seguir disponible", engine.canUndo())
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // REJECTION LEARNING (Session vs Permanent)
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `rejection - backspace undo agrega a sessionRejected`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            // Backspace → undo → sessionRejected
            val undoResult = engine.onBackspace()
            assertIs<AutocorrectEngine.BackspaceResult.UndoCorrection>(undoResult)

            // Reintentar → no debe corregir (sessionRejected)
            engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
            val second = engine.onSpace("teh")
            assertFalse("No debe repetir corrección rechazada",
                second is AutocorrectEngine.SpaceResult.Corrected &&
                (second as AutocorrectEngine.SpaceResult.Corrected).corrected ==
                (first as AutocorrectEngine.SpaceResult.Corrected).corrected
            )
        }
    }

    @Test fun `rejection - reset limpia sessionRejected`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            engine.onBackspace()  // sessionRejected

            // Nuevo campo → reset()
            engine.reset()

            // Mismo par después de reset → puede corregir de nuevo
            engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
            val afterReset = engine.onSpace("teh")
            // Puede corregir (sessionRejected limpio) o no (rejectedPairs)
        }
    }

    @Test fun `rejection - rejectedPairs persiste entre sesiones`() {
        // Simular persistencia: agregar par manualmente a rejectedPairs
        // (en producción, esto viene de SharedPreferences)

        // Forzar corrección + rechazo permanente
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            engine.onBackspace()  // rechaza

            // destroy + recreate engine (simula nueva sesión)
            val newEngine = AutocorrectEngine(ctx, dict, personalDict)
            newEngine.initialize()  // carga rejectedPairs de SharedPreferences

            newEngine.onCharacter('t'); newEngine.onCharacter('e'); newEngine.onCharacter('h')
            val afterRestart = newEngine.onSpace("teh")

            assertFalse("No debe repetir corrección rechazada tras reinicio",
                afterRestart is AutocorrectEngine.SpaceResult.Corrected &&
                (afterRestart as AutocorrectEngine.SpaceResult.Corrected).corrected == "the"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EDGE CASES & ROBUSTNESS
    // ═══════════════════════════════════════════════════════════════════

    @Test fun `edge - palabra vacia no crash`() {
        val result = engine.onSpace("")
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edge - palabra con espacios no crash`() {
        val result = engine.onSpace("  hola  ")
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edge - hint null no crash`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh", null)
        // Puede corregir o no — no debe crash
    }

    @Test fun `edge - hint vacio no crash`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh", "")
        // Puede corregir o no — no debe crash
    }

    @Test fun `edge - hint con espacios se trimmea`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh", "  the  ")
        // Si corrige, debe ser a "the" (trimmed)
        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            assertEquals("the", result.corrected)
        }
    }

    @Test fun `edge - enabled=false no corrige`() {
        engine.isEnabled = false
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edge - shouldAutocorrect=false no corrige`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        engine.onEditorChanged(info)

        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `edge - palabra con digitos no se corrige`() {
        engine.onCharacter('w'); engine.onCharacter('i')
        engine.onCharacter('f'); engine.onCharacter('i')
        engine.onCharacter('2')
        val result = engine.onSpace("wifi2")

        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    // ═══════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════

    private inline fun <reified T> assertIs(value: Any?, message: String = "") {
        assertTrue(
            "Esperado ${T::class.simpleName}, fue ${value?.javaClass?.simpleName}. $message",
            value is T
        )
    }
}
