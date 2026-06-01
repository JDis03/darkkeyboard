package org.dark.keyboard

import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper

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
import org.dark.keyboard.util.FileLoggingTree
import timber.log.Timber

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
                Timber.i("Preference '$key' changed, reloading keyboard...")
                reloadKeyboard()
            }
            "keyboard_theme", "show_modifier_status" -> {
                Timber.i("Preference '$key' changed, applying...")
                applyTheme()
                updateModifierStatus(
                    keyboardView?.isShiftActive() ?: false,
                    keyboardView?.isCtrlActive() ?: false,
                    keyboardView?.isAltActive() ?: false,
                    keyboardView?.isFnActive() ?: false
                )
            }
            "autocorrect_enabled" -> {
                val newValue = prefs.getBoolean("autocorrect_enabled", false)
                Timber.i("Preference 'autocorrect_enabled' changed to: $newValue")
                autocorrect.isEnabled = newValue
                // Si se desactivó, limpiar cualquier composing activo
                if (!newValue) {
                    Timber.i("Autocorrect disabled - clearing active composing")
                    autocorrect.onFinishComposing()
                    currentInputConnection?.finishComposingText()
                }
            }
        }
    }
    

    
    override fun onCreate() {
        super.onCreate()

        // Plantar FileLoggingTree para logs compartibles
        fileLoggingTree = FileLoggingTree(this).also {
            Timber.plant(it)
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    android.util.Log.println(priority, tag ?: "DarkIME", message)
                }
            })

        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Timber.e("=== onCreate() CALLED ===")
        Timber.i("DarkIME2 onCreate")

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
            Timber.i("Suggestion engine: ${suggestionEngine.engineName}")
        }
    }
    
     override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)

        // Clasificar el campo actual y ajustar comportamiento
        val profile = AppInputProfile.classify(attribute)
        isTerminalMode = profile.mode == AppInputProfile.Mode.TERMINAL
        Timber.i("=== onStartInput() mode=${profile.mode} reason=${profile.reason} inputType=${attribute?.inputType} ===")

        // Autocorrect: reset ghost-composing + ajustar según perfil
        autocorrect.isTerminalApp = isTerminalMode
        // DON'T override isEnabled - it's the user's preference from Settings
        // autocorrect.isEnabled = profile.useAutocorrect  ❌ REMOVED
        autocorrect.reset()
        terminalBuffer.clear()
        composingBroken = false
        composingFailCount = 0
        expectedSelStart = -1  // recalculate on next updateSelection
        expectedSelEnd = -1
        hasActiveSelection = false
        autocorrect.onEditorChanged(attribute)
        // AppInputProfile gana sobre onEditorChanged — browsers ignoran FLAG_NO_SUGGESTIONS del HTML
        autocorrect.overrideProfile(
            useComposing  = profile.useComposing,
            useAutocorrect = profile.useAutocorrect
        )

        keyboardView?.modifierState?.clearAll()
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        suggestionBarView?.clearSuggestions()

        if (!isSymbolsMode) {
            reloadKeyboard()
        }
        applyTheme()
    }

    companion object {

        private const val KEYCODE_DELETE = -5
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_ENTER = 10

        var fileLoggingTree: FileLoggingTree? = null
            private set

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
        Timber.e("=== onEvaluateFullscreenMode() = $result ===")
        return false  // Force non-fullscreen mode
    }
    
    private var inputViewContainer: View? = null
    // true = app es terminal SSH (usa commitText para control chars)
    private var isTerminalMode = false

    // Shadow buffer para sugerencias en terminal SSH.
    // getTextBeforeCursor() devuelve "" en terminales — rastreamos manualmente.
    private val terminalBuffer = StringBuilder()
    
    // ── Modo "direct commit" (HeliBoard pattern) ──────────────────────────────
    // Si setComposingText() retorna false repetidamente, el campo no soporta
    // composing (ej: WebView, campos de búsqueda). Cambiamos a commit directo.
    private var composingBroken = false
    private var composingFailCount = 0
    private val maxComposingFails = 3
    
    // ── Cursor tracking (HeliBoard pattern) ───────────────────────────────────
    // expectedSelStart: posición esperada del cursor después de nuestras operaciones.
    // Si onUpdateSelection llega con este valor → es un update "belated" (nuestro).
    // Si llega con otro valor → el usuario movió el cursor externamente.
    // -1 = desconocido (se recalcula al inicio)
    private var expectedSelStart = -1
    private var expectedSelEnd = -1

    // ── Selection safety ─────────────────────────────────────────────────────
    // true cuando el usuario tiene texto seleccionado (selStart != selEnd).
    // Detectado en onUpdateSelection, consumido en handleKey.
    private var hasActiveSelection = false
    
    override fun onCreateInputView(): View? {
        Timber.e("=== onCreateInputView CALLED ===")
        
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

                // ── Terminal SSH: getTextBeforeCursor="" y composing="" ──────
                // Usar terminalBuffer para saber qué borrar, backspaces reales para borrarlo.
                if (isTerminalMode && terminalBuffer.isNotEmpty()) {
                    val partial = terminalBuffer.toString()
                    val t = System.currentTimeMillis()
                    // Enviar N backspaces para borrar la palabra parcial tipada
                    repeat(partial.length) {
                        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0))
                        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP,   KeyEvent.KEYCODE_DEL, 0))
                    }
                    terminalBuffer.clear()
                    ic.commitText(text, 1)  // sin espacio — en terminal el espacio lo pone el usuario
                    
                    // Alimentar learning engine — en terminal no hay contexto previo (getTextBeforeCursor = "")
                    if (::suggestionEngine.isInitialized) {
                        suggestionEngine.onSuggestionAccepted(text, "")
                    }
                    
                    updateSuggestions()
                    return
                }

                // ── Apps normales ─────────────────────────────────────────────
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
                    val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                    partial = if (!before.endsWith(" "))
                        before.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
                    else ""
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
        
        Timber.i("Keyboard created")
        
        return layout
    }
    
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        // Let Android handle insets automatically
        Timber.d("onComputeInsets: contentTop=${outInsets.contentTopInsets}, visibleTop=${outInsets.visibleTopInsets}")
    }
    
    private fun handleKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val ic = currentInputConnection ?: return

        var metaState = 0
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON

        val charLabel = if (code > 0 && code < 128) code.toChar().toString() else "?"
        Timber.i(">>> handleKey: code=$code ($charLabel), shift=$shift, ctrl=$ctrl, alt=$alt, fn=$fn, metaState=$metaState")

        when (code) {

            // ── Backspace ─────────────────────────────────────────────────
            KEYCODE_DELETE -> {
                if (isTerminalMode && terminalBuffer.isNotEmpty()) {
                    terminalBuffer.deleteCharAt(terminalBuffer.length - 1)
                }
                if (ctrl) {
                    autocorrect.onFinishComposing()
                    ic.finishComposingText()
                    deleteWord(ic)
                } else if (!autocorrect.isEnabled) {
                    // Autocorrect OFF: simple backspace
                    sendSimpleKeyEvent(KeyEvent.KEYCODE_DEL)
                    expectedSelStart = maxOf(0, expectedSelStart - 1)
                    expectedSelEnd = expectedSelStart
                    updateSuggestions()
                } else {
                    when (val result = autocorrect.onBackspace()) {
                        is AutocorrectEngine.BackspaceResult.UndoCorrection -> {
                            val r = result.record
                            val textBeforeUndo = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                            
                            // La palabra corregida + espacio están al final de textBefore
                            // Ej: "calcetines " corregido → undo borra "calcetines" y pone "calcet"
                            val suffix = "${r.corrected} "
                            if (textBeforeUndo.endsWith(suffix)) {
                                ic.beginBatchEdit()
                                
                                // Verificar que deleteSurroundingText realmente borra todo
                                // (fix para bug "oosea" - deleteSurroundingText miente en WebView)
                                // Borrar palabra + espacio (cursor está después del espacio)
                                ic.deleteSurroundingText(r.corrected.length + 1, 0)
                                
                                val textAfterDelete = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                                val actuallyDeleted = textBeforeUndo.length - textAfterDelete.length
                                val expectedToDelete = r.corrected.length + 1  // palabra + espacio
                                
                                if (actuallyDeleted == expectedToDelete) {
                                    // Éxito: borró completamente
                                    val ok = ic.setComposingText(r.original, 1)
                                    if (ok) {
                                        autocorrect.restoreComposing(r.original)
                                        expectedSelStart = ic.getTextBeforeCursor(1000, 0)?.length 
                                            ?: expectedSelStart
                                        Timber.i("Undo: '${r.corrected}' → '${r.original}' (deleted ${actuallyDeleted} chars)")
                                    } else {
                                        Timber.w("Undo setComposingText failed, word may be duplicated")
                                    }
                                } else {
                                    // Falló: deleteSurroundingText no borró todo
                                    Timber.w("Undo deleteSurroundingText failed: expected ${expectedToDelete} but deleted ${actuallyDeleted}")
                                    Timber.w("  textBefore: '${textBeforeUndo.takeLast(20)}'")
                                    Timber.w("  textAfter: '${textAfterDelete.takeLast(20)}'")
                                    
                                    // Fallback: intentar setComposingText sobre lo que quedó
                                    // Puede quedar "oosea" pero al menos está en composing y usuario puede corregir
                                    val ok = ic.setComposingText(r.original, 1)
                                    if (ok) {
                                        autocorrect.restoreComposing(r.original)
                                        Timber.w("Undo fallback: composed '${r.original}' over remaining text")
                                    }
                                }
                                
                                ic.endBatchEdit()
                            } else {
                                ic.finishComposingText()
                                sendSimpleKeyEvent(KeyEvent.KEYCODE_DEL)
                            }
                        }
                        is AutocorrectEngine.BackspaceResult.UpdateComposing -> {
                            if (result.remaining.isEmpty()) {
                                ic.finishComposingText()
                                expectedSelStart = ic.getTextBeforeCursor(100, 0)?.length ?: -1
                                expectedSelEnd = expectedSelStart
                            } else {
                                val ok = ic.setComposingText(result.remaining, 1)
                                if (!ok) {
                                    Timber.w("Backspace setComposingText failed, resetting")
                                    autocorrect.onFinishComposing()
                                    ic.finishComposingText()
                                    expectedSelStart = -1
                                } else {
                                    expectedSelStart = ic.getTextBeforeCursor(100, 0)?.length 
                                        ?: expectedSelStart
                                }
                            }
                        }
                        AutocorrectEngine.BackspaceResult.Normal -> {
                            sendSimpleKeyEvent(KeyEvent.KEYCODE_DEL)
                            expectedSelStart = maxOf(0, expectedSelStart - 1)
                            expectedSelEnd = expectedSelStart
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // SPACE HANDLER — HeliBoard/Gboard pattern (simplified)
            // ══════════════════════════════════════════════════════════════
            ' '.code -> {
                // Autocorrect OFF: just insert space
                if (!autocorrect.isEnabled) {
                    ic.commitText(" ", 1)
                    updateSuggestions()
                    return
                }
                
                val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                val topSuggestion = suggestionBarView?.getTopSuggestion()

                when (val result = autocorrect.onSpace(textBefore, topSuggestion)) {
                    is AutocorrectEngine.SpaceResult.Corrected -> {
                        // Reemplazar composing con la corrección, luego commit
                        ic.beginBatchEdit()
                        ic.setComposingText(result.corrected, 1)
                        ic.finishComposingText()
                        ic.commitText(" ", 1)
                        ic.endBatchEdit()
                        
                        expectedSelStart = ic.getTextBeforeCursor(1000, 0)?.length ?: expectedSelStart
                        expectedSelEnd = expectedSelStart
                        
                        // Learning engine
                        if (::suggestionEngine.isInitialized) {
                            val cleanContext = textBefore.trimEnd()
                                .removeSuffix(result.original).trimEnd()
                            suggestionEngine.onSuggestionAccepted(result.corrected, cleanContext)
                        }
                        
                        Timber.i("Autocorrect: '${result.original}' → '${result.corrected}'")
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
                terminalBuffer.clear()
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
                        terminalBuffer.clear()
                    } else if (ctrl && !alt && !shift && code in 'a'.code..'z'.code && isTerminalMode) {
                        val ctrlByte = (code - 'a'.code + 1).toChar().toString()
                        ic.commitText(ctrlByte, 1)
                        terminalBuffer.clear()
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
                        // ══════════════════════════════════════════════════════════
                        // LETTER HANDLER — HeliBoard/Gboard pattern (simplified)
                        // ══════════════════════════════════════════════════════════
                        val c = code.toChar()
                        if (c.isLetter()) {
                            var char = c.toString()
                            if (shift) char = char.uppercase()

                            // Selection active? Commit directly, reset state
                            if (hasActiveSelection) {
                                hasActiveSelection = false
                                autocorrect.onFinishComposing()
                                ic.finishComposingText()
                                ic.commitText(char, 1)
                                expectedSelStart = -1
                                Timber.d("Selection replaced with '$char'")
                                updateSuggestions()
                                return
                            }

                            // Auto-capitalización
                            val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                            if (!shift && autocorrect.shouldCapitalizeNext(textBefore)) {
                                char = char.uppercase()
                            }
                            
                            // Terminal mode: direct commit, track in shadow buffer
                            if (isTerminalMode) {
                                ic.commitText(char, 1)
                                terminalBuffer.append(char)
                                expectedSelStart += 1
                                updateSuggestions()
                                return
                            }
                            
                            // Autocorrect disabled? Direct commit (no composing/underline)
                            // Suggestions still work via textBeforeCursor
                            Timber.d("Letter '$char': autocorrect.isEnabled=${autocorrect.isEnabled}")
                            if (!autocorrect.isEnabled) {
                                Timber.d(">>> DIRECT COMMIT '$char' (autocorrect OFF)")
                                ic.commitText(char, 1)
                                expectedSelStart += 1
                                updateSuggestions()
                                return
                            }
                            
                            Timber.d(">>> COMPOSING '$char' (autocorrect ON)")
                            
                            // Composing broken (IC doesn't support it)? Direct commit
                            if (composingBroken) {
                                ic.commitText(char, 1)
                                expectedSelStart += 1
                                updateSuggestions()
                                return
                            }
                            
                            // ── HeliBoard pattern: add to composing, update IC ──────
                            val composing = autocorrect.onCharacter(char[0])
                            when (composing) {
                                is AutocorrectEngine.CharResult.UpdateComposing -> {
                                    val ok = ic.setComposingText(composing.composing, 1)
                                    if (!ok) {
                                        composingFailCount++
                                        Timber.w("setComposingText failed ($composingFailCount/$maxComposingFails)")
                                        if (composingFailCount >= maxComposingFails) {
                                            composingBroken = true
                                            Timber.w("Composing broken — switching to direct commit")
                                            autocorrect.onFinishComposing()
                                            ic.finishComposingText()
                                            ic.commitText(char, 1)
                                        }
                                        expectedSelStart = -1
                                    } else {
                                        composingFailCount = 0
                                        expectedSelStart = ic.getTextBeforeCursor(100, 0)?.length ?: expectedSelStart
                                    }
                                }
                                is AutocorrectEngine.CharResult.CommitDirect -> {
                                    ic.commitText(composing.char, 1)
                                    expectedSelStart += 1
                                }
                            }
                        } else {
                            // Puntuación — finaliza composing
                            autocorrect.onFinishComposing()
                            ic.finishComposingText()
                            var char = c.toString()
                            if (shift && code in 'a'.code..'z'.code) char = char.uppercase()
                            ic.commitText(char, 1)
                            if (isTerminalMode) terminalBuffer.clear()
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
            Timber.w(e, "sendModifiedKeyDownUp failed")
        }
    }

    private fun sendSimpleKeyEvent(key: Int) {
        val ic = currentInputConnection ?: return
        try {
            val now = System.currentTimeMillis()
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0, 0))
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, key, 0, 0))
        } catch (e: Exception) {
            Timber.w(e, "sendSimpleKeyEvent failed")
        }
    }

    private fun sendModifierDown(ic: android.view.inputmethod.InputConnection, eventTime: Long, active: Boolean, keycode: Int) {
        if (!active) return
        try {
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keycode, 0, 0))
        } catch (e: Exception) {
            Timber.w(e, "sendModifierDown failed")
        }
    }

    private fun sendModifierUp(ic: android.view.inputmethod.InputConnection, eventTime: Long, active: Boolean, keycode: Int) {
        if (!active) return
        try {
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keycode, 0, 0))
        } catch (e: Exception) {
            Timber.w(e, "sendModifierUp failed")
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
        Timber.i("Switched layout to ${if (isSymbolsMode) "symbols" else "alphabet"}")
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
            Timber.i("Modifiers active: ${statusParts.joinToString(" + ")}")
        } else {
            modifierStatusView?.visibility = View.GONE
        }
    }
    
    private fun getLayoutResourceId(): Int {
        val layout = prefs.getString("keyboard_layout", "pc")
        Timber.i(">>> getLayoutResourceId: layout preference = '$layout'")
        val resourceId = when (layout) {
            "pc" -> R.xml.kbd_pc
            "compact" -> R.xml.kbd_compact
            else -> R.xml.kbd_pc  // Default to QWERTY Standard
        }
        Timber.i(">>> returning resource ID: ${if (resourceId == R.xml.kbd_pc) "kbd_pc" else "kbd_compact"}")
        return resourceId
    }

    private fun applyTheme() {
        val themeName = prefs.getString("keyboard_theme", "Dark (Default)") ?: "Dark (Default)"
        val theme = KeyboardTheme.fromName(themeName)
        keyboardView?.keyboardTheme = theme
        Timber.i("Applied theme: $themeName")
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
        
        // HeliBoard algorithm — isBelatedExpectedUpdate
        // Si el nuevo cursor coincide con nuestro expected → es un update generado
        // por nuestras propias operaciones (setComposingText, etc.). Ignorar.
        if (expectedSelStart == newSelStart && expectedSelEnd == newSelEnd) {
            return
        }
        
        // ── Selection safety (RC1 fix) ────────────────────────────────────────
        // Detectar si el usuario tiene texto seleccionado (selStart != selEnd).
        // En ese caso NO se debe iniciar composing — setComposingText reemplazaría la selección.
        val newHasSelection = (newSelStart != newSelEnd)
        if (newHasSelection != hasActiveSelection) {
            hasActiveSelection = newHasSelection
            if (hasActiveSelection) {
                Timber.d("Selection active: [$newSelStart, $newSelEnd] — finishing composing")
                autocorrect.onFinishComposing()
                currentInputConnection?.finishComposingText()
            }
        }

        // Si nuestro expected coincide con los old values Y new es diferente,
        // el usuario movió el cursor externamente → reset
        if (expectedSelStart == oldSelStart && expectedSelEnd == oldSelEnd
            && (oldSelStart != newSelStart || oldSelEnd != newSelEnd)) {
            Timber.d("External cursor move: $oldSelStart → $newSelStart, resetting composing")
            autocorrect.onFinishComposing()
            expectedSelStart = newSelStart
            expectedSelEnd = newSelEnd
            updateSuggestions()
            return
        }
        
        // Si nuestro expected está entre old y new (update intermedio de Android)
        val betweenOldAndExpected = 
            (newSelStart - oldSelStart) * (expectedSelStart - newSelStart) >= 0 &&
            (newSelEnd - oldSelEnd) * (expectedSelEnd - newSelEnd) >= 0
        
        if (newSelStart == newSelEnd && betweenOldAndExpected) {
            // Belated update → ignorar
            return
        }
        
        // Update no reconocido → recalcular expected
        expectedSelStart = newSelStart
        expectedSelEnd = newSelEnd
        autocorrect.onCursorMoved(newSelStart)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (!::suggestionEngine.isInitialized) return
        val ic = currentInputConnection ?: return

        // En terminal SSH: getTextBeforeCursor devuelve "" — usar shadow buffer
        val text = if (isTerminalMode && terminalBuffer.isNotEmpty()) {
            terminalBuffer.toString()
        } else {
            ic.getTextBeforeCursor(100, 0)?.toString() ?: return
        }

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
        if (::suggestionEngine.isInitialized) {
            (suggestionEngine as? DictSuggestionEngine)?.savePersistedData()
            suggestionEngine.close()
        }
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        imeScope.cancel()
        fileLoggingTree?.close()
        super.onDestroy()
    }
    
    private fun reloadKeyboard() {
        if (keyboardView != null) {
            loadAndSetKeyboard()
            applyTheme()
            Timber.i("Keyboard reloaded")
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
                Timber.i("Loading custom layout: $name")
                SimpleKeyboard.fromXml(this, inputStream, screenWidth, screenHeight, showNumberRow)
                    .also { inputStream.close() }
            } catch (e: Exception) {
                Timber.e(e, "Custom layout failed, using built-in")
                try { inputStream.close() } catch (_: Exception) {}
                SimpleKeyboard.fromXml(this, fallbackId, screenWidth, screenHeight, showNumberRow)
            }
        } else {
            Timber.w("Custom layout '$name' not found, using built-in")
            SimpleKeyboard.fromXml(this, fallbackId, screenWidth, screenHeight, showNumberRow)
        }
    }
}
