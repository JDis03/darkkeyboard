package org.dark.keyboard;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;

public class DarkIME extends InputMethodService implements LatinKeyboardBaseView.OnKeyboardActionListener {

    private LatinKeyboardView kv;
    private LatinKeyboard kbd;
    private boolean shiftState, ctrlActive, altActive, fnActive;

    @Override public void onCreate() {
        super.onCreate();
        Log.e("DK", "onCreate");
    }

    @Override public View onCreateInputView() {
        Log.e("DK", "onCreateInputView START");
        try {
            kbd = new LatinKeyboard(this, R.xml.kbd_qwerty, 0, 0.35f);
            kv = (LatinKeyboardView) getLayoutInflater().inflate(R.layout.input_ics, null)
                .findViewById(R.id.LatinkeyboardBaseView);
            kv.setKeyboard(kbd);
            kv.setOnKeyboardActionListener(this);
            kv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            Log.e("DK", "onCreateInputView OK");
            return kv;
        } catch (Exception e) {
            Log.e("DK", "ERROR: " + e.getMessage(), e);
            android.widget.TextView tv = new android.widget.TextView(this);
            tv.setText("DK Error: " + e.getMessage());
            return tv;
        }
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.e("DK", "onStartInputView");
    }

    @Override public void onKey(int primaryCode, int[] keyCodes, int x, int y) {
        var ic = getCurrentInputConnection();
        if (ic == null) return;

        if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            shiftState = !shiftState;
            kv.setShiftState(shiftState ? Keyboard.SHIFT_ON : Keyboard.SHIFT_OFF);
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            ic.deleteSurroundingText(1, 0);
        } else if (primaryCode == LatinKeyboardView.KEYCODE_CTRL_LEFT) {
            ctrlActive = !ctrlActive; kv.setCtrlIndicator(ctrlActive);
        } else if (primaryCode == LatinKeyboardView.KEYCODE_ALT_LEFT) {
            altActive = !altActive; kv.setAltIndicator(altActive);
        } else if (primaryCode == LatinKeyboardView.KEYCODE_FN) {
            fnActive = !fnActive;
            kbd = new LatinKeyboard(this, fnActive ? R.xml.kbd_symbols : R.xml.kbd_qwerty, 0, 0.35f);
            kv.setKeyboard(kbd);
        } else if (primaryCode == '\t') {
            ic.commitText("\t", 1);
        } else if (primaryCode == '\n') {
            ic.commitText("\n", 1);
            shiftState = false; kv.setShiftState(Keyboard.SHIFT_OFF);
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            kbd = new LatinKeyboard(this, R.xml.kbd_symbols, 0, 0.35f);
            kv.setKeyboard(kbd);
        } else if (ctrlActive && primaryCode >= 'A' && primaryCode <= 'Z') {
            // Ctrl shortcuts
            switch (primaryCode) {
                case 'C': ic.performContextMenuAction(android.R.id.copy); break;
                case 'V': ic.performContextMenuAction(android.R.id.paste); break;
                case 'X': ic.performContextMenuAction(android.R.id.cut); break;
                case 'A': ic.performContextMenuAction(android.R.id.selectAll); break;
                default: ic.commitText(String.valueOf((char) primaryCode), 1);
            }
            ctrlActive = false; kv.setCtrlIndicator(false);
        } else {
            ic.commitText(String.valueOf((char) primaryCode), 1);
        }
    }

    @Override public void onRelease(int primaryCode) {}
    @Override public void onText(CharSequence text) {}
    @Override public boolean swipeRight() { return false; }
    @Override public boolean swipeLeft() { return false; }
    @Override public boolean swipeDown() { return false; }
    @Override public boolean swipeUp() { return false; }
    @Override public void onPress(int primaryCode) {}
    @Override public void onCancel() {}
}
