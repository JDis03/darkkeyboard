package org.dark.keyboard.shared.presets

import org.dark.keyboard.shared.validation.LayoutValidator
import org.dark.keyboard.shared.validation.ValidationResult
import kotlin.test.Test

class DebugPresetTest {
    
    @Test
    fun `debug QWERTY preset validation`() {
        val layout = PresetLayouts.qwertyPreset
        
        println("Layout: ${layout.name}")
        println("Dimensions: ${layout.width}x${layout.height}")
        println("Keys: ${layout.keys.size}")
        
        val result = LayoutValidator.validate(layout)
        
        when (result) {
            is ValidationResult.Success -> println("✓ Valid!")
            is ValidationResult.Error -> {
                println("✗ Validation failed:")
                result.messages.forEach { println("  - $it") }
            }
        }
    }
}
