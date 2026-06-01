package org.dark.keyboard.shared.model

import kotlinx.serialization.Serializable

/**
 * Represents a single key in a keyboard layout.
 * 
 * This model is compatible with Android's Key class and includes all necessary
 * properties for proper rendering and behavior, including modifier keys.
 * 
 * All position/size properties are in pixels for consistency with Android's rendering.
 */
@Serializable
data class KeyModel(
    /** Display label shown on the key */
    val label: String,
    
    /** Key code sent when pressed (e.g., 113 for 'q', 32 for space, -1 for Shift) */
    val code: Int,
    
    /** X position in pixels (left edge) */
    val x: Int,
    
    /** Y position in pixels (top edge) */
    val y: Int,
    
    /** Width in pixels */
    val width: Int,
    
    /** Height in pixels */
    val height: Int,
    
    /** Alternative label shown when Shift modifier is active (e.g., "!" when Shift + "1") */
    val shiftLabel: String? = null,
    
    /** Optional popup keys shown on long-press (e.g., ["á", "à", "â"]) */
    val popupKeys: List<String> = emptyList(),
    
    /** Whether this key is a modifier (Shift, Ctrl, Alt, Fn) - affects rendering and behavior */
    val isModifier: Boolean = false,
    
    /** Whether this modifier key is sticky (stays active after release until next key press) */
    val isSticky: Boolean = false,
    
    /** Whether this key repeats when held down (e.g., backspace, arrow keys) */
    val repeatable: Boolean = false,
    
    /** Edge flags for rendering borders (sum of EDGE_LEFT=1, EDGE_RIGHT=2, EDGE_TOP=4, EDGE_BOTTOM=8) */
    val edgeFlags: Int = 0
) {
    companion object {
        // Edge flags for rendering
        const val EDGE_LEFT = 1
        const val EDGE_RIGHT = 2
        const val EDGE_TOP = 4
        const val EDGE_BOTTOM = 8
        
        // Common modifier key codes (negative values)
        const val CODE_SHIFT = -1
        const val CODE_SYMBOL = -2
        const val CODE_CTRL = -113
        const val CODE_ALT = -57
        const val CODE_FN = -119
        const val CODE_DELETE = -5
        const val CODE_ENTER = 10
        const val CODE_SPACE = 32
        const val CODE_TAB = 9
    }
}
