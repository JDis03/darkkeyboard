package org.dark.keyboard.shared.validation

/**
 * Result of layout validation.
 * 
 * Use pattern matching to handle success/error cases:
 * ```kotlin
 * when (val result = LayoutValidator.validate(layout)) {
 *     is ValidationResult.Success -> // layout is valid
 *     is ValidationResult.Error -> println(result.messages)
 * }
 * ```
 */
sealed class ValidationResult {
    /**
     * Validation passed - layout is valid and safe to use.
     */
    data object Success : ValidationResult()
    
    /**
     * Validation failed - layout has errors that must be fixed.
     * 
     * @property messages List of error messages describing the problems
     */
    data class Error(val messages: List<String>) : ValidationResult() {
        constructor(vararg messages: String) : this(messages.toList())
        
        /**
         * All error messages joined into a single string.
         */
        val allMessages: String
            get() = messages.joinToString("\n")
    }
    
    /**
     * Check if validation was successful.
     */
    val isValid: Boolean
        get() = this is Success
}
