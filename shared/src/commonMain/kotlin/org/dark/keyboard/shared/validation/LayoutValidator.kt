package org.dark.keyboard.shared.validation

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel

/**
 * Validates keyboard layouts for correctness and usability.
 * 
 * This validator is shared between the web editor and Android app,
 * ensuring consistent validation rules on both platforms.
 */
object LayoutValidator {
    
    /**
     * Validate a layout for correctness and usability.
     * 
     * Checks:
     * - Required fields are present and valid
     * - Dimensions are positive
     * - Key count is within limits
     * - All keys have valid positions and sizes
     * - Keys are within layout bounds
     * - Modifier keys have correct configuration
     * 
     * @param layout The layout to validate
     * @return ValidationResult.Success if valid, ValidationResult.Error with messages if invalid
     */
    fun validate(layout: LayoutModel): ValidationResult {
        val errors = mutableListOf<String>()
        
        // Validate layout-level properties
        validateLayoutProperties(layout, errors)
        
        // Validate all keys
        layout.keys.forEachIndexed { index, key ->
            validateKey(key, index, layout, errors)
        }
        
        // Check for overlapping keys (warning level - allowed but suspicious)
        // Skip for now - may be intentional for popup zones
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    private fun validateLayoutProperties(layout: LayoutModel, errors: MutableList<String>) {
        // Name must not be blank
        if (layout.name.isBlank()) {
            errors.add("Layout name cannot be blank")
        }
        
        // Version must match current schema version
        if (layout.version != LayoutModel.CURRENT_VERSION) {
            errors.add("Layout version ${layout.version} does not match current version ${LayoutModel.CURRENT_VERSION}")
        }
        
        // Dimensions must be positive
        if (layout.width <= 0) {
            errors.add("Layout width must be positive (got ${layout.width})")
        }
        if (layout.height <= 0) {
            errors.add("Layout height must be positive (got ${layout.height})")
        }
        
        // Key count limits
        if (layout.keys.isEmpty()) {
            errors.add("Layout must have at least one key")
        }
        if (layout.keys.size > LayoutModel.MAX_KEYS) {
            errors.add("Layout has ${layout.keys.size} keys, maximum is ${LayoutModel.MAX_KEYS}")
        }
    }
    
    private fun validateKey(
        key: KeyModel,
        index: Int,
        layout: LayoutModel,
        errors: MutableList<String>
    ) {
        val keyRef = "Key[$index] '${key.label}'"
        
        // Label must not be blank (unless it's a special key like Space)
        if (key.label.isBlank() && key.code !in listOf(
            KeyModel.CODE_SPACE,
            KeyModel.CODE_DELETE,
            KeyModel.CODE_ENTER,
            KeyModel.CODE_TAB
        )) {
            errors.add("$keyRef: label cannot be blank")
        }
        
        // Position and size must be non-negative
        if (key.x < 0) {
            errors.add("$keyRef: x position cannot be negative (got ${key.x})")
        }
        if (key.y < 0) {
            errors.add("$keyRef: y position cannot be negative (got ${key.y})")
        }
        if (key.width <= 0) {
            errors.add("$keyRef: width must be positive (got ${key.width})")
        }
        if (key.height <= 0) {
            errors.add("$keyRef: height must be positive (got ${key.height})")
        }
        
        // Minimum size constraint (usability)
        if (key.width < LayoutModel.MIN_KEY_SIZE) {
            errors.add("$keyRef: width ${key.width}px is below minimum ${LayoutModel.MIN_KEY_SIZE}px")
        }
        if (key.height < LayoutModel.MIN_KEY_SIZE) {
            errors.add("$keyRef: height ${key.height}px is below minimum ${LayoutModel.MIN_KEY_SIZE}px")
        }
        
        // Keys must be within layout bounds
        if (key.x + key.width > layout.width) {
            errors.add("$keyRef: extends beyond layout width (x=${key.x} + width=${key.width} > ${layout.width})")
        }
        if (key.y + key.height > layout.height) {
            errors.add("$keyRef: extends beyond layout height (y=${key.y} + height=${key.height} > ${layout.height})")
        }
        
        // Validate modifier keys
        validateModifierKey(key, keyRef, errors)
        
        // Edge flags must be valid (0-15)
        if (key.edgeFlags < 0 || key.edgeFlags > 15) {
            errors.add("$keyRef: edgeFlags must be 0-15 (got ${key.edgeFlags})")
        }
    }
    
    private fun validateModifierKey(
        key: KeyModel,
        keyRef: String,
        errors: MutableList<String>
    ) {
        val modifierCodes = setOf(
            KeyModel.CODE_SHIFT,
            KeyModel.CODE_CTRL,
            KeyModel.CODE_ALT,
            KeyModel.CODE_FN
        )
        
        // If key has a modifier code, it should be marked as modifier
        if (key.code in modifierCodes && !key.isModifier) {
            errors.add("$keyRef: has modifier code ${key.code} but isModifier=false")
        }
        
        // Only modifiers should be sticky
        if (key.isSticky && !key.isModifier) {
            errors.add("$keyRef: isSticky=true but isModifier=false (only modifiers can be sticky)")
        }
        
        // Modifiers shouldn't be repeatable
        if (key.isModifier && key.repeatable) {
            errors.add("$keyRef: modifier keys should not be repeatable")
        }
    }
}
