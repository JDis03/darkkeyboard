package org.dark.keyboard

import android.text.InputType
import android.util.Log
import android.view.inputmethod.EditorInfo

/**
 * Clasifica el campo de texto actual y determina qué features activar.
 *
 * Problema: diferentes apps/campos tienen distinto soporte para
 * composing text (setComposingText), autocorrect y sugerencias.
 * Un IME debe adaptar su comportamiento a cada contexto.
 *
 * Clasificación:
 *   TERMINAL     → SSH/RDP: sin composing, sin autocorrect, control chars
 *   DIRECT       → password, PIN, número, URL, email, filtro, search
 *   WEBVIEW      → WebView edit text: composing + autocorrect (usa composingWord, no getTextBeforeCursor)
 *   STANDARD     → mensajería, notas, docs: composing + autocorrect + sugerencias
 */
object AppInputProfile {

    private const val TAG = "InputProfile"

    enum class Mode {
        TERMINAL,   // SSH/RDP
        DIRECT,     // commitText directo, sin composing ni autocorrect
        WEBVIEW,    // composing con precaución
        STANDARD    // todo activado
    }

    data class Profile(
        val mode: Mode,
        val useComposing: Boolean,
        val useAutocorrect: Boolean,
        val useSuggestions: Boolean,
        val reason: String
    )

    // Paquetes de terminal conocidos
    private val TERMINAL_PACKAGES = setOf(
        "com.termux", "org.connectbot", "com.jcraft.jsch",
        "com.sonelli.juicessh", "net.schmizz.sshj",
        "com.github.damus_io", "org.dark.ssh", "com.blink.terminal",
        "net.xnano.android.sshclient", "de.mud.terminal",
        "com.iiordanov.bVNC", "com.freerdp", "org.dark.rdp",
        "com.darkssh.client", "com.darkrdp.client"
    )

    // Paquetes WebView conocidos (navegadores)
    private val WEBVIEW_PACKAGES = setOf(
        "com.android.chrome", "org.mozilla.firefox",
        "com.microsoft.emmx", "com.brave.browser",
        "com.opera.browser", "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android", "com.vivaldi.browser",
        "com.kiwibrowser.browser", "com.ecosia.android"
    )

    // Paquetes con composing problemático conocido
    private val NO_COMPOSING_PACKAGES = setOf(
        "com.discord",           // Discord a veces duplica texto con composing
        "com.google.android.apps.docs",  // Docs mobile tiene bugs con composing
    )

    fun classify(info: EditorInfo?): Profile {
        if (info == null) return standard("null EditorInfo")

        val pkg  = info.packageName ?: ""
        val type = info.inputType
        val cls  = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val flags = type and InputType.TYPE_MASK_FLAGS

        // ── 1. Terminal SSH/RDP ────────────────────────────────────────
        if (TERMINAL_PACKAGES.any { pkg.contains(it) }) {
            return Profile(Mode.TERMINAL, false, false, false, "terminal pkg=$pkg")
        }

        // ── 2. Campos sin composing por InputType ──────────────────────
        val isPassword = variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        if (isPassword) return direct("password field")

        if (cls == InputType.TYPE_CLASS_NUMBER ||
            cls == InputType.TYPE_CLASS_PHONE  ||
            cls == InputType.TYPE_CLASS_DATETIME) {
            return direct("numeric/phone/datetime class=$cls")
        }

        val isNoSuggestions = flags and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        val isAutoComplete  = flags and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE != 0
        val isBrowserPkg    = WEBVIEW_PACKAGES.any { pkg.contains(it) }

        // En browsers, FLAG_NO_SUGGESTIONS viene del HTML (autocomplete="off"),
        // no del usuario. Lo ignoramos para poder ofrecer correcciones.
        if (isNoSuggestions && !isBrowserPkg) return direct("FLAG_NO_SUGGESTIONS")
        if (isAutoComplete  && !isBrowserPkg) return direct("FLAG_AUTO_COMPLETE")

        val isUri   = variation == InputType.TYPE_TEXT_VARIATION_URI
        val isEmail = variation in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )
        val isFilter = variation == InputType.TYPE_TEXT_VARIATION_FILTER

        if (isUri)    return direct("URI field")
        if (isEmail)  return direct("email field")
        if (isFilter) return direct("filter field")

        // ── 3. Paquetes con composing problemático ─────────────────────
        if (NO_COMPOSING_PACKAGES.any { pkg.contains(it) }) {
            return Profile(Mode.DIRECT, false, true, true, "no-composing pkg=$pkg")
        }

        // ── 4. WebView ─────────────────────────────────────────────────
        val isWebView = variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT

        if (isWebView || isBrowserPkg) {
            // Composing + autocorrect: usamos composingWord como fuente primaria,
            // NO getTextBeforeCursor (que es poco fiable en WebView).
            // La corrección en onSpace() usa composingWord.ifEmpty { textBefore }
            // así que WebView es seguro siempre que haya composing activo.
            return Profile(Mode.WEBVIEW, true, true, true, "webview var=$variation pkg=$pkg")
        }

        // ── 5. Campos de persona/dirección → sin autocorrect ──────────
        val isPersonOrAddress = variation in setOf(
            InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
        )
        if (isPersonOrAddress) {
            return Profile(Mode.STANDARD, true, false, true, "person/address field")
        }

        // ── 6. Standard: mensajería, docs, notas ─────────────────────
        return standard("pkg=$pkg variation=$variation")
    }

    private fun standard(reason: String) =
        Profile(Mode.STANDARD, true, true, true, reason).also {
            Log.d(TAG, "STANDARD: $reason")
        }

    private fun direct(reason: String) =
        Profile(Mode.DIRECT, false, false, false, reason).also {
            Log.d(TAG, "DIRECT: $reason")
        }
}
