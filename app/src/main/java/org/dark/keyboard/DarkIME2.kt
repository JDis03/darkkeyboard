package org.dark.keyboard

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.dark.keyboard.autocorrect.AutocorrectEngine
import org.dark.keyboard.autocorrect.PersonalDictionary
import org.dark.keyboard.autocorrect.WordDictionary

/**
 * InputMethodService con autocorrección nivel 3.
 */
class DarkIME2 : InputMethodService() {
    
    private var keyboardView: SimpleKeyboardView? = null
    private var modifierStatusView: TextView? = null
    private var isSymbolsMode = false
    private lateinit var prefs: SharedPreferences
    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Autocorrect ──────────────────────────────────────────────────────
    private lateinit var wordDict: WordDictionary
    private lateinit var personalDict: PersonalDictionary
    private lateinit var autocorrect: AutocorrectEngine
    private var currentLang = "es"

    companion object {
        private const val TAG = "DarkIME2"
        private const val KEYCODE_DELETE = -5
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_ENTER = 10
    }
    
    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        // Inicializar autocorrect en background
        wordDict     = WordDictionary(this)
        personalDict = PersonalDictionary(this)
        autocorrect  = AutocorrectEngine(this, wordDict, personalDict)
        autocorrect.initialize()
        currentLang  = prefs.getString("suggestion_language", "es") ?: "es"
        imeScope.launch(Dispatchers.IO) {
            wordDict.load(currentLang)
            Log.i(TAG, "WordDictionary ready for $currentLang")
        }
        
        // Listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "keyboard_layout" || key == "show_number_row") {
                Log.i(TAG, "Preference '$key' changed, reloading keyboard...")
                reloadKeyboard()
            }
            if (key == "keyboard_theme" || key == "show_modifier_status") {
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
        
        Log.e(TAG, "=== onCreate() CALLED ===")
    }
    
    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.e(TAG, "=== onStartInput() inputType=${attribute?.inputType} ===")

        // Resetear autocorrect — previene ghost-composing entre campos
        autocorrect.reset()
        autocorrect.onEditorChanged(attribute)

        if (!isSymbolsMode) reloadKeyboard()
        applyTheme()
    }

    override fun onFinishInput() {
        // Finalizar composing antes de perder el campo — previene texto duplicado
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
        // Detectar movimiento externo del cursor (tap del usuario en otro punto)
        autocorrect.onCursorMoved(newSelStart)
    }
    
    override fun onEvaluateFullscreenMode(): Boolean {
        val result = super.onEvaluateFullscreenMode()
        Log.e(TAG, "=== onEvaluateFullscreenMode() = $result ===")
        return false  // Force non-fullscreen mode
    }
    
    private var inputViewContainer: View? = null
    
    override fun onCreateInputView(): View? {
        Log.e(TAG, "=== onCreateInputView CALLED ===")
        
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        inputViewContainer = layout
        keyboardView = layout.findViewById(R.id.keyboard)
        modifierStatusView = layout.findViewById(R.id.modifier_status)
        
        // Crear teclado desde XML - usar layout desde preferences
        val dm = resources.displayMetrics
        val layoutId = getLayoutResourceId()
        val showNumberRow = prefs.getBoolean("show_number_row", true)
        val keyboard = SimpleKeyboard.fromXml(this, layoutId, dm.widthPixels, dm.heightPixels, showNumberRow)
        keyboardView?.setKeyboard(keyboard)
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
        
        Log.i(TAG, "Keyboard created: ${keyboard.allKeys.size} keys, ${keyboard.rows.size} rows")
        
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
                            // Borrar " corrected" (espacio + palabra corregida)
                            val deleteCount = r.corrected.length + 1
                            if (ic.deleteSurroundingText(deleteCount, 0)) {
                                ic.setComposingText(r.original, 1)
                                Log.i(TAG, "Undo: '${r.corrected}' → '${r.original}'")
                            }
                        }
                        is AutocorrectEngine.BackspaceResult.UpdateComposing -> {
                            if (result.remaining.isEmpty()) {
                                ic.commitText("", 1)
                            } else {
                                ic.setComposingText(result.remaining, 1)
                            }
                        }
                        AutocorrectEngine.BackspaceResult.Normal -> {
                            ic.deleteSurroundingText(1, 0)
                        }
                    }
                }
            }

            // ── Space ─────────────────────────────────────────────────────
            ' '.code -> {
                val textBefore = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
                when (val result = autocorrect.onSpace(textBefore)) {
                    is AutocorrectEngine.SpaceResult.Corrected -> {
                        // Finalizar composing antes de modificar texto
                        ic.finishComposingText()
                        // Borrar la palabra tipada y reemplazar con corrección
                        ic.deleteSurroundingText(result.original.length, 0)
                        ic.commitText("${result.corrected} ", 1)
                        Log.i(TAG, "Autocorrect: '${result.original}' → '${result.corrected}'")
                    }
                    AutocorrectEngine.SpaceResult.PeriodInserted -> {
                        ic.finishComposingText()
                        ic.deleteSurroundingText(1, 0)  // quitar el espacio anterior
                        ic.commitText(". ", 1)
                        Log.i(TAG, "Double-space → period")
                    }
                    AutocorrectEngine.SpaceResult.Normal -> {
                        ic.finishComposingText()
                        ic.commitText(" ", 1)
                    }
                }
            }

            // ── Teclas especiales — finalizan composing ───────────────────
            KEYCODE_SHIFT -> { }
            Key.CODE_CTRL_LEFT -> { }
            Key.CODE_ALT_LEFT -> { }
            Key.CODE_MODE_CHANGE -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                Log.i(TAG, "MODE_CHANGE pressed, switching layout...")
                switchLayout()
            }
            KEYCODE_ENTER -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_ENTER, shift, ctrl, alt, fn)
            }
            Key.CODE_TAB -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB, shift, ctrl, alt, fn)
            }
            Key.CODE_ESCAPE -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_ESCAPE, shift, ctrl, alt, fn)
            }
            Key.CODE_FORWARD_DEL -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_FORWARD_DEL, shift, ctrl, alt, fn)
            }
            Key.CODE_HOME -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_MOVE_HOME, shift, ctrl, alt, fn)
            }
            Key.CODE_END -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_MOVE_END, shift, ctrl, alt, fn)
            }
            Key.CODE_PAGE_UP -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_PAGE_UP, shift, ctrl, alt, fn)
            }
            Key.CODE_PAGE_DOWN -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_PAGE_DOWN, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_UP -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_UP, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_DOWN -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_DOWN, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_LEFT -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_RIGHT -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_RIGHT, shift, ctrl, alt, fn)
            }
            Key.CODE_CLOSE -> {
                requestHideSelf(0)
                Log.d(TAG, "Closing keyboard")
            }
            Key.CODE_SWITCH_INPUT -> {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
                Log.d(TAG, "Showing IME picker")
            }
            in SimpleKeyboardView.KEYCODE_FKEY_F1..SimpleKeyboardView.KEYCODE_FKEY_F12 -> {
                autocorrect.onFinishComposing()
                ic.finishComposingText()
                val fKeyNumber = SimpleKeyboardView.KEYCODE_FKEY_F1 - code + 1
                val keycode = KeyEvent.KEYCODE_F1 + (fKeyNumber - 1)
                sendModifiedKeyDownUp(keycode, shift, ctrl, alt, fn)
            }

            // ── Caracteres regulares ──────────────────────────────────────
            else -> {
                if (code > 0 && code < 127) {
                    if (ctrl || alt) {
                        // Ctrl/Alt combos — finalizar composing primero
                        autocorrect.onFinishComposing()
                        ic.finishComposingText()
                        val keycode = when (code.toChar().lowercaseChar()) {
                            in 'a'..'z' -> KeyEvent.KEYCODE_A + (code.toChar().lowercaseChar() - 'a')
                            ' '         -> KeyEvent.KEYCODE_SPACE
                            else -> {
                                var char = code.toChar().toString()
                                if (shift && code in 'a'.code..'z'.code) char = char.uppercase()
                                ic.commitText(char, 1)
                                return
                            }
                        }
                        sendModifiedKeyDownUp(keycode, shift, ctrl, alt, fn)
                    } else {
                        val c = code.toChar()
                        val isLetter = c.isLetter()

                        if (isLetter) {
                            // ── Letra: pasar por autocorrect ─────────────
                            var char = c.toString()
                            if (shift) char = char.uppercase()

                            // Auto-capitalización después de ". " etc.
                            if (!shift) {
                                val textBefore = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                                if (autocorrect.shouldCapitalizeNext(textBefore)) {
                                    char = char.uppercase()
                                }
                            }

                            when (val result = autocorrect.onCharacter(char[0])) {
                                is AutocorrectEngine.CharResult.UpdateComposing -> {
                                    ic.setComposingText(result.composing, 1)
                                }
                                is AutocorrectEngine.CharResult.CommitDirect -> {
                                    ic.commitText(result.char, 1)
                                }
                            }
                        } else {
                            // ── Puntuación u otro char — finaliza composing
                            autocorrect.onFinishComposing()
                            ic.finishComposingText()
                            var char = c.toString()
                            if (shift && code in 'a'.code..'z'.code) char = char.uppercase()
                            ic.commitText(char, 1)
                        }
                    }
                }
            }
        }
    }

    private fun sendModifiedKeyDownUp(key: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val ic = currentInputConnection ?: return
        val eventTime = System.currentTimeMillis()

        val meta = buildMetaState(shift, ctrl, alt, fn)

        // Send modifier DOWN events first (Ctrl/Alt/Meta keys as real KeyEvents)
        sendModifierDown(ic, eventTime, ctrl, KeyEvent.KEYCODE_CTRL_LEFT)
        sendModifierDown(ic, eventTime, alt, KeyEvent.KEYCODE_ALT_LEFT)

        // Target key DOWN + UP with meta state
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, key, 0, meta))
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, key, 0, meta))

        // Send modifier UP events (reverse order)
        sendModifierUp(ic, eventTime, alt, KeyEvent.KEYCODE_ALT_LEFT)
        sendModifierUp(ic, eventTime, ctrl, KeyEvent.KEYCODE_CTRL_LEFT)
    }

    private fun sendModifierDown(ic: android.view.inputmethod.InputConnection, eventTime: Long, active: Boolean, keycode: Int) {
        if (!active) return
        val chordingMode = prefs.getString("chording_ctrl_key", "0") != "0"
        if (chordingMode) {
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keycode, 0, 0))
        }
    }

    private fun sendModifierUp(ic: android.view.inputmethod.InputConnection, eventTime: Long, active: Boolean, keycode: Int) {
        if (!active) return
        val chordingMode = prefs.getString("chording_ctrl_key", "0") != "0"
        if (chordingMode) {
            ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keycode, 0, 0))
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
        val dm = resources.displayMetrics
        val showNumberRow = prefs.getBoolean("show_number_row", true)
        val keyboard = if (isSymbolsMode) {
            // En modo símbolos, SIEMPRE mostrar number row (son parte del layout de símbolos)
            SimpleKeyboard.fromXml(this, R.xml.kbd_symbols_simple, dm.widthPixels, dm.heightPixels, true)
        } else {
            // En modo alfabético, respetar preferencia del usuario
            val layoutId = getLayoutResourceId()
            SimpleKeyboard.fromXml(this, layoutId, dm.widthPixels, dm.heightPixels, showNumberRow)
        }
        keyboardView?.setKeyboard(keyboard)
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

    override fun onDestroy() {
        imeScope.cancel()
        super.onDestroy()
    }
    
    private fun reloadKeyboard() {
        // Reload the keyboard with new layout
        if (keyboardView != null) {
            val dm = resources.displayMetrics
            val layoutId = getLayoutResourceId()
            val showNumberRow = prefs.getBoolean("show_number_row", true)
            val keyboard = SimpleKeyboard.fromXml(this, layoutId, dm.widthPixels, dm.heightPixels, showNumberRow)
            keyboardView?.setKeyboard(keyboard)
            applyTheme()
            Log.i(TAG, "Keyboard reloaded with new layout")
        }
    }
}
