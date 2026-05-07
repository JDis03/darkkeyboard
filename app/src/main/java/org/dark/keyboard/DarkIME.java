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
            // Log dimensiones de pantalla
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            Log.e("DK", "Display: " + dm.widthPixels + "x" + dm.heightPixels + " density=" + dm.density);
            
            // Calcular altura del teclado como HeliBoard:
            // Base: 205.6dp * 1.33 (con number row) = 273.4dp
            float defaultHeightDp = 205.6f * 1.33f;
            float defaultHeightPx = defaultHeightDp * dm.density;
            // Max: 46% de la altura de pantalla
            float maxHeight = dm.heightPixels * 0.46f;
            // Usar el menor de los dos
            float keyboardHeightPx = Math.min(defaultHeightPx, maxHeight);
            // Convertir a porcentaje para el constructor
            float heightPercent = (keyboardHeightPx / dm.heightPixels) * 100f;
            
            Log.e("DK", "Keyboard height calculation: default=" + defaultHeightPx + "px, max=" + maxHeight + "px, final=" + keyboardHeightPx + "px (" + heightPercent + "%)");
            
            // Crear el teclado desde XML
            kbd = new LatinKeyboard(this, R.xml.kbd_qwerty, 0, heightPercent);
            Log.e("DK", "Keyboard created: height=" + kbd.getHeight() + "px, total keys=" + kbd.getKeys().size() + ", layoutRows=" + kbd.mLayoutRows);
            
            // Inflar el layout COMPLETO (igual que HeliBoard - no extraer vista)
            android.view.ViewGroup root = (android.view.ViewGroup) getLayoutInflater()
                .inflate(R.layout.input_ics, null);
            
            // Encontrar el LatinKeyboardView DENTRO del layout inflado
            kv = root.findViewById(R.id.LatinkeyboardBaseView);
            kv.setKeyboard(kbd);
            kv.setOnKeyboardActionListener(this);
            
            // Aplicar window insets listener para detectar navigation bar
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.view.WindowInsets windowInsets = insets;
                int navBarHeight = windowInsets.getSystemWindowInsetBottom();
                Log.e("DK", "WindowInsets: navBar=" + navBarHeight + "px");
                
                // Aplicar padding adicional si hay navigation bar
                if (navBarHeight > 0) {
                    kv.setPadding(
                        kv.getPaddingLeft(),
                        kv.getPaddingTop(),
                        kv.getPaddingRight(),
                        kv.getPaddingBottom() + navBarHeight
                    );
                    Log.e("DK", "Applied navBar padding: " + navBarHeight + "px");
                }
                return insets;
            });
            
            Log.e("DK", "onCreateInputView OK - returning ROOT layout");
            // Devolver el layout COMPLETO, no solo el keyboard view
            return root;
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
            // TODO: calcular heightPercent dinámicamente (por ahora usar 30% conservador)
            kbd = new LatinKeyboard(this, fnActive ? R.xml.kbd_symbols : R.xml.kbd_qwerty, 0, 30f);
            kv.setKeyboard(kbd);
        } else if (primaryCode == '\t') {
            ic.commitText("\t", 1);
        } else if (primaryCode == '\n') {
            ic.commitText("\n", 1);
            shiftState = false; kv.setShiftState(Keyboard.SHIFT_OFF);
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE) {
            kbd = new LatinKeyboard(this, R.xml.kbd_symbols, 0, 30f);
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
