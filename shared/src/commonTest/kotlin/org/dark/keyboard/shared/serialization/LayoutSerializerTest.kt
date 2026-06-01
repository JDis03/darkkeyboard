package org.dark.keyboard.shared.serialization

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutSerializerTest {
    
    @Test
    fun `serialize empty layout to JSON`() {
        val layout = LayoutModel(
            name = "Empty Layout",
            version = 1,
            width = 1080,
            height = 720,
            keys = emptyList()
        )
        
        val json = LayoutSerializer.toJson(layout)
        
        assertTrue(json.contains("\"name\": \"Empty Layout\""))
        assertTrue(json.contains("\"version\": 1"))
        assertTrue(json.contains("\"width\": 1080"))
        assertTrue(json.contains("\"height\": 720"))
        assertTrue(json.contains("\"keys\": []"))
    }
    
    @Test
    fun `deserialize empty layout from JSON`() {
        val json = """
            {
              "name": "Test Layout",
              "version": 1,
              "width": 1080,
              "height": 720,
              "keys": []
            }
        """.trimIndent()
        
        val layout = LayoutSerializer.fromJson(json)
        
        assertEquals("Test Layout", layout.name)
        assertEquals(1, layout.version)
        assertEquals(1080, layout.width)
        assertEquals(720, layout.height)
        assertEquals(0, layout.keys.size)
    }
    
    @Test
    fun `round-trip serialization preserves data`() {
        val original = LayoutModel(
            name = "Round Trip Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "Q",
                    code = 113,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150
                ),
                KeyModel(
                    label = "W",
                    code = 119,
                    x = 100,
                    y = 0,
                    width = 100,
                    height = 150
                )
            )
        )
        
        val json = LayoutSerializer.toJson(original)
        val deserialized = LayoutSerializer.fromJson(json)
        
        assertEquals(original.name, deserialized.name)
        assertEquals(original.version, deserialized.version)
        assertEquals(original.width, deserialized.width)
        assertEquals(original.height, deserialized.height)
        assertEquals(original.keys.size, deserialized.keys.size)
        assertEquals(original.keys[0].label, deserialized.keys[0].label)
        assertEquals(original.keys[1].code, deserialized.keys[1].code)
    }
    
    @Test
    fun `serialize key with all properties`() {
        val layout = LayoutModel(
            name = "Full Key Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "1",
                    code = 49,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150,
                    shiftLabel = "!",
                    popupKeys = listOf("¹", "½", "⅓"),
                    isModifier = false,
                    isSticky = false,
                    repeatable = false,
                    edgeFlags = 5 // EDGE_LEFT | EDGE_TOP
                )
            )
        )
        
        val json = LayoutSerializer.toJson(layout)
        val deserialized = LayoutSerializer.fromJson(json)
        
        val key = deserialized.keys[0]
        assertEquals("1", key.label)
        assertEquals(49, key.code)
        assertEquals("!", key.shiftLabel)
        assertEquals(3, key.popupKeys.size)
        assertEquals("¹", key.popupKeys[0])
        assertEquals(false, key.isModifier)
        assertEquals(false, key.isSticky)
        assertEquals(false, key.repeatable)
        assertEquals(5, key.edgeFlags)
    }
    
    @Test
    fun `serialize modifier key`() {
        val layout = LayoutModel(
            name = "Modifier Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "Shift",
                    code = KeyModel.CODE_SHIFT,
                    x = 0,
                    y = 555,
                    width = 180,
                    height = 149,
                    isModifier = true,
                    isSticky = true,
                    edgeFlags = 9 // EDGE_LEFT | EDGE_BOTTOM
                )
            )
        )
        
        val json = LayoutSerializer.toJson(layout)
        val deserialized = LayoutSerializer.fromJson(json)
        
        val shift = deserialized.keys[0]
        assertEquals("Shift", shift.label)
        assertEquals(-1, shift.code)
        assertTrue(shift.isModifier)
        assertTrue(shift.isSticky)
        assertEquals(9, shift.edgeFlags)
    }
    
    @Test
    fun `deserialize handles unknown fields gracefully`() {
        val json = """
            {
              "name": "Future Layout",
              "version": 1,
              "width": 1080,
              "height": 720,
              "keys": [],
              "unknownField": "some value",
              "anotherUnknownField": 42
            }
        """.trimIndent()
        
        // Should not throw - ignoreUnknownKeys = true
        val layout = LayoutSerializer.fromJson(json)
        
        assertEquals("Future Layout", layout.name)
    }
    
    @Test
    fun `serialize includes default values`() {
        val layout = LayoutModel(
            name = "Defaults Test",
            version = 1,
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
                    // All optional fields use defaults
                )
            )
        )
        
        val json = LayoutSerializer.toJson(layout)
        
        // Because encodeDefaults = true, all fields should be present
        assertTrue(json.contains("\"shiftLabel\": null"))
        assertTrue(json.contains("\"popupKeys\": []"))
        assertTrue(json.contains("\"isModifier\": false"))
        assertTrue(json.contains("\"isSticky\": false"))
        assertTrue(json.contains("\"repeatable\": false"))
        assertTrue(json.contains("\"edgeFlags\": 0"))
    }
    
    @Test
    fun `layout with 10 keys serializes correctly`() {
        val keys = (0 until 10).map { i ->
            KeyModel(
                label = i.toString(),
                code = 48 + i, // '0'..'9'
                x = i * 100,
                y = 0,
                width = 100,
                height = 150
            )
        }
        
        val layout = LayoutModel(
            name = "10 Keys",
            version = 1,
            width = 1000,
            height = 150,
            keys = keys
        )
        
        val json = LayoutSerializer.toJson(layout)
        val deserialized = LayoutSerializer.fromJson(json)
        
        assertEquals(10, deserialized.keys.size)
        assertEquals("0", deserialized.keys[0].label)
        assertEquals("9", deserialized.keys[9].label)
    }
}
