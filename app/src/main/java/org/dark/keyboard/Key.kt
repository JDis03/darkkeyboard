package org.dark.keyboard

data class Key(
    val label: String?,
    val code: Int,
    val shiftLabel: String? = null,
    val popupCharacters: String? = null,
    var x: Int,
    var y: Int,
    var width: Int,
    var height: Int,
    val isModifier: Boolean = false,
    val isSticky: Boolean = false,
    val isRepeatable: Boolean = false,
    val edgeFlags: Int = 0
) {
    fun contains(px: Int, py: Int): Boolean =
        px >= x && px < x + width && py >= y && py < y + height

    companion object {
        const val EDGE_LEFT = 1
        const val EDGE_RIGHT = 2
        const val EDGE_TOP = 4
        const val EDGE_BOTTOM = 8

        const val CODE_SHIFT = -1
        const val CODE_MODE_CHANGE = -2
        const val CODE_CANCEL = -3
        const val CODE_CLOSE = -3          // Same as CANCEL - close keyboard
        const val CODE_SWITCH_INPUT = -4   // Switch to another IME
        const val CODE_DELETE = -5
        const val CODE_ALT_LEFT = -57
        const val CODE_ESCAPE = -111
        const val CODE_FORWARD_DEL = -112
        const val CODE_CTRL_LEFT = -113
        const val CODE_CAPS_LOCK = -115
        const val CODE_META_LEFT = -117
        const val CODE_FN = -119
        const val CODE_HOME = -122
        const val CODE_END = -123
        const val CODE_INSERT = -124
        const val CODE_PAGE_UP = -92
        const val CODE_PAGE_DOWN = -93
        const val CODE_DPAD_UP = -19
        const val CODE_DPAD_DOWN = -20
        const val CODE_DPAD_LEFT = -21
        const val CODE_DPAD_RIGHT = -22
        const val CODE_SETTINGS = -100
        const val CODE_VOICE = -102
        const val CODE_F1 = -103
        const val CODE_CLIPBOARD = -106
        const val CODE_TAB = 9
        const val CODE_ENTER = 10
        const val CODE_SPACE = 32
    }
}