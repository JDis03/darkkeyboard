package org.dark.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.widget.LinearLayout

/**
 * InputMethodService simple y funcional
 * Sin complejidad innecesaria - solo mostrar teclado y escribir
 */
class DarkIME2 : InputMethodService() {
    
    private var keyboardView: SimpleKeyboardView? = null
    
    companion object {
        private const val TAG = "DarkIME2"
        private const val KEYCODE_DELETE = -5
        private const val KEYCODE_SHIFT = -1
        private const val KEYCODE_ENTER = 10
    }
    
    override fun onCreateInputView(): View {
        Log.i(TAG, "onCreateInputView")
        
        val layout = layoutInflater.inflate(R.layout.keyboard_view, null) as LinearLayout
        keyboardView = layout.findViewById(R.id.keyboard)
        
        // Crear teclado desde XML
        val dm = resources.displayMetrics
        val keyboard = SimpleKeyboard.fromXml(this, R.xml.kbd_qwerty, dm.widthPixels, dm.heightPixels)
        keyboardView?.setKeyboard(keyboard)
        keyboardView?.onKeyListener = object : SimpleKeyboardView.OnKeyListener {
            override fun onKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
                handleKey(code, shift, ctrl, alt, fn)
            }
            override fun onText(text: CharSequence) {
                currentInputConnection?.commitText(text, 1)
            }
        }
        
        Log.i(TAG, "Keyboard created: ${keyboard.allKeys.size} keys, ${keyboard.rows.size} rows")
        
        return layout
    }
    
    private fun handleKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val ic = currentInputConnection ?: return
        
        when (code) {
            KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
            }
            KEYCODE_SHIFT -> {
                // Shift se maneja en el View
            }
            KEYCODE_ENTER -> {
                ic.commitText("\n", 1)
            }
            else -> {
                // Escribir la tecla
                val char = code.toChar().toString()
                ic.commitText(char, 1)
            }
        }
    }
}
