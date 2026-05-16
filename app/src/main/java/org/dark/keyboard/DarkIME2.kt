package org.dark.keyboard

import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dark.keyboard.autocorrect.AutocorrectEngine
import org.dark.keyboard.autocorrect.PersonalDictionary
import org.dark.keyboard.autocorrect.WordDictionary
import org.dark.keyboard.suggestions.DictSuggestionEngine
import org.dark.keyboard.suggestions.FallbackSuggestionEngine
import org.dark.keyboard.suggestions.SuggestionEngine

/**
 * InputMethodService con sugerencias + autocorrección nivel 3.
 */
class DarkIME2 : InputMethodService() {
    
    private var keyboardView: SimpleKeyboardView? = null
    private var modifierStatusView: TextView? = null
    private var suggestionBarView: SuggestionBarView? = null
    private var clipboardPopup: ClipboardPopup? = null
    private var isSymbolsMode = false

    // Handler para limpiar sugerencias cuando el usuario deja de escribir
    private val mainHandler = Handler(Looper.getMainLooper())
    private val clearSuggestionsRunnable = Runnable {
        suggestionBarView?.clearSuggestions()
    }
    private val CLEAR_DELAY_MS    = 3000L  // 3s sin escribir → limpiar
    private val DEBOUNCE_MS       = 80L    // esperar 80ms antes de inferir
    private val MIN_TEXT_LENGTH   = 2      // mínimo 2 chars antes de sugerir

    // Dispatcher single-thread para TFLite (evita race condition XNNPACK)
    private val inferDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Job de inferencia activo — cancelar antes de lanzar uno nuevo
    private var suggestionJob: Job? = null
    private lateinit var prefs: SharedPreferences

