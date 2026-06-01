package org.dark.keyboard.shared.serialization

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel
import kotlin.test.Test
import kotlin.test.assertTrue

class JsonFormatTest {
    
    @Test
    fun `JSON output is pretty printed`() {
        val layout = LayoutModel(
            name = "Test",
            width = 1080,
            height = 720,
            keys = emptyList()
        )
        
        val json = LayoutSerializer.toJson(layout)
        
        // Pretty printed JSON should have newlines and indentation
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("  ")) // 2-space indentation
    }
    
    @Test
    fun `JSON includes all key properties even when default`() {
        val layout = LayoutModel(
            name = "Defaults",
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "A",
                    code = 97,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150
                )
            )
        )
        
        val json = LayoutSerializer.toJson(layout)
        
        // encodeDefaults = true means all fields are present
        assertTrue(json.contains("\"shiftLabel\""))
        assertTrue(json.contains("\"popupKeys\""))
        assertTrue(json.contains("\"isModifier\""))
        assertTrue(json.contains("\"isSticky\""))
        assertTrue(json.contains("\"repeatable\""))
        assertTrue(json.contains("\"edgeFlags\""))
    }
    
    @Test
    fun `verify sample JSON structure`() {
        val layout = LayoutModel(
            name = "QWERTY Sample",
            version = 1,
            width = 1080,
            height = 710,
            keys = listOf(
                KeyModel(
                    label = "Shift",
                    code = -1,
                    x = 0,
                    y = 555,
                    width = 180,
                    height = 149,
                    isModifier = true,
                    isSticky = true,
                    edgeFlags = 9
                )
            )
        )
        
        val json = LayoutSerializer.toJson(layout)
        
        // Verify structure
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"name\": \"QWERTY Sample\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"width\": 1080"))
        assertTrue(json.contains("\"height\": 710"))
        assertTrue(json.contains("\"keys\": ["))
        assertTrue(json.contains("\"label\": \"Shift\""))
        assertTrue(json.contains("\"code\": -1"))
        assertTrue(json.contains("\"isModifier\": true"))
        assertTrue(json.contains("\"isSticky\": true"))
        assertTrue(json.contains("\"edgeFlags\": 9"))
    }
}
