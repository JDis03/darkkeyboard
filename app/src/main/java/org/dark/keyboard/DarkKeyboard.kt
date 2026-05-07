package org.dark.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView

class DarkKeyboard : InputMethodService(), LatinKeyboardBaseView.OnKeyboardActionListener {

    private lateinit var kv: LatinKeyboardView
    private lateinit var kbd: LatinKeyboard

    override fun onCreate() {
        super.onCreate()
        android.util.Log.e("DK", "onCreate")
    }

    override fun onCreateInputView(): View {
        android.util.Log.e("DK", "onCreateInputView START")
        try {
            kbd = LatinKeyboard(this, R.xml.kbd_qwerty, 0, 0.35f)
            val inflated = layoutInflater.inflate(R.layout.input_ics, null)
            kv = if (inflated is LatinKeyboardView) inflated 
                 else inflated.findViewById(org.dark.keyboard.R.id.LatinkeyboardBaseView)!!
            kv.setKeyboard(kbd)
            kv.setOnKeyboardActionListener(this)
            android.util.Log.e("DK", "onCreateInputView SUCCESS")
            return kv
        } catch (e: Exception) {
            android.util.Log.e("DK", "onCreateInputView FAILED: ${e.message}", e)
            return TextView(this).apply { text = "Error: ${e.message}" }
        }
    }

    override fun onBindInput() {
        super.onBindInput()
        android.util.Log.e("DK", "onBindInput")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        android.util.Log.e("DK", "onStartInputView")
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray, x: Int, y: Int) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_SHIFT -> kv.setShiftState(if (kv.keyboard?.shiftState == Keyboard.SHIFT_ON) Keyboard.SHIFT_OFF else Keyboard.SHIFT_ON)
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
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
