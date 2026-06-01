package org.dark.keyboard.shared.validation

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LayoutValidatorTest {
    
    @Test
    fun `valid layout passes validation`() {
        val layout = LayoutModel(
            name = "Valid Layout",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel("A", 97, 0, 0, 100, 150)
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Success)
        assertTrue(result.isValid)
    }
    
    @Test
    fun `blank name fails validation`() {
        val layout = LayoutModel(
            name = "",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(KeyModel("A", 97, 0, 0, 100, 150))
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("name cannot be blank") })
    }
    
    @Test
    fun `wrong version fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 999,
            width = 1080,
            height = 720,
            keys = listOf(KeyModel("A", 97, 0, 0, 100, 150))
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("version") })
    }
    
    @Test
    fun `negative dimensions fail validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = -100,
            height = 720,
            keys = listOf(KeyModel("A", 97, 0, 0, 100, 150))
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("width must be positive") })
    }
    
    @Test
    fun `empty keys fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = emptyList()
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("at least one key") })
    }
    
    @Test
    fun `too many keys fails validation`() {
        val keys = (0..100).map { i ->
            KeyModel(i.toString(), i, 0, 0, 100, 150)
        }
        
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = keys
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("maximum is") })
    }
    
    @Test
    fun `key with negative position fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel("A", 97, -10, 0, 100, 150)
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("x position cannot be negative") })
    }
    
    @Test
    fun `key smaller than minimum fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel("A", 97, 0, 0, 50, 50) // Below MIN_KEY_SIZE (60)
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("below minimum") })
    }
    
    @Test
    fun `key outside layout bounds fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel("A", 97, 1000, 0, 100, 150) // x + width = 1100 > 1080
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("beyond layout width") })
    }
    
    @Test
    fun `modifier key with wrong isModifier flag fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "Shift",
                    code = KeyModel.CODE_SHIFT,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150,
                    isModifier = false // Should be true
                )
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("isModifier=false") })
    }
    
    @Test
    fun `sticky non-modifier fails validation`() {
        val layout = LayoutModel(
            name = "Test",
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
                    height = 150,
                    isSticky = true, // Should only be true for modifiers
                    isModifier = false
                )
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("only modifiers can be sticky") })
    }
    
    @Test
    fun `repeatable modifier fails validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "Shift",
                    code = KeyModel.CODE_SHIFT,
                    x = 0,
                    y = 0,
                    width = 100,
                    height = 150,
                    isModifier = true,
                    repeatable = true // Modifiers shouldn't repeat
                )
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("should not be repeatable") })
    }
    
    @Test
    fun `invalid edge flags fails validation`() {
        val layout = LayoutModel(
            name = "Test",
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
                    height = 150,
                    edgeFlags = 99 // Must be 0-15
                )
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.any { it.contains("edgeFlags must be 0-15") })
    }
    
    @Test
    fun `valid modifier key passes validation`() {
        val layout = LayoutModel(
            name = "Test",
            version = 1,
            width = 1080,
            height = 720,
            keys = listOf(
                KeyModel(
                    label = "Shift",
                    code = KeyModel.CODE_SHIFT,
                    x = 0,
                    y = 0,
                    width = 180,
                    height = 150,
                    isModifier = true,
                    isSticky = true,
                    edgeFlags = 9
                )
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Success)
    }
    
    @Test
    fun `ValidationResult Error has allMessages property`() {
        val error = ValidationResult.Error("Error 1", "Error 2", "Error 3")
        
        val allMessages = error.allMessages
        
        assertTrue(allMessages.contains("Error 1"))
        assertTrue(allMessages.contains("Error 2"))
        assertTrue(allMessages.contains("Error 3"))
    }
    
    @Test
    fun `multiple errors are all reported`() {
        val layout = LayoutModel(
            name = "", // Error 1
            version = 1,
            width = -100, // Error 2
            height = 720,
            keys = listOf(
                KeyModel("A", 97, -10, 0, 50, 50) // Error 3, 4, 5
            )
        )
        
        val result = LayoutValidator.validate(layout)
        
        assertTrue(result is ValidationResult.Error)
        assertTrue(result.messages.size >= 3) // At least 3 errors
    }
}
