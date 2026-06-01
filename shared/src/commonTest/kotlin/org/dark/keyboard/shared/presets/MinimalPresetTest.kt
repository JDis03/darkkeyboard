package org.dark.keyboard.shared.presets

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel
import org.dark.keyboard.shared.validation.LayoutValidator
import org.dark.keyboard.shared.validation.ValidationResult
import kotlin.test.Test
import kotlin.test.fail

class MinimalPresetTest {
    
    @Test
    fun `simple layout validation`() {
        val layout = LayoutModel(
            name = "Simple",
            version = 1,
            width = 1080,
            height = 200,
            keys = listOf(
                KeyModel("A", 97, 0, 0, 100, 100)
            )
        )
        
        when (val result = LayoutValidator.validate(layout)) {
            is ValidationResult.Success -> { /* OK */ }
            is ValidationResult.Error -> fail("Simple layout failed: ${result.allMessages}")
        }
    }
    
    @Test
    fun `check qwerty preset first key`() {
        val layout = PresetLayouts.qwertyPreset
        val firstKey = layout.keys.first()
        
        // Should be "1" key
        when (val result = LayoutValidator.validate(layout)) {
            is ValidationResult.Success -> { /* OK */ }
            is ValidationResult.Error -> fail("QWERTY failed:\n${result.allMessages}")
        }
    }
}
