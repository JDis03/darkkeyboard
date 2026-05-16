package org.dark.keyboard

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppInputProfileTest {

    private fun editor(
        pkg: String = "com.example.app",
        inputType: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    ) = EditorInfo().apply {
        packageName = this@AppInputProfileTest.run { pkg }
        this.inputType = inputType
    }

    // ── Terminal ──────────────────────────────────────────────────────

    @Test fun `termux es TERMINAL`() {
        val p = AppInputProfile.classify(editor(pkg = "com.termux"))
        assertEquals(AppInputProfile.Mode.TERMINAL, p.mode)
        assertFalse(p.useComposing)
        assertFalse(p.useAutocorrect)
    }

    @Test fun `connectbot es TERMINAL`() {
        val p = AppInputProfile.classify(editor(pkg = "org.connectbot"))
        assertEquals(AppInputProfile.Mode.TERMINAL, p.mode)
    }

    @Test fun `juicessh es TERMINAL`() {
        val p = AppInputProfile.classify(editor(pkg = "com.sonelli.juicessh"))
        assertEquals(AppInputProfile.Mode.TERMINAL, p.mode)
    }

    // ── Direct (sin composing ni autocorrect) ─────────────────────────

    @Test fun `password field es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
        assertFalse(p.useComposing)
        assertFalse(p.useAutocorrect)
    }

    @Test fun `visible password es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    @Test fun `URI field es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
        assertFalse(p.useAutocorrect)
    }

    @Test fun `email field es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    @Test fun `NUMBER class es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType = InputType.TYPE_CLASS_NUMBER))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    @Test fun `PHONE class es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType = InputType.TYPE_CLASS_PHONE))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    @Test fun `FLAG_NO_SUGGESTIONS es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    @Test fun `FLAG_AUTO_COMPLETE es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    @Test fun `filter field es DIRECT`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER))
        assertEquals(AppInputProfile.Mode.DIRECT, p.mode)
    }

    // ── WebView ───────────────────────────────────────────────────────

    @Test fun `WEB_EDIT_TEXT es WEBVIEW`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT))
        assertEquals(AppInputProfile.Mode.WEBVIEW, p.mode)
        assertTrue(p.useComposing)
        assertFalse(p.useAutocorrect)   // precaución en WebView
        assertTrue(p.useSuggestions)
    }

    @Test fun `Chrome pkg es WEBVIEW`() {
        val p = AppInputProfile.classify(editor(pkg = "com.android.chrome"))
        assertEquals(AppInputProfile.Mode.WEBVIEW, p.mode)
    }

    @Test fun `Firefox pkg es WEBVIEW`() {
        val p = AppInputProfile.classify(editor(pkg = "org.mozilla.firefox"))
        assertEquals(AppInputProfile.Mode.WEBVIEW, p.mode)
    }

    // ── Standard ──────────────────────────────────────────────────────

    @Test fun `campo normal es STANDARD`() {
        val p = AppInputProfile.classify(editor())
        assertEquals(AppInputProfile.Mode.STANDARD, p.mode)
        assertTrue(p.useComposing)
        assertTrue(p.useAutocorrect)
        assertTrue(p.useSuggestions)
    }

    @Test fun `WhatsApp texto normal es STANDARD`() {
        val p = AppInputProfile.classify(editor(
            pkg = "com.whatsapp",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
        ))
        assertEquals(AppInputProfile.Mode.STANDARD, p.mode)
    }

    @Test fun `Telegram texto normal es STANDARD`() {
        val p = AppInputProfile.classify(editor(
            pkg = "org.telegram.messenger",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        ))
        assertEquals(AppInputProfile.Mode.STANDARD, p.mode)
    }

    @Test fun `null EditorInfo es STANDARD por defecto`() {
        val p = AppInputProfile.classify(null)
        assertEquals(AppInputProfile.Mode.STANDARD, p.mode)
    }

    // ── Persona / dirección → sin autocorrect ────────────────────────

    @Test fun `PERSON_NAME sin autocorrect`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME))
        assertFalse("No debe autocorregir nombres propios", p.useAutocorrect)
        assertTrue(p.useComposing)
        assertTrue(p.useSuggestions)
    }

    @Test fun `POSTAL_ADDRESS sin autocorrect`() {
        val p = AppInputProfile.classify(editor(inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS))
        assertFalse(p.useAutocorrect)
    }
}
