package org.dark.keyboard.editor

import org.dark.keyboard.shared.model.LayoutModel
import org.dark.keyboard.shared.serialization.LayoutSerializer
import org.dark.keyboard.shared.validation.LayoutValidator
import org.dark.keyboard.shared.validation.ValidationResult
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * Utilities for importing layouts from files
 */
object FileImporter {
    /**
     * Open file picker and import JSON layout
     */
    fun importJson(
        onSuccess: (LayoutModel) -> Unit,
        onError: (String) -> Unit
    ) {
        // Create hidden file input
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = ".json,application/json"
        input.style.display = "none"
        
        input.onchange = { event ->
            val file = input.files?.get(0)
            if (file != null) {
                readJsonFile(file, onSuccess, onError)
            }
            // Clean up
            document.body?.removeChild(input)
        }
        
        // Trigger file picker
        document.body?.appendChild(input)
        input.click()
    }
    
    /**
     * Read and parse JSON file
     */
    private fun readJsonFile(
        file: org.w3c.files.File,
        onSuccess: (LayoutModel) -> Unit,
        onError: (String) -> Unit
    ) {
        val reader = FileReader()
        
        reader.onload = { event ->
            try {
                val jsonString = reader.result as String
                
                // Deserialize JSON to LayoutModel
                val layout = LayoutSerializer.fromJson(jsonString)
                
                // Validate layout
                when (val result = LayoutValidator.validate(layout)) {
                    is ValidationResult.Success -> {
                        onSuccess(layout)
                    }
                    is ValidationResult.Error -> {
                        onError("Invalid layout: ${result.messages.joinToString(", ")}")
                    }
                }
            } catch (e: Exception) {
                onError("Failed to parse JSON: ${e.message}")
            }
        }
        
        reader.onerror = {
            onError("Failed to read file: ${reader.error?.message}")
        }
        
        reader.readAsText(file)
    }
    
    /**
     * Import layout from clipboard
     */
    fun importFromClipboard(
        onSuccess: (LayoutModel) -> Unit,
        onError: (String) -> Unit
    ) {
        kotlinx.browser.window.navigator.asDynamic().clipboard?.readText()
            ?.then { text: String ->
                try {
                    val layout = LayoutSerializer.fromJson(text)
                    
                    when (val result = LayoutValidator.validate(layout)) {
                        is ValidationResult.Success -> {
                            onSuccess(layout)
                        }
                        is ValidationResult.Error -> {
                            onError("Invalid layout: ${result.messages.joinToString(", ")}")
                        }
                    }
                } catch (e: Exception) {
                    onError("Failed to parse JSON: ${e.message}")
                }
            }
            ?.catch { error: dynamic ->
                onError("Failed to read clipboard: ${error.message}")
            }
    }
}
