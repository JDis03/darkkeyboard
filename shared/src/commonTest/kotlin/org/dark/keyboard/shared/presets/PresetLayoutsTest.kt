package org.dark.keyboard.shared.presets

import org.dark.keyboard.shared.serialization.LayoutSerializer
import org.dark.keyboard.shared.validation.LayoutValidator
import org.dark.keyboard.shared.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresetLayoutsTest {
    
    @Test
    fun `QWERTY preset is valid`() {
        val layout = PresetLayouts.qwertyPreset
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Success, 
            "QWERTY preset should be valid but got: ${(result as? ValidationResult.Error)?.allMessages}")
    }
    
    @Test
    fun `minimal example has correct dimensions`() {
        val layout = PresetLayouts.minimalExample
        
        assertEquals(500, layout.width)
        assertEquals(300, layout.height)
    }
    
    @Test
    fun `minimal example has all key types`() {
        val layout = PresetLayouts.minimalExample
        
        // Should have: number with shift, letter, backspace, shift, space, ctrl, enter = 7 keys
        assertEquals(7, layout.keys.size)
    }
    
    @Test
    fun `minimal example has modifier keys`() {
        val layout = PresetLayouts.minimalExample
        
        val modifiers = layout.keys.filter { it.isModifier }
        
        assertTrue(modifiers.any { it.label == "Shift" })
        assertTrue(modifiers.any { it.label == "Ctrl" })
    }
    
    @Test
    fun `QWERTY preset modifiers are sticky`() {
        val layout = PresetLayouts.qwertyPreset
        
        val shift = layout.keys.find { it.label == "Shift" }
        val ctrl = layout.keys.find { it.label == "Ctrl" }
        
        assertTrue(shift?.isSticky == true)
        assertTrue(ctrl?.isSticky == true)
    }
    
    @Test
    fun `QWERTY preset has repeatable backspace`() {
        val layout = PresetLayouts.qwertyPreset
        
        val backspace = layout.keys.find { it.code == -5 }
        
        assertTrue(backspace != null)
        assertTrue(backspace.repeatable)
    }
    
    @Test
    fun `QWERTY preset serializes to valid JSON`() {
        val layout = PresetLayouts.qwertyPreset
        
        val json = LayoutSerializer.toJson(layout)
        
        assertTrue(json.contains("\"name\": \"QWERTY Preset\""))
        assertTrue(json.isNotEmpty())
    }
    
    @Test
    fun `QWERTY preset round-trip serialization works`() {
        val original = PresetLayouts.qwertyPreset
        
        val json = LayoutSerializer.toJson(original)
        val deserialized = LayoutSerializer.fromJson(json)
        
        assertEquals(original.name, deserialized.name)
        assertEquals(original.keys.size, deserialized.keys.size)
        assertEquals(original.keys[0].label, deserialized.keys[0].label)
    }
    
    @Test
    fun `QWERTY preset keys are within bounds`() {
        val layout = PresetLayouts.qwertyPreset
        
        layout.keys.forEach { key ->
            assertTrue(key.x >= 0, "${key.label} has negative x")
            assertTrue(key.y >= 0, "${key.label} has negative y")
            assertTrue(key.x + key.width <= layout.width, 
                "${key.label} extends beyond width: ${key.x} + ${key.width} > ${layout.width}")
            assertTrue(key.y + key.height <= layout.height,
                "${key.label} extends beyond height: ${key.y} + ${key.height} > ${layout.height}")
        }
    }
}
