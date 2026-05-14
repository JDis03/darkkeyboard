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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.dark.keyboard.suggestions.FallbackSuggestionEngine
import org.dark.keyboard.suggestions.SuggestionEngine
import org.dark.keyboard.suggestions.TFLiteSuggestionEngine

/**
 * InputMethodService simple y funcional
 * Sin complejidad innecesaria - solo mostrar teclado y escribir
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
    private val CLEAR_DELAY_MS = 3000L  // 3 segundos sin escribir → limpiar
    private lateinit var prefs: SharedPreferences

    // Motor de sugerencias — TFLite si hay modelo, Fallback si no
    private lateinit var suggestionEngine: SuggestionEngine
    private val fallbackEngine = FallbackSuggestionEngine()
    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
    
    companion object {
        private const val TAG = "DarkIME2"
        private const val KEYCODE_DELETE = -5
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_ENTER = 10
    }
    
    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        Log.e(TAG, "=== onCreate() CALLED ===")

        // Inicializar motor de sugerencias en background
        imeScope.launch(Dispatchers.IO) {
            val tflite = TFLiteSuggestionEngine(this@DarkIME2)
            tflite.initialize()
            suggestionEngine = if (tflite.isReady()) {
                Log.i(TAG, "Using TFLite suggestion engine")
                tflite
            } else {
                Log.i(TAG, "TFLite not available, using fallback engine")
                // Cargar historial del usuario guardado
                val saved = prefs.getString("suggestion_freq", "") ?: ""
                fallbackEngine.deserializeFrequency(saved)
                fallbackEngine
            }
        }
    }
    
    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.e(TAG, "=== onStartInput() inputType=${attribute?.inputType} ===")

        keyboardView?.modifierState?.clearAll()
        // Limpiar sugerencias al cambiar de campo de texto
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        suggestionBarView?.clearSuggestions()

        if (!isSymbolsMode) {
            reloadKeyboard()
        }
        applyTheme()
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
        suggestionBarView = layout.findViewById(R.id.suggestion_bar)

        clipboardPopup = ClipboardPopup(this) { text ->
            currentInputConnection?.commitText(text, 1)
        }

        suggestionBarView?.listener = object : SuggestionBarView.Listener {
            override fun onSuggestionClick(text: String) {
                val ic = currentInputConnection ?: return
                val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: ""
                val endsWithSpace = before.endsWith(" ")
                val partial = if (!endsWithSpace) before.trimEnd().split(Regex("\\s+")).lastOrNull() ?: "" else ""

                // Reemplazar palabra parcial si la sugerencia la completa
                if (partial.isNotEmpty() && text.startsWith(partial, ignoreCase = true)) {
                    ic.deleteSurroundingText(partial.length, 0)
                }
                ic.commitText("$text ", 1)

                // Aprender del usuario
                if (::suggestionEngine.isInitialized) {
                    suggestionEngine.onSuggestionAccepted(text, before)
                }

                // Actualizar sugerencias para la siguiente palabra
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
            KEYCODE_DELETE -> {
                if (ctrl) {
                    deleteWord(ic)
                } else {
                    sendSimpleKeyEvent(KeyEvent.KEYCODE_DEL)
                }
            }
            KEYCODE_SHIFT -> { }
            Key.CODE_CTRL_LEFT -> { }
            Key.CODE_ALT_LEFT -> { }
            Key.CODE_MODE_CHANGE -> {
                Log.i(TAG, "MODE_CHANGE pressed, switching layout...")
                switchLayout()
            }
            KEYCODE_ENTER -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_ENTER, shift, ctrl, alt, fn)
            }
            Key.CODE_TAB -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_TAB, shift, ctrl, alt, fn)
            }
            Key.CODE_ESCAPE -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_ESCAPE, shift, ctrl, alt, fn)
            }
            Key.CODE_FORWARD_DEL -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_FORWARD_DEL, shift, ctrl, alt, fn)
            }
            Key.CODE_HOME -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_MOVE_HOME, shift, ctrl, alt, fn)
            }
            Key.CODE_END -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_MOVE_END, shift, ctrl, alt, fn)
            }
            Key.CODE_PAGE_UP -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_PAGE_UP, shift, ctrl, alt, fn)
            }
            Key.CODE_PAGE_DOWN -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_PAGE_DOWN, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_UP -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_UP, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_DOWN -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_DOWN, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_LEFT -> {
                sendModifiedKeyDownUp(KeyEvent.KEYCODE_DPAD_LEFT, shift, ctrl, alt, fn)
            }
            Key.CODE_DPAD_RIGHT -> {
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
                val fKeyNumber = SimpleKeyboardView.KEYCODE_FKEY_F1 - code + 1
                val keycode = KeyEvent.KEYCODE_F1 + (fKeyNumber - 1)
                sendModifiedKeyDownUp(keycode, shift, ctrl, alt, fn)
            }
            else -> {
                if (code > 0 && code < 127) {
                    if (ctrl || alt) {
                        val keycode = when (code.toChar().lowercaseChar()) {
                            in 'a'..'z' -> KeyEvent.KEYCODE_A + (code.toChar().lowercaseChar() - 'a')
                            ' ' -> KeyEvent.KEYCODE_SPACE
                            else -> {
                                var char = code.toChar().toString()
                                if (shift && code in 'a'.code..'z'.code) {
                                    char = char.uppercase()
                                }
                                ic.commitText(char, 1)
                                return
                            }
                        }
                        sendModifiedKeyDownUp(keycode, shift, ctrl, alt, fn)
                    } else {
                        var char = code.toChar().toString()
                        if (shift && code in 'a'.code..'z'.code) {
                            char = char.uppercase()
                        }
                        ic.commitText(char, 1)
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

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (!::suggestionEngine.isInitialized) return
        val ic = currentInputConnection ?: return
        val text = ic.getTextBeforeCursor(100, 0)?.toString() ?: return

        // Limpiar si no hay texto antes del cursor
        if (text.isBlank()) {
            mainHandler.removeCallbacks(clearSuggestionsRunnable)
            suggestionBarView?.clearSuggestions()
            return
        }

        // Resetear timer de limpieza — si el usuario para de escribir 3s, se limpian
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        mainHandler.postDelayed(clearSuggestionsRunnable, CLEAR_DELAY_MS)

        imeScope.launch(Dispatchers.IO) {
            val results = suggestionEngine.getSuggestions(text)
            launch(Dispatchers.Main) {
                if (results.isEmpty()) {
                    // No resetear el timer — dejar que limpie solo
                } else {
                    suggestionBarView?.setSuggestions(results)
                }
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(clearSuggestionsRunnable)
        // Guardar historial del fallback engine
        if (::suggestionEngine.isInitialized && suggestionEngine is FallbackSuggestionEngine) {
            prefs.edit()
                .putString("suggestion_freq", fallbackEngine.serializeFrequency())
                .apply()
        }
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
