package org.dark.keyboard.shared.presets

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel

/**
 * Predefined layouts for testing and quick start.
 * 
 * Standard Android keyboard dimensions:
 * - Width: 1080px (typical phone width in portrait)
 * - Row height: 160px
 * - Key width: 108px (1080 / 10 columns)
 */
object PresetLayouts {
    
    /**
     * Minimal example layout with all key types.
     * 
     * Demonstrates:
     * - Regular keys (letter keys)
     * - Number keys with shift labels
     * - Modifier keys (Shift, Ctrl)
     * - Special keys (Space, Enter, Backspace)
     * - Popup keys
     * - Edge flags
     * 
     * Size: 500x300px (small for testing)
     */
    val minimalExample: LayoutModel by lazy {
        LayoutModel(
            name = "Minimal Example",
            version = 1,
            width = 500,
            height = 300,
            keys = listOf(
                // Row 1: Number key with shift label
                KeyModel(
                    label = "1",
                    code = 49,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 100,
                    shiftLabel = "!",
                    popupKeys = listOf("¹", "½"),
                    edgeFlags = KeyModel.EDGE_LEFT or KeyModel.EDGE_TOP
                ),
                
                // Regular letter key
                KeyModel(
                    label = "Q",
                    code = 113,
                    x = 100,
                    y = 0,
                    width = 100,
                    height = 100,
                    popupKeys = listOf("1"),
                    edgeFlags = KeyModel.EDGE_TOP
                ),
                
                // Backspace (repeatable)
                KeyModel(
                    label = "⌫",
                    code = KeyModel.CODE_DELETE,
                    x = 200,
                    y = 0,
                    width = 100,
                    height = 100,
                    repeatable = true,
                    edgeFlags = KeyModel.EDGE_TOP or KeyModel.EDGE_RIGHT
                ),
                
                // Row 2: Shift modifier (sticky)
                KeyModel(
                    label = "Shift",
                    code = KeyModel.CODE_SHIFT,
                    x = 0,
                    y = 100,
                    width = 150,
                    height = 100,
                    isModifier = true,
                    isSticky = true,
                    edgeFlags = KeyModel.EDGE_LEFT
                ),
                
                // Space bar
                KeyModel(
                    label = "Space",
                    code = KeyModel.CODE_SPACE,
                    x = 150,
                    y = 100,
                    width = 200,
                    height = 100
                ),
                
                // Row 3: Ctrl modifier
                KeyModel(
                    label = "Ctrl",
                    code = KeyModel.CODE_CTRL,
                    x = 0,
                    y = 200,
                    width = 150,
                    height = 100,
                    isModifier = true,
                    isSticky = true,
                    edgeFlags = KeyModel.EDGE_LEFT or KeyModel.EDGE_BOTTOM
                ),
                
                // Enter
                KeyModel(
                    label = "Enter",
                    code = KeyModel.CODE_ENTER,
                    x = 150,
                    y = 200,
                    width = 150,
                    height = 100,
                    edgeFlags = KeyModel.EDGE_BOTTOM or KeyModel.EDGE_RIGHT
                )
            )
        )
    }
    
