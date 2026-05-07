package org.dark.keyboard
import java.util.Locale

object LatinIME {
    const val ASCII_SPACE = 32
    const val ASCII_ENTER = 10
    const val ASCII_PERIOD = 46
    @JvmField val sKeyboardSettings = KeyboardSettings()

    class KeyboardSettings {
        @JvmField val topRowScale = 1.0f
        @JvmField var inputLocale: Locale = Locale.getDefault()
        @JvmField val popupKeyboardFlags = 0
        @JvmField val useExtension = false
        @JvmField val renderMode = 0
        @JvmField val hintMode = 0
        @JvmField val longpressTimeout = 300
        @JvmField val labelScale = 1.0f
        @JvmField val labelScalePref = 1.0f
        @JvmField val shiftLockModifiers = false
        @JvmField val sendSlideKeys = 0
        @JvmField val showTouchPos = false
    }
}