    // Motor de sugerencias — TFLite si hay modelo, Fallback si no
    private lateinit var suggestionEngine: SuggestionEngine
    private val fallbackEngine = FallbackSuggestionEngine()
    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Motor de autocorrección nivel 3
    private lateinit var wordDict: WordDictionary
    private lateinit var personalDict: PersonalDictionary
    private lateinit var autocorrect: AutocorrectEngine
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "keyboard_layout", "show_number_row", "custom_layout_name" -> {
                Log.i(TAG, "Preference '$key' changed, reloading keyboard...")
                reloadKeyboard()
            }
            "keyboard_theme", "show_modifier_status" -> {
                Log.i(TAG, "Preference '$key' changed, applying...")
                applyTheme()
                updateModifierStatus(
                    keyboardView?.isShiftActive() ?: false,
                    keyboardView?.isCtrlActive() ?: false,
                    keyboardView?.isAltActive() ?: false,
                    keyboardView?.isFnActive() ?: false
                )
            }
        }
    }
    

    
    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.e(TAG, "=== onCreate() CALLED ===")

        // Inicializar autocorrect + diccionario en background
        wordDict     = WordDictionary(this)
        personalDict = PersonalDictionary(this)
        autocorrect  = AutocorrectEngine(this, wordDict, personalDict)
        autocorrect.initialize()

        // Inicializar motor de sugerencias multi-idioma en background
        imeScope.launch(Dispatchers.IO) {
            val engine = DictSuggestionEngine(this@DarkIME2)
            val savedLang = prefs.getString("suggestion_language", "es") ?: "es"
            engine.switchLanguage(savedLang)
            engine.initialize()
            suggestionEngine = engine
            // Compartir el mismo diccionario con autocorrect para eficiencia
            wordDict.load(savedLang)
            autocorrect.isTerminalApp = false
            val label = DictSuggestionEngine.LANGUAGE_NAMES[savedLang] ?: savedLang.uppercase()
            launch(Dispatchers.Main) {
                suggestionBarView?.setLanguageLabel(label)
            }
            Log.i(TAG, "Suggestion engine: ${suggestionEngine.engineName}")
        }
    }
    
     override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Clasificar el campo actual y ajustar comportamiento
        val profile = AppInputProfile.classify(attribute)
        isTerminalMode = profile.mode == AppInputProfile.Mode.TERMINAL
        Log.i(TAG, "=== onStartInput() mode=${profile.mode} reason=${profile.reason} inputType=${attribute?.inputType} ===")

        // Autocorrect: reset ghost-composing + ajustar según perfil
        autocorrect.isTerminalApp = isTerminalMode
        autocorrect.isEnabled = profile.useAutocorrect
        autocorrect.reset()
        autocorrect.onEditorChanged(attribute)

        keyboardView?.modifierState?.clearAll()
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        suggestionBarView?.clearSuggestions()

        if (!isSymbolsMode) {
            reloadKeyboard()
        }
        applyTheme()
    }

    companion object {
        private const val TAG = "DarkIME2"
        private const val KEYCODE_DELETE = -5
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_ENTER = 10

        // Packages conocidos de terminales SSH que necesitan bytes de control
        private val TERMINAL_PACKAGES = listOf(
            "com.darkssh.client",   // DarkSSH
            "org.connectbot",        // ConnectBot
            "com.sonelli.juicessh", // JuiceSSH
            "com.server.auditor.ssh.client", // Termius
            "com.blink.terminal",   // Blink
            "net.xnano.android.sshclient" // SSH Client
        )
    }
    
    override fun onEvaluateFullscreenMode(): Boolean {
        val result = super.onEvaluateFullscreenMode()
        Log.e(TAG, "=== onEvaluateFullscreenMode() = $result ===")
        return false  // Force non-fullscreen mode
    }
    
    private var inputViewContainer: View? = null
    // true = app es terminal SSH (usa commitText para control chars)
    // false = app normal/RDP (usa sendKeyEvent con metaState)
    private var isTerminalMode = false
    
    override fun onCreateInputView(): View? {
        Log.e(TAG, "=== onCreateInputView CALLED ===")
        
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        inputViewContainer = layout
        keyboardView = layout.findViewById(R.id.keyboard)
        modifierStatusView = layout.findViewById(R.id.modifier_status)
        suggestionBarView = layout.findViewById(R.id.suggestion_bar)

        clipboardPopup = ClipboardPopup(this) { text ->
            currentInputConnection?.commitText(text, 1)
        }

        suggestionBarView?.listener = object : SuggestionBarView.Listener {
            override fun onSuggestionClick(text: String) {
                val ic = currentInputConnection ?: return

                // Determinar la palabra a reemplazar.
                // Prioridad: composing activo (más confiable que getTextBeforeCursor)
                val composing = autocorrect.getComposing()
                val partial: String

                if (composing.isNotEmpty()) {
                    // Reemplazar composing directamente — WebView-safe (no deleteSurroundingText)
                    autocorrect.onFinishComposing()
                    ic.setComposingText(text, 1)
                    ic.finishComposingText()
                    ic.commitText(" ", 1)
                    if (::suggestionEngine.isInitialized) {
                        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                        suggestionEngine.onSuggestionAccepted(text, before)
                    }
                    updateSuggestions()
                    return
                } else {
                    // Sin composing — obtener última palabra del texto
                    val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                    partial = if (!before.endsWith(" "))
                        before.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
                    else ""
                    // Siempre borrar la palabra parcial, sin importar si la sugerencia
                    // la "extiende" o la "corrige" (ej: "teh"→"the" no es prefijo)
                    if (partial.isNotEmpty()) {
                        ic.deleteSurroundingText(partial.length, 0)
                    }
                }

                // Preservar case del usuario
                val toInsert = if (partial.isNotEmpty() && partial[0].isLowerCase() && text[0].isUpperCase()) {
                    text.replaceFirstChar { it.lowercase() }
                } else text

                ic.commitText("$toInsert ", 1)

                // Aprender del usuario
                if (::suggestionEngine.isInitialized) {
                    val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                    suggestionEngine.onSuggestionAccepted(toInsert, before)
                }

                updateSuggestions()
            }
            override fun onClipboardClick() {
                suggestionBarView?.let { clipboardPopup?.show(it) }
            }
            override fun onSettingsClick() {
                val intent = Intent(this@DarkIME2, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            override fun onLanguageClick() {
                val engine = suggestionEngine as? DictSuggestionEngine ?: return
                val next = engine.nextLanguage()
                imeScope.launch(Dispatchers.IO) {
                    engine.switchLanguage(next)
                    prefs.edit().putString("suggestion_language", next).apply()
                    val label = DictSuggestionEngine.LANGUAGE_NAMES[next] ?: next.uppercase()
                    launch(Dispatchers.Main) {
                        suggestionBarView?.setLanguageLabel(label)
                        suggestionBarView?.clearSuggestions()
                    }
                }
            }
        }

        loadAndSetKeyboard()
        applyTheme()
        keyboardView?.onKeyListener = object : SimpleKeyboardView.OnKeyListener {
            override fun onKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
                handleKey(code, shift, ctrl, alt, fn)
            }
            override fun onText(text: CharSequence) {
                currentInputConnection?.commitText(text, 1)
            }
        }

        observeModifierFlows()
        
        Log.i(TAG, "Keyboard created")
        
        return layout
    }
    
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // Let Android handle insets automatically
        Log.d(TAG, "onComputeInsets: contentTop=${outInsets.contentTopInsets}, visibleTop=${outInsets.visibleTopInsets}")
    }
    
    private fun handleKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val ic = currentInputConnection ?: return

        var metaState = 0
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON

        val charLabel = if (code > 0 && code < 128) code.toChar().toString() else "?"
        Log.i(TAG, ">>> handleKey: code=$code ($charLabel), shift=$shift, ctrl=$ctrl, alt=$alt, fn=$fn, metaState=$metaState")

        when (code) {

            // ── Backspace ─────────────────────────────────────────────────
            KEYCODE_DELETE -> {
                if (ctrl) {
                    autocorrect.onFinishComposing()
                    ic.finishComposingText()
                    deleteWord(ic)
                } else {
                    when (val result = autocorrect.onBackspace()) {
                        is AutocorrectEngine.BackspaceResult.UndoCorrection -> {
                            val r = result.record
                            ic.finishComposingText()
                            if (ic.deleteSurroundingText(r.corrected.length + 1, 0)) {
                                ic.setComposingText(r.original, 1)
                                Log.i(TAG, "Undo autocorrect: '${r.corrected}' → '${r.original}'")
                            }
                        }
                        is AutocorrectEngine.BackspaceResult.UpdateComposing -> {
                            if (result.remaining.isEmpty()) ic.commitText("", 1)
                            else ic.setComposingText(result.remaining, 1)
                        }
                        AutocorrectEngine.BackspaceResult.Normal -> {
                            sendSimpleKeyEvent(KeyEvent.KEYCODE_DEL)
                        }
                    }
                }
            }

            // ── Space ─────────────────────────────────────────────────────
            ' '.code -> {
                val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val topSuggestion = suggestionBarView?.getTopSuggestion()
                // Guardar composing ANTES de onSpace() — lo limpia internamente
                val composingSnapshot = autocorrect.getComposing()

                when (val result = autocorrect.onSpace(textBefore, topSuggestion)) {
                    is AutocorrectEngine.SpaceResult.Corrected -> {
                        if (composingSnapshot.isNotEmpty()) {
                            // Camino WebView-safe: reemplazar la región de composing
                            // directamente con setComposingText → finishComposingText.
                            // Evita deleteSurroundingText que falla en Chromium/WebViews.
                            ic.setComposingText(result.corrected, 1)
                            ic.finishComposingText()
                            ic.commitText(" ", 1)
                        } else {
                            // Sin composing: camino estándar (deleteSurrounding + commit)
                            ic.finishComposingText()
                            ic.deleteSurroundingText(result.original.length, 0)
                            ic.commitText("${result.corrected} ", 1)
                        }
                        Log.i(TAG, "Autocorrect: '${result.original}' → '${result.corrected}'")
                    }
                    AutocorrectEngine.SpaceResult.PeriodInserted -> {
                        ic.finishComposingText()
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                    }
                    AutocorrectEngine.SpaceResult.Normal -> {
                        ic.finishComposingText()
                        ic.commitText(" ", 1)
                    }
                }
                updateSuggestions()
            }

            // ── Teclas que finalizan composing ────────────────────────────
            KEYCODE_SHIFT -> { }
            Key.CODE_CTRL_LEFT -> { }
            Key.CODE_ALT_LEFT -> { }
            Key.CODE_MODE_CHANGE -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                switchLayout()
            }
            KEYCODE_ENTER -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                learnFromCurrentText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_ENTER, shift, ctrl, alt, fn)
            }
            Key.CODE_TAB -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB, shift, ctrl, alt, fn)
            }
            Key.CODE_ESCAPE -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_ESCAPE, shift, ctrl, alt, fn)
            }
            Key.CODE_FORWARD_DEL -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_FORWARD_DEL, shift, ctrl, alt, fn)
            }
            Key.CODE_HOME -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_MOVE_HOME, shift, ctrl, alt, fn)
            }
            Key.CODE_END -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_MOVE_END, shift, ctrl, alt, fn)
            }
            Key.CODE_PAGE_UP -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_PAGE_UP, shift, ctrl, alt, fn)
            }
            Key.CODE_PAGE_DOWN -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_PAGE_DOWN, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_UP -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_UP, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_DOWN -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_DOWN, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_LEFT -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_RIGHT -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, shift, ctrl, alt, fn)
            }
            Key.CODE_CLOSE -> { requestHideSelf(0) }
            Key.CODE_SWITCH_INPUT -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
            }
            in SimpleKeyboardView.KEYCODE_FKEY_F1..SimpleKeyboardView.KEYCODE_FKEY_F12 -> {
                autocorrect.onFinishComposing(); ic.finishComposingText()
                val fKeyNumber = SimpleKeyboardView.KEYCODE_FKEY_F1 - code + 1
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_F1 + (fKeyNumber - 1), shift, ctrl, alt, fn)
            }

            // ── Caracteres ────────────────────────────────────────────────
            else -> {
                if (code > 0 && code < 127) {
                    if (isTerminalMode && ctrl && !alt && shift && code == 'v'.code) {
                        val clip = (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                            .primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clip.isNullOrEmpty()) ic.commitText(clip, 1)
                    } else if (ctrl && !alt && !shift && code in 'a'.code..'z'.code && isTerminalMode) {
                        val ctrlByte = (code - 'a'.code + 1).toChar().toString()
                        ic.commitText(ctrlByte, 1)
                    } else if (ctrl || alt) {
                        autocorrect.onFinishComposing(); ic.finishComposingText()
                        val keycode = when (code.toChar().lowercaseChar()) {
                            in 'a'..'z' -> KeyEvent.KEYCODE_A + (code.toChar().lowercaseChar() - 'a')
                            ' ' -> KeyEvent.KEYCODE_SPACE
                            else -> {
                                var char = code.toChar().toString()
                                if (shift && code in 'a'.code..'z'.code) char = char.uppercase()
                                ic.commitText(char, 1); return
                            }
                        }
                        sendModifiedKeyDownUp(keycode, shift, ctrl, alt, fn)
                    } else {
                        val c = code.toChar()
                        if (c.isLetter()) {
                            var char = c.toString()
                            if (shift) char = char.uppercase()
                            // Auto-capitalización
                            if (!shift) {
                                val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                                if (autocorrect.shouldCapitalizeNext(textBefore)) char = char.uppercase()
                            }
                            when (val res = autocorrect.onCharacter(char[0])) {
                                is AutocorrectEngine.CharResult.UpdateComposing ->
                                    ic.setComposingText(res.composing, 1)
                                is AutocorrectEngine.CharResult.CommitDirect ->
                                    ic.commitText(res.char, 1)
                            }
                        } else {
                            // Puntuación — finaliza composing
                            autocorrect.onFinishComposing(); ic.finishComposingText()
                            var char = c.toString()
                            if (shift && code in 'a'.code..'z'.code) char = char.uppercase()
                            ic.commitText(char, 1)
                        }
                        updateSuggestions()
                    }
                }
            }
        }
    }

    private fun sendModifiedKeyDownUp(key: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val ic = currentInputConnection ?: return
        val eventTime = System.currentTimeMillis()

        val meta = buildMetaState(shift, ctrl, alt, fn)

        try {
            sendModifierDown(ic, eventTime, shift, KeyEvent.KEYCODE_SHIFT_LEFT)
            sendModifierDown(ic, eventTime, ctrl, KeyEvent.KEYCODE_CTRL_LEFT)
            sendModifierDown(ic, eventTime, alt, KeyEvent.KEYCODE_ALT_LEFT)

            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, key, 0, meta))
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, key, 0, meta))

            sendModifierUp(ic, eventTime, alt, KeyEvent.KEYCODE_ALT_LEFT)
            sendModifierUp(ic, eventTime, ctrl, KeyEvent.KEYCODE_CTRL_LEFT)
            sendModifierUp(ic, eventTime, shift, KeyEvent.KEYCODE_SHIFT_LEFT)
        } catch (e: Exception) {
            Log.w(TAG, "sendModifiedKeyDownUp failed: ${e.message}")
        }
    }

    private fun sendSimpleKeyEvent(key: Int) {
        val ic = currentInputConnection ?: return
        try {
            val now = System.currentTimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, 0))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, 0))
        } catch (e: Exception) {
            Log.w(TAG, "sendSimpleKeyEvent failed: ${e.message}")
        }
    }

    private fun sendModifierDown(ic: android.view.inputmethod.InputConnection, eventTime: Long, active: Boolean, keycode: Int) {
        if (!active) return
        try {
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keycode, 0, 0))
        } catch (e: Exception) {
            Log.w(TAG, "sendModifierDown failed: ${e.message}")
        }
    }

    private fun sendModifierUp(ic: android.view.inputmethod.InputConnection, eventTime: Long, active: Boolean, keycode: Int) {
        if (!active) return
        try {
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keycode, 0, 0))
        } catch (e: Exception) {
            Log.w(TAG, "sendModifierUp failed: ${e.message}")
        }
    }

    private fun buildMetaState(shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean): Int {
        var m = 0
        if (shift) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (ctrl) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        return m
    }
    
    private fun deleteWord(ic: android.view.inputmethod.InputConnection) {
        // Delete word to the left of cursor
        val textBeforeCursor = ic.getTextBeforeCursor(100, 0) ?: return
        var deleteCount = 0
        var foundNonSpace = false
        
        for (i in textBeforeCursor.length - 1 downTo 0) {
            val c = textBeforeCursor[i]
            if (c.isWhitespace()) {
                if (foundNonSpace) break
            } else {
                foundNonSpace = true
            }
            deleteCount++
        }
        
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
        }
    }
    
    private fun switchLayout() {
        isSymbolsMode = !isSymbolsMode
        loadAndSetKeyboard()
        Log.i(TAG, "Switched layout to ${if (isSymbolsMode) "symbols" else "alphabet"}")
    }
    
    private fun updateModifierStatus(shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val statusParts = mutableListOf<String>()
        if (ctrl) statusParts.add("Ctrl")
        if (shift) statusParts.add("Shift")
        if (alt) statusParts.add("Alt")
        if (fn) statusParts.add("Fn")
        
        val showStatus = prefs.getBoolean("show_modifier_status", true)
        if (statusParts.isNotEmpty() && showStatus) {
            modifierStatusView?.text = "[ ${statusParts.joinToString(" + ")} ]"
            modifierStatusView?.visibility = View.VISIBLE
            Log.i(TAG, "Modifiers active: ${statusParts.joinToString(" + ")}")
        } else {
            modifierStatusView?.visibility = View.GONE
        }
    }
    
    private fun getLayoutResourceId(): Int {
        val layout = prefs.getString("keyboard_layout", "pc")
        Log.i(TAG, ">>> getLayoutResourceId: layout preference = '$layout'")
        val resourceId = when (layout) {
            "pc" -> R.xml.kbd_pc
            "compact" -> R.xml.kbd_compact
            else -> R.xml.kbd_pc  // Default to QWERTY Standard
        }
        Log.i(TAG, ">>> returning resource ID: ${if (resourceId == R.xml.kbd_pc) "kbd_pc" else "kbd_compact"}")
        return resourceId
    }

    private fun applyTheme() {
        val themeName = prefs.getString("keyboard_theme", "Dark (Default)") ?: "Dark (Default)"
        val theme = KeyboardTheme.fromName(themeName)
        keyboardView?.keyboardTheme = theme
        Log.i(TAG, "Applied theme: $themeName")
    }

    private fun observeModifierFlows() {
        val view = keyboardView ?: return
        imeScope.launch {
            combine(view.modifierState.shift, view.modifierState.ctrl, view.modifierState.alt, view.modifierState.fn)
            { s, c, a, f -> updateModifierStatus(s, c, a, f) }.collect { }
        }
    }

    private fun learnFromCurrentText() {
        val text = currentInputConnection?.getTextBeforeCursor(200, 0)?.toString() ?: return
        val engine = suggestionEngine as? DictSuggestionEngine ?: return
        imeScope.launch(Dispatchers.IO) {
            engine.learnFromText(text)
        }
    }

    override fun onFinishInput() {
        currentInputConnection?.finishComposingText()
        autocorrect.reset()
        super.onFinishInput()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        autocorrect.onCursorMoved(newSelStart)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (!::suggestionEngine.isInitialized) return
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: return

        // Limpiar si texto muy corto o vacío
        val trimmed = text.trim()
        if (trimmed.length < MIN_TEXT_LENGTH) {
            mainHandler.removeCallbacks(clearSuggestionsRunnable)
            suggestionBarView?.clearSuggestions()
            return
        }

        // Resetear timer de auto-limpieza
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        mainHandler.postDelayed(clearSuggestionsRunnable, CLEAR_DELAY_MS)

        // Cancelar job anterior — el delay() es suspension point, se cancela inmediatamente
        suggestionJob?.cancel()
        suggestionJob = imeScope.launch {
            // Debounce: esperar antes de inferir (evita inferir en cada tecla)
            delay(DEBOUNCE_MS)
            if (!isActive) return@launch

            // Inferir en dispatcher single-thread (evita XNNPACK race condition)
            val results = withContext(inferDispatcher) {
                suggestionEngine.getSuggestions(text)
            }
            if (!isActive) return@launch

            if (results.isNotEmpty()) {
                suggestionBarView?.setSuggestions(results)
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        // Guardar historial del DictSuggestionEngine
        (suggestionEngine as? DictSuggestionEngine)?.savePersistedData()
        suggestionEngine.takeIf { ::suggestionEngine.isInitialized }?.close()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        imeScope.cancel()
        super.onDestroy()
    }
    
    private fun reloadKeyboard() {
        if (keyboardView != null) {
            loadAndSetKeyboard()
            applyTheme()
            Log.i(TAG, "Keyboard reloaded")
        }
    }

    private fun loadAndSetKeyboard() {
        val dm = resources.displayMetrics
        val showNumberRow = prefs.getBoolean("show_number_row", true)
        val layoutName = prefs.getString("custom_layout_name", null)

        val keyboard = if (!isSymbolsMode && layoutName != null) {
            loadCustomOrFallback(layoutName, dm.widthPixels, dm.heightPixels, showNumberRow)
        } else {
            val layoutId = if (isSymbolsMode) {
                R.xml.kbd_symbols_simple
            } else {
                getLayoutResourceId()
            }
            SimpleKeyboard.fromXml(this, layoutId, dm.widthPixels, dm.heightPixels, showNumberRow)
        }
        keyboardView?.setKeyboard(keyboard)
    }

    private fun loadCustomOrFallback(
        name: String,
        screenWidth: Int,
        screenHeight: Int,
        showNumberRow: Boolean
    ): SimpleKeyboard {
        val fallbackId = getLayoutResourceId()
        val inputStream = XmlKeyboardStorage.openInputStream(this, name)
        return if (inputStream != null) {
            try {
                Log.i(TAG, "Loading custom layout: $name")
                SimpleKeyboard.fromXml(this, inputStream, screenWidth, screenHeight, showNumberRow)
                    .also { inputStream.close() }
            } catch (e: Exception) {
                Log.e(TAG, "Custom layout failed, using built-in", e)
                try { inputStream.close() } catch (_: Exception) {}
                SimpleKeyboard.fromXml(this, fallbackId, screenWidth, screenHeight, showNumberRow)
            }
        } else {
            Log.w(TAG, "Custom layout '$name' not found, using built-in")
            SimpleKeyboard.fromXml(this, fallbackId, screenWidth, screenHeight, showNumberRow)
        }
    }
}