    /**
     * QWERTY layout matching DarkKeyboard kbd_pc.xml (QWERTY Standard).
     * Gboard-style proportions: 5 rows with extension numbers
     * - Row 1 (extension): 1-0 with popups (10 keys @ 10% = 100%)
     * - Row 2: QWERTYUIOP (10 keys @ 10%)
     * - Row 3: ASDFGHJKL (9 keys @ 10%, auto-centered)
     * - Row 4: Shift(15%) + ZXCVBNM(7×10%) + Del(15%)
     * - Row 5: Ctrl(12%) + Sym(12%) + Space(44%) + .(12%) + Enter(20%)
     */
    val qwertyPreset: LayoutModel by lazy {
        val baseWidth = 108 // 10% of 1080px
        val keyHeight = 143 // 716 / 5 rows
        val keys = mutableListOf<KeyModel>()
        
        // Row 1 (extension): Numbers 1-0 with popups (10 keys @ 10% each)
        val row1 = listOf(
            "1" to "!`", "2" to "@~", "3" to "#-", "4" to "\$_", "5" to "%=",
            "6" to "^+", "7" to "{&", "8" to "}*", "9" to "([", "0" to ")]"
        )
        row1.forEachIndexed { index, (label, popups) ->
            keys.add(
                KeyModel(
                    label = label,
                    code = label[0].code, // Use actual character code
                    x = index * baseWidth,
                    y = 0,
                    width = baseWidth,
                    height = keyHeight,
                    popupKeys = popups.map { it.toString() },
                    edgeFlags = when (index) {
                        0 -> KeyModel.EDGE_LEFT or KeyModel.EDGE_TOP
                        9 -> KeyModel.EDGE_TOP or KeyModel.EDGE_RIGHT
                        else -> KeyModel.EDGE_TOP
                    }
                )
            )
        }
        
        // Row 2: QWERTYUIOP (10 keys @ 10%)
        "qwertyuiop".forEachIndexed { index, char ->
            keys.add(
                KeyModel(
                    label = char.toString(),
                    code = char.code,
                    x = index * baseWidth,
                    y = keyHeight,
                    width = baseWidth,
                    height = keyHeight,
                    edgeFlags = when (index) {
                        0 -> KeyModel.EDGE_LEFT
                        9 -> KeyModel.EDGE_RIGHT
                        else -> 0
                    }
                )
            )
        }
        
        // Row 3: ASDFGHJKL (9 keys @ 10%, auto-centered with 5% gap on each side)
        "asdfghjkl".forEachIndexed { index, char ->
            keys.add(
                KeyModel(
                    label = char.toString(),
                    code = char.code,
                    x = index * baseWidth + (baseWidth / 2), // 5% offset for centering
                    y = keyHeight * 2,
                    width = baseWidth,
                    height = keyHeight,
                    edgeFlags = when (index) {
                        0 -> KeyModel.EDGE_LEFT
                        8 -> KeyModel.EDGE_RIGHT
                        else -> 0
                    }
                )
            )
        }
        
        // Row 4: Shift(15%) + ZXCVBNM(7×10%) + Del(15%)
        keys.add(
            KeyModel(
                label = "⇧",
                code = KeyModel.CODE_SHIFT,
                x = 0,
                y = keyHeight * 3,
                width = (baseWidth * 1.5).toInt(), // 15%
                height = keyHeight,
                isModifier = true,
                isSticky = true,
                edgeFlags = KeyModel.EDGE_LEFT
            )
        )
        "zxcvbnm".forEachIndexed { index, char ->
            keys.add(
                KeyModel(
                    label = char.toString(),
                    code = char.code,
                    x = (baseWidth * 1.5).toInt() + (index * baseWidth),
                    y = keyHeight * 3,
                    width = baseWidth,
                    height = keyHeight
                )
            )
        }
        keys.add(
            KeyModel(
                label = "⌫",
                code = KeyModel.CODE_DELETE,
                x = (baseWidth * 8.5).toInt(),
                y = keyHeight * 3,
                width = (baseWidth * 1.5).toInt(), // 15%
                height = keyHeight,
                repeatable = true,
                edgeFlags = KeyModel.EDGE_RIGHT
            )
        )
        
        // Row 5: Ctrl(12%) + Sym(12%) + Space(44%) + .(12%) + Enter(20%)
        // Use exact percentage calculations to avoid rounding errors
        val ctrlWidth = (1080 * 0.12).toInt()     // 12% = 129.6 → 129
        val symWidth = (1080 * 0.12).toInt()      // 12% = 129.6 → 129
        val spaceWidth = (1080 * 0.44).toInt()    // 44% = 475.2 → 475
        val dotWidth = (1080 * 0.12).toInt()      // 12% = 129.6 → 129
        val enterWidth = 1080 - ctrlWidth - symWidth - spaceWidth - dotWidth // Exact remaining
        
        var xPos = 0
        keys.add(
            KeyModel(
                label = "Ctrl",
                code = KeyModel.CODE_CTRL,
                x = xPos,
                y = keyHeight * 4,
                width = ctrlWidth,
                height = keyHeight,
                isModifier = true,
                isSticky = true,
                edgeFlags = KeyModel.EDGE_LEFT or KeyModel.EDGE_BOTTOM
            )
        )
        xPos += ctrlWidth
        
        keys.add(
            KeyModel(
                label = "?123",
                code = KeyModel.CODE_SYMBOL,
                x = xPos,
                y = keyHeight * 4,
                width = symWidth,
                height = keyHeight,
                edgeFlags = KeyModel.EDGE_BOTTOM
            )
        )
        xPos += symWidth
        
        keys.add(
            KeyModel(
                label = " ",
                code = KeyModel.CODE_SPACE,
                x = xPos,
                y = keyHeight * 4,
                width = spaceWidth,
                height = keyHeight,
                edgeFlags = KeyModel.EDGE_BOTTOM
            )
        )
        xPos += spaceWidth
        
        keys.add(
            KeyModel(
                label = ".",
                code = 46, // '.'
                x = xPos,
                y = keyHeight * 4,
                width = dotWidth,
                height = keyHeight,
                edgeFlags = KeyModel.EDGE_BOTTOM
            )
        )
        xPos += dotWidth
        
        keys.add(
            KeyModel(
                label = "↵",
                code = KeyModel.CODE_ENTER,
                x = xPos,
                y = keyHeight * 4,
                width = enterWidth,
                height = keyHeight,
                edgeFlags = KeyModel.EDGE_RIGHT or KeyModel.EDGE_BOTTOM
            )
        )
        
        LayoutModel(
            name = "QWERTY Standard",
            version = 1,
            width = 1080,
            height = keyHeight * 5,
            keys = keys
        )
    }
    
    /**
     * Dvorak preset - not implemented yet.
     * Will be added by web editor or manually created.
     */
    val dvorakPreset: LayoutModel
        get() = minimalExample.copy(name = "Dvorak Preset")
    
    /**
     * Programmer preset - not implemented yet.
     * Will be added by web editor or manually created.
     */
    val programmerPreset: LayoutModel
        get() = minimalExample.copy(name = "Programmer Preset")
}
