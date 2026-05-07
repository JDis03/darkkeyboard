package org.dark.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout

class DarkIME : InputMethodService(), SimpleKeyboardView.OnKeyListener {

    private var keyboardView: SimpleKeyboardView? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("DarkIME", "onCreate")
    }

    override fun onCreateInputView(): View {
        Log.d("DarkIME", "onCreateInputView")
        try {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val keyboard = SimpleKeyboard.fromXml(
                this,
                R.xml.kbd_qwerty,
                screenWidth,
                screenHeight
            )

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            keyboardView = SimpleKeyboardView(this).apply {
                setKeyboard(keyboard)
                onKeyListener = this@DarkIME
            }

            container.addView(keyboardView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            Log.d("DarkIME", "Keyboard created: ${keyboard.rows.size} rows, ${keyboard.allKeys.size} keys, h=${keyboard.totalHeight}")
            return container
        } catch (e: Exception) {
            Log.e("DarkIME", "onCreateInputView error", e)
            return android.widget.TextView(this).apply { text = "Error: ${e.message}" }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.d("DarkIME", "onStartInputView")
    }

    override fun onKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean) {
        val ic = currentInputConnection ?: return

        when (code) {
            Key.CODE_DELETE -> {
                if (ctrl) {
                    ic.deleteSurroundingText(1, 0)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            Key.CODE_ENTER -> {
                val action = currentInputEditorInfo?.imeOptions ?: 0
                val maskedAction = action and EditorInfo.IME_MASK_ACTION
                if (maskedAction != EditorInfo.IME_ACTION_NONE) {
                    ic.performEditorAction(maskedAction)
                } else {
                    ic.commitText("\n", 1)
                }
            }
            Key.CODE_SPACE -> ic.commitText(" ", 1)
            Key.CODE_TAB -> ic.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_TAB)
            )
            Key.CODE_SETTINGS -> {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("DarkIME", "Cannot open settings", e)
                }
            }
            Key.CODE_F1 -> { }
            else -> {
                if (ctrl && code in 'A'.code..'Z'.code) {
                    when (code.toChar()) {
                        'A' -> ic.performContextMenuAction(android.R.id.selectAll)
                        'C' -> ic.performContextMenuAction(android.R.id.copy)
                        'V' -> ic.performContextMenuAction(android.R.id.paste)
                        'X' -> ic.performContextMenuAction(android.R.id.cut)
                        else -> ic.commitText(code.toChar().toString(), 1)
                    }
                } else {
                    val char = if (shift) code.toChar().uppercaseChar() else code.toChar()
                    ic.commitText(char.toString(), 1)
                }
            }
        }
    }

    override fun onText(text: CharSequence) {
        currentInputConnection?.commitText(text, 1)
    }
}