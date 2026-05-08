package org.dark.keyboard

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.PreferenceManager

/**
 * InputMethodService simple y funcional
 * Sin complejidad innecesaria - solo mostrar teclado y escribir
 */
class DarkIME2 : InputMethodService() {
    
    private var keyboardView: SimpleKeyboardView? = null
    private var modifierStatusView: TextView? = null
    private var isSymbolsMode = false
    private lateinit var prefs: SharedPreferences
    
    companion object {
        private const val TAG = "DarkIME2"
        private const val KEYCODE_DELETE = -5
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_ENTER = 10
    }
    
    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        
        // Listen for preference changes
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            if (key == "keyboard_layout" || key == "show_number_row") {
                Log.i(TAG, "Preference '$key' changed, reloading keyboard...")
                reloadKeyboard()
            }
        }
        
        Log.e(TAG, "=== onCreate() CALLED ===")
    }
    
    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.e(TAG, "=== onStartInput() inputType=${attribute?.inputType} ===")
        
        // Reload keyboard if layout preference changed
        if (!isSymbolsMode) {
            reloadKeyboard()
        }
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
        keyboardView?.onKeyListener = object : SimpleKeyboardView.OnKeyListener {
            override fun onKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
                handleKey(code, shift, ctrl, alt, fn)
            }
            override fun onText(text: CharSequence) {
                currentInputConnection?.commitText(text, 1)
            }
        }
        keyboardView?.onModifierChangeListener = object : SimpleKeyboardView.OnModifierChangeListener {
            override fun onModifierChanged(shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
                updateModifierStatus(shift, ctrl, alt, fn)
            }
        }
        
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
        
        // Build meta state based on active modifiers
        var metaState = 0
        if (shift) metaState = metaState or KeyEvent.META_SHIFT_ON
        if (ctrl) metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        
        val charLabel = if (code > 0 && code < 128) code.toChar().toString() else "?"
        Log.i(TAG, ">>> handleKey: code=$code ($charLabel), shift=$shift, ctrl=$ctrl, alt=$alt, fn=$fn, metaState=$metaState")
        
        when (code) {
            KEYCODE_DELETE -> {
                if (ctrl) {
                    // Ctrl+Backspace: delete word
                    deleteWord(ic)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            KEYCODE_SHIFT -> {
                // Shift se maneja en el View
            }
            Key.CODE_CTRL_LEFT -> {
                // Ctrl se maneja en el View como toggle
                Log.d(TAG, "Ctrl toggle")
            }
            Key.CODE_ALT_LEFT -> {
                // Alt se maneja en el View como toggle
                Log.d(TAG, "Alt toggle")
            }
            Key.CODE_MODE_CHANGE -> {
                // Cambiar entre alfabético y símbolos
                Log.i(TAG, "MODE_CHANGE pressed, switching layout...")
                switchLayout()
            }
            KEYCODE_ENTER -> {
                sendKeyEvent(KeyEvent.KEYCODE_ENTER, metaState)
            }
            Key.CODE_TAB -> {
                sendKeyEvent(KeyEvent.KEYCODE_TAB, metaState)
            }
            Key.CODE_ESCAPE -> {
                sendKeyEvent(KeyEvent.KEYCODE_ESCAPE, metaState)
            }
            Key.CODE_FORWARD_DEL -> {
                sendKeyEvent(KeyEvent.KEYCODE_FORWARD_DEL, metaState)
            }
            Key.CODE_HOME -> {
                sendKeyEvent(KeyEvent.KEYCODE_MOVE_HOME, metaState)
            }
            Key.CODE_END -> {
                sendKeyEvent(KeyEvent.KEYCODE_MOVE_END, metaState)
            }
            Key.CODE_PAGE_UP -> {
                sendKeyEvent(KeyEvent.KEYCODE_PAGE_UP, metaState)
            }
            Key.CODE_PAGE_DOWN -> {
                sendKeyEvent(KeyEvent.KEYCODE_PAGE_DOWN, metaState)
            }
            Key.CODE_DPAD_UP -> {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP, metaState)
            }
            Key.CODE_DPAD_DOWN -> {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, metaState)
            }
            Key.CODE_DPAD_LEFT -> {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, metaState)
            }
            Key.CODE_DPAD_RIGHT -> {
                sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, metaState)
            }
            Key.CODE_CLOSE -> {
                // Close/hide the keyboard
                requestHideSelf(0)
                Log.d(TAG, "Closing keyboard")
            }
            Key.CODE_SWITCH_INPUT -> {
                // Switch to next IME
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showInputMethodPicker()
                Log.d(TAG, "Showing IME picker")
            }
            in SimpleKeyboardView.KEYCODE_FKEY_F1..SimpleKeyboardView.KEYCODE_FKEY_F12 -> {
                // Map F1-F12 to Android keycodes
                val fKeyNumber = SimpleKeyboardView.KEYCODE_FKEY_F1 - code + 1
                val keycode = KeyEvent.KEYCODE_F1 + (fKeyNumber - 1)
                sendKeyEvent(keycode, metaState)
            }
            else -> {
                if (code > 0 && code < 127) {
                    // Regular character - handle with modifiers
                    if (ctrl || alt) {
                        // Send as KeyEvent to preserve modifiers
                        val keycode = when (code.toChar().lowercaseChar()) {
                            in 'a'..'z' -> KeyEvent.KEYCODE_A + (code.toChar().lowercaseChar() - 'a')
                            ' ' -> KeyEvent.KEYCODE_SPACE
                            else -> {
                                // For other chars, commit text directly
                                var char = code.toChar().toString()
                                if (shift && code in 'a'.code..'z'.code) {
                                    char = char.uppercase()
                                }
                                ic.commitText(char, 1)
                                return
                            }
                        }
                        sendKeyEvent(keycode, metaState)
                    } else {
                        // No modifiers - commit text directly
                        var char = code.toChar().toString()
                        if (shift && code in 'a'.code..'z'.code) {
                            char = char.uppercase()
                        }
                        ic.commitText(char, 1)
                    }
                }
            }
        }
    }
    
    private fun sendKeyEvent(keycode: Int, metaState: Int) {
        val ic = currentInputConnection ?: return
        val eventTime = System.currentTimeMillis()
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keycode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keycode, 0, metaState))
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
            SimpleKeyboard.fromXml(this, R.xml.kbd_symbols_simple, dm.widthPixels, dm.heightPixels, showNumberRow)
        } else {
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
    
    private fun reloadKeyboard() {
        // Reload the keyboard with new layout
        if (keyboardView != null) {
            val dm = resources.displayMetrics
            val layoutId = getLayoutResourceId()
            val showNumberRow = prefs.getBoolean("show_number_row", true)
            val keyboard = SimpleKeyboard.fromXml(this, layoutId, dm.widthPixels, dm.heightPixels, showNumberRow)
            keyboardView?.setKeyboard(keyboard)
            Log.i(TAG, "Keyboard reloaded with new layout")
        }
    }
}
