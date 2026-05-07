package org.dark.keyboard

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest

class DarkKeyboard : InputMethodService(), LatinKeyboardBaseView.OnKeyboardActionListener {

    private lateinit var kv: LatinKeyboardView
    private lateinit var kbd: LatinKeyboard
    private var shiftState = false
    private var ctrlActive = false
    private var altActive = false
    private var fnActive = false

    override fun onCreate() {
        super.onCreate()
        Log.i("DK", "onCreate")
    }

    override fun onCreateInputView(): View {
        Log.i("DK", "onCreateInputView")
        kbd = LatinKeyboard(this, R.xml.kbd_qwerty, 0, 0.35f)
        kv = LatinKeyboardView(this, null).apply {
            setKeyboard(kbd)
            setOnKeyboardActionListener(this@DarkKeyboard)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return kv
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Log.i("DK", "onStartInputView")
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray, x: Int, y: Int) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> {
                shiftState = !shiftState
                kv.setShiftState(if (shiftState) Keyboard.SHIFT_ON else Keyboard.SHIFT_OFF)
            }
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            LatinKeyboardView.KEYCODE_CTRL_LEFT -> {
                ctrlActive = !ctrlActive
                kv.setCtrlIndicator(ctrlActive)
            }
            LatinKeyboardView.KEYCODE_ALT_LEFT -> {
                altActive = !altActive
                kv.setAltIndicator(altActive)
            }
            LatinKeyboardView.KEYCODE_FN -> {
                fnActive = !fnActive
                kbd = LatinKeyboard(this, R.xml.kbd_qwerty, 0, 0.35f)
                kv.setKeyboard(kbd)
            }
            '\t'.code -> ic.commitText("\t", 1)
            '\n'.code -> {
                ic.commitText("\n", 1)
                shiftState = false
                kv.setShiftState(Keyboard.SHIFT_OFF)
            }
            Keyboard.KEYCODE_MODE_CHANGE -> {
                val xml = if (fnActive) R.xml.kbd_symbols else R.xml.kbd_qwerty
                kbd = LatinKeyboard(this, xml, 0, 0.35f)
                kv.setKeyboard(kbd)
            }
            else -> {
                val c = primaryCode.toChar()
                if (ctrlActive && c.isLetter()) {
                    when (c.lowercaseChar()) {
                        'c' -> ic.performContextMenuAction(android.R.id.copy)
                        'v' -> ic.performContextMenuAction(android.R.id.paste)
                        'x' -> ic.performContextMenuAction(android.R.id.cut)
                        'a' -> ic.performContextMenuAction(android.R.id.selectAll)
                        else -> ic.commitText(c.toString(), 1)
                    }
                    ctrlActive = false
                    kv.setCtrlIndicator(false)
                } else {
                    ic.commitText(c.toString(), 1)
                }
            }
        }
    }

    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeRight(): Boolean = false
    override fun swipeLeft(): Boolean = false
    override fun swipeDown(): Boolean = false
    override fun swipeUp(): Boolean = false
    override fun onPress(primaryCode: Int) {}
    override fun onCancel() {}
}
