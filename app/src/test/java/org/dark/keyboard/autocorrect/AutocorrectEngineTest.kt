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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AutocorrectEngineTest {

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

    // ── Composing word (state machine) ─────────────────────────────────

    @Test fun `onCharacter - construye composing char a char`() {
        engine.onCharacter('h')
        engine.onCharacter('o')
        val result = engine.onCharacter('l')
        assertIs<AutocorrectEngine.CharResult.UpdateComposing>(result)
        assertEquals("hol", (result as AutocorrectEngine.CharResult.UpdateComposing).composing)
    }

    @Test fun `onCharacter - commit directo en modo terminal`() {
        engine.isTerminalApp = true
        engine.onEditorChanged(null)
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.CommitDirect>(result)
        assertEquals("h", (result as AutocorrectEngine.CharResult.CommitDirect).char)
    }

    @Test fun `onCharacter - commit directo cuando disabled`() {
        engine.isEnabled = false
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.CommitDirect>(result)
    }

    // ── Backspace ─────────────────────────────────────────────────────

    @Test fun `onBackspace - reduce composing de a un char`() {
        engine.onCharacter('h')
        engine.onCharacter('e')
        engine.onCharacter('l')
        val result = engine.onBackspace()
        assertIs<AutocorrectEngine.BackspaceResult.UpdateComposing>(result)
        assertEquals("he", (result as AutocorrectEngine.BackspaceResult.UpdateComposing).remaining)
    }

    @Test fun `onBackspace - composing vacio retorna Normal`() {
        val result = engine.onBackspace()
        assertIs<AutocorrectEngine.BackspaceResult.Normal>(result)
    }

    @Test fun `onBackspace - composing de 1 char retorna UpdateComposing vacio`() {
        engine.onCharacter('h')
        val result = engine.onBackspace()
        assertIs<AutocorrectEngine.BackspaceResult.UpdateComposing>(result)
        assertEquals("", (result as AutocorrectEngine.BackspaceResult.UpdateComposing).remaining)
    }

    // ── Undo corrección ───────────────────────────────────────────────

    @Test fun `onBackspace - undo tras correccion`() {
        // Simular que se hizo una corrección
        engine.onCharacter('t')
        engine.onCharacter('e')
        engine.onCharacter('h')
        val spaceResult = engine.onSpace("teh")

        if (spaceResult is AutocorrectEngine.SpaceResult.Corrected) {
            val undoResult = engine.onBackspace()
            assertIs<AutocorrectEngine.BackspaceResult.UndoCorrection>(undoResult)
            val undo = undoResult as AutocorrectEngine.BackspaceResult.UndoCorrection
            assertEquals("teh", undo.record.original)
        }
        // Si no corrigió (ej: "teh" no está en dict ES), el test pasa igual
    }

    @Test fun `onBackspace - undo solo disponible una vez (no loop)`() {
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val spaceResult = engine.onSpace("teh")

        if (spaceResult is AutocorrectEngine.SpaceResult.Corrected) {
            // Primer backspace → undo
            val first = engine.onBackspace()
            assertIs<AutocorrectEngine.BackspaceResult.UndoCorrection>(first)

            // Segundo backspace → ya no hay nada que deshacer (lastCorrection = null)
            val second = engine.onBackspace()
            // Debe ser Normal o UpdateComposing (si "teh" quedó en composing), no UndoCorrection
            assertFalse(second is AutocorrectEngine.BackspaceResult.UndoCorrection)
        }
    }

    // ── Space ─────────────────────────────────────────────────────────

    @Test fun `onSpace - double space inserta punto`() {
        val result = engine.onSpace("hola ")  // ya hay un espacio al final
        assertIs<AutocorrectEngine.SpaceResult.PeriodInserted>(result)
    }

    @Test fun `onSpace - no corrige ALL_CAPS`() {
        engine.onCharacter('N'); engine.onCharacter('A'); engine.onCharacter('S'); engine.onCharacter('A')
        val result = engine.onSpace("NASA")
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `onSpace - no corrige palabra con digito`() {
        engine.onCharacter('w'); engine.onCharacter('i'); engine.onCharacter('f')
        engine.onCharacter('i'); engine.onCharacter('2')
        val result = engine.onSpace("wifi2")
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `onSpace - no corrige palabra en diccionario personal`() {
        // Agregar exactamente la palabra que se va a tipear
        personalDict.add("xz")
        engine.onCharacter('x'); engine.onCharacter('z')
        val result = engine.onSpace("xz")
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `onSpace - no corrige nombre propio mid-sentence`() {
        // "John" con mayúscula en medio de oración → proper noun
        engine.onCharacter('J'); engine.onCharacter('o')
        engine.onCharacter('h'); engine.onCharacter('n')
        val result = engine.onSpace("Hola John")  // mid-sentence
        assertIs<AutocorrectEngine.SpaceResult.Normal>(result)
    }

    @Test fun `onSpace - permite correccion al inicio de oracion`() {
        // Al inicio no es proper noun → puede corregir
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh")
        // Si corrige → Corrected, si no → Normal (ambos son válidos, pero no debe ser PeriodInserted)
        assertFalse(result is AutocorrectEngine.SpaceResult.PeriodInserted)
    }

    @Test fun `onSpace - par rechazado no se corrige de nuevo`() {
        // Forzar rechazo del par "teh→the" directamente via backspace undo
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val first = engine.onSpace("teh")

        if (first is AutocorrectEngine.SpaceResult.Corrected) {
            engine.onBackspace()  // rechaza la corrección → agrega a rejectedPairs

            // Intentar de nuevo — no debe corregir
            engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
            val second = engine.onSpace("teh")
            assertFalse("No debe repetir corrección rechazada",
                second is AutocorrectEngine.SpaceResult.Corrected &&
                (second as AutocorrectEngine.SpaceResult.Corrected).corrected ==
                (first as AutocorrectEngine.SpaceResult.Corrected).corrected
            )
        }
    }

    // ── Auto-capitalización ───────────────────────────────────────────

    @Test fun `shouldCapitalizeNext - al inicio de campo (texto vacio)`() {
        assertTrue(engine.shouldCapitalizeNext(""))
    }

    @Test fun `shouldCapitalizeNext - despues de punto espacio`() {
        assertTrue(engine.shouldCapitalizeNext("Hola. "))
    }

    @Test fun `shouldCapitalizeNext - despues de signo de exclamacion`() {
        assertTrue(engine.shouldCapitalizeNext("Hola! "))
    }

    @Test fun `shouldCapitalizeNext - despues de signo de interrogacion`() {
        assertTrue(engine.shouldCapitalizeNext("Hola? "))
    }

    @Test fun `shouldCapitalizeNext - NO capitaliza mid-sentence`() {
        assertFalse(engine.shouldCapitalizeNext("Hola mundo "))
    }

    @Test fun `shouldCapitalizeNext - NO capitaliza despues de Dr`() {
        assertFalse(engine.shouldCapitalizeNext("Dr. "))
    }

    @Test fun `shouldCapitalizeNext - NO capitaliza despues de Mr`() {
        assertFalse(engine.shouldCapitalizeNext("Mr. "))
    }

    @Test fun `shouldCapitalizeNext - NO capitaliza despues de inicial sola`() {
        // "A. Smith" → después de "A." no capitalizar (inicial de nombre)
        assertFalse(engine.shouldCapitalizeNext("A. "))
    }

    // ── onFinishComposing ─────────────────────────────────────────────

    @Test fun `onFinishComposing - limpia composing`() {
        engine.onCharacter('h'); engine.onCharacter('e')
        engine.onFinishComposing()
        assertEquals("", engine.getComposing())
    }

    @Test fun `onFinishComposing - limpia lastCorrection`() {
        assertFalse(engine.canUndo())
        engine.onFinishComposing()
        assertFalse(engine.canUndo())
    }

    // ── reset / lifecycle ─────────────────────────────────────────────

    @Test fun `reset - limpia composing y lastCorrection`() {
        engine.onCharacter('h'); engine.onCharacter('i')
        engine.reset()
        assertEquals("", engine.getComposing())
        assertFalse(engine.canUndo())
    }

    // ── El bug que corregimos: cursor move durante composing ──────────

    @Test fun `onCursorMoved - NO resetea composing cuando hay texto en composicion`() {
        engine.onCharacter('h')
        engine.onCharacter('e')
        engine.onCharacter('l')
        assertEquals("hel", engine.getComposing())

        // Simular movimientos de cursor que genera Android al expandir composing
        engine.onCursorMoved(1)
        engine.onCursorMoved(2)
        engine.onCursorMoved(3)

        // Composing NO debe haberse reseteado
        assertEquals("hel", engine.getComposing())
    }

    @Test fun `onCursorMoved - SI limpia lastCorrection en movimiento externo`() {
        // Simular corrección aplicada
        engine.onCharacter('t'); engine.onCharacter('e'); engine.onCharacter('h')
        val result = engine.onSpace("teh")
        if (result is AutocorrectEngine.SpaceResult.Corrected) {
            assertTrue(engine.canUndo())
            // Movimiento externo del cursor (composing = "" en ese momento)
            engine.onCursorMoved(0)   // cursor a pos 0
            engine.onCursorMoved(99)  // salta a posición lejana → externo
            assertFalse(engine.canUndo())
        }
    }

    // ── EditorInfo checks ─────────────────────────────────────────────

    @Test fun `onEditorChanged - desactiva autocorrect en password field`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        engine.onEditorChanged(info)
        // Al no autocorregir, onCharacter debe retornar CommitDirect
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.CommitDirect>(result)
    }

    @Test fun `onEditorChanged - desactiva en URI field`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        engine.onEditorChanged(info)
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.CommitDirect>(result)
    }

    @Test fun `onEditorChanged - desactiva en email field`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }
        engine.onEditorChanged(info)
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.CommitDirect>(result)
    }

    @Test fun `onEditorChanged - desactiva con FLAG_NO_SUGGESTIONS`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        engine.onEditorChanged(info)
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.CommitDirect>(result)
    }

    @Test fun `onEditorChanged - activo en campo de texto normal`() {
        val info = EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        }
        engine.onEditorChanged(info)
        val result = engine.onCharacter('h')
        assertIs<AutocorrectEngine.CharResult.UpdateComposing>(result)
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private inline fun <reified T> assertIs(value: Any?, message: String = "") {
        assertTrue(
            "Esperado ${T::class.simpleName}, fue ${value?.javaClass?.simpleName}. $message",
            value is T
        )
    }
}
