package org.dark.keyboard
import android.content.SharedPreferences

open class KeyboardSwitcher : SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        @JvmField val MODE_TEXT = 0
        @JvmField val MODE_SYMBOLS = 1
        @JvmField val MODE_URL = 2
        @JvmField val MODE_EMAIL = 3
        @JvmField val MODE_IM = 4
        @JvmField val MODE_WEB = 5
        @JvmField val MODE_PHONE = 6
        @JvmField val MODE_NONE = -1
        @JvmStatic fun getInstance() = KeyboardSwitcher()
    }

    fun isInMomentaryAutoModeSwitchState() = false
    fun getKeyboardMode() = MODE_TEXT
    fun setKeyboardMode(mode: Int, opts: Int) {}
    fun makeKeyboards(force: Boolean) {}

    override fun onSharedPreferenceChanged(p0: SharedPreferences?, p1: String?) {}
}
