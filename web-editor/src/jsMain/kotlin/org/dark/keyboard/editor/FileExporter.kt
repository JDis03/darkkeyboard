package org.dark.keyboard.editor

import org.dark.keyboard.shared.model.LayoutModel
import org.dark.keyboard.shared.serialization.LayoutSerializer
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement

/**
 * Utilities for exporting layouts as files
 */
object FileExporter {
    /**
     * Download layout as JSON file
     */
    fun downloadJson(layout: LayoutModel, filename: String = "${layout.name}.json") {
        // Serialize layout to JSON
        val jsonString = LayoutSerializer.toJson(layout)
        
        // Create blob with JSON content
        val blob = Blob(
            arrayOf(jsonString),
            BlobPropertyBag(type = "application/json")
        )
        
        // Create download URL
        val url = URL.createObjectURL(blob)
        
        // Create temporary anchor element and trigger download
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = filename
        anchor.style.display = "none"
        
        document.body?.appendChild(anchor)
        anchor.click()
        document.body?.removeChild(anchor)
        
        // Clean up
        URL.revokeObjectURL(url)
    }
    
    /**
     * Copy layout JSON to clipboard
     */
    fun copyToClipboard(layout: LayoutModel, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val jsonString = LayoutSerializer.toJson(layout)
        
        // Use clipboard API
        window.navigator.asDynamic().clipboard?.writeText(jsonString)
            ?.then {
                onSuccess()
            }
            ?.catch { error: dynamic ->
                onError("Failed to copy: ${error.message}")
            }
    }
}
