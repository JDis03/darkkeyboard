package org.dark.keyboard;

import android.inputmethodservice.InputMethodService;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class DarkIME extends InputMethodService {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("DK", "Java: onCreate");
    }
    
    @Override
    public View onCreateInputView() {
        Log.e("DK", "Java: onCreateInputView");
        TextView tv = new TextView(this);
        tv.setText("DARK KEYBOARD WORKS!");
        tv.setTextSize(32);
        tv.setBackgroundColor(0xFF1A1A1A);
        tv.setTextColor(0xFFFFFFFF);
        return tv;
    }
    
    @Override
    public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        Log.e("DK", "Java: onStartInputView");
    }
}
