package org.dark.keyboard.shared.serialization

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel
import kotlin.test.Test

/**
 * This test demonstrates the actual JSON output format.
 * Run this test and check the console output to see the JSON structure.
 */
class JsonOutputTest {
    
    @Test
    fun `print sample layout JSON for documentation`() {
        val layout = LayoutModel(
            name = "Sample QWERTY Layout",
            version = 1,
            width = 1080,
            height = 710,
            keys = listOf(
                // First row - number keys
                KeyModel(
                    label = "1",
                    code = 49,
                    x = 0,
                    y = 0,
                    width = 108,
                    height = 142,
                    shiftLabel = "!",
                    popupKeys = listOf("¹", "½", "⅓"),
                    edgeFlags = KeyModel.EDGE_LEFT or KeyModel.EDGE_TOP
                ),
                KeyModel(
                    label = "2",
                    code = 50,
                    x = 108,
                    y = 0,
                    width = 108,
                    height = 142,
                    shiftLabel = "@",
                    popupKeys = listOf("²"),
                    edgeFlags = KeyModel.EDGE_TOP
                ),
                
                // Modifier keys
                KeyModel(
                    label = "Shift",
                    code = KeyModel.CODE_SHIFT,
                    x = 0,
                    y = 555,
                    width = 180,
                    height = 149,
                    isModifier = true,
                    isSticky = true,
                    edgeFlags = KeyModel.EDGE_LEFT or KeyModel.EDGE_BOTTOM
                ),
                
                // Regular letter key
                KeyModel(
                    label = "A",
                    code = 97,
                    x = 54,
                    y = 185,
                    width = 108,
                    height = 142,
                    popupKeys = listOf("á", "à", "â", "ä", "ã", "å", "1")
                ),
                
                // Space bar
                KeyModel(
                    label = "Space",
                    code = KeyModel.CODE_SPACE,
                    x = 324,
                    y = 555,
                    width = 432,
                    height = 149,
                    edgeFlags = KeyModel.EDGE_BOTTOM
                ),
                
                // Backspace (repeatable)
                KeyModel(
                    label = "⌫",
                    code = KeyModel.CODE_DELETE,
                    x = 972,
                    y = 0,
                    width = 108,
                    height = 142,
                    repeatable = true,
                    edgeFlags = KeyModel.EDGE_RIGHT or KeyModel.EDGE_TOP
                )
            )
        )
        
        val json = LayoutSerializer.toJson(layout)
        
        println("=" * 80)
        println("Sample Layout JSON Output:")
        println("=" * 80)
        println(json)
        println("=" * 80)
        
        // This test always passes - it's just for documentation
        assert(json.isNotEmpty())
    }
}

private operator fun String.times(count: Int): String = this.repeat(count)
