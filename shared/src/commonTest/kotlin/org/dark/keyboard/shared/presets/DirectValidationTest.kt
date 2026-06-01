package org.dark.keyboard.shared.presets

import org.dark.keyboard.shared.validation.LayoutValidator
import org.dark.keyboard.shared.validation.ValidationResult
import kotlin.test.Test

class DirectValidationTest {
    
    @Test
    fun `validate qwerty and print all errors`() {
        val layout = PresetLayouts.qwertyPreset
        val result = LayoutValidator.validate(layout)
        
        if (result is ValidationResult.Error) {
            // Force test to fail but with full error messages
            val errorMessage = buildString {
                appendLine("QWERTY Preset Validation Errors:")
                result.messages.forEach { appendLine("  - $it") }
            }
            throw AssertionError(errorMessage)
        }
        
        // If we get here, validation passed
        assert(result.isValid)
    }
}
