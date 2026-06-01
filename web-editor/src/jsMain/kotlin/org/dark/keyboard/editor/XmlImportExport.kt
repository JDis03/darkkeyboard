package org.dark.keyboard.editor

import kotlinx.browser.document
import kotlinx.browser.window
import org.dark.keyboard.shared.model.LayoutModel
import org.dark.keyboard.shared.xml.SimpleXmlParser
import org.dark.keyboard.shared.xml.XmlLayoutGenerator
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * XML import/export utilities for web editor.
 * Handles conversion between Android XML and LayoutModel.
 */
object XmlImportExport {
    
    /**
     * Import XML from file picker dialog.
     */
    fun importXml(
        onSuccess: (LayoutModel) -> Unit,
        onError: (String) -> Unit
    ) {
        val input = document.createElement("input") as org.w3c.dom.HTMLInputElement
        input.type = "file"
        input.accept = ".xml,text/xml"
        
        input.onchange = {
            val file = input.files?.get(0)
            if (file != null) {
                val reader = FileReader()
                reader.onload = { event ->
                    try {
                        val xml = event.target.asDynamic().result as String
                        val layout = SimpleXmlParser.parse(xml)
                        onSuccess(layout)
                    } catch (e: Exception) {
                        onError("Failed to parse XML: ${e.message}")
                    }
                }
                reader.onerror = {
                    onError("Failed to read file")
                }
                reader.readAsText(file)
            }
            null
        }
        
        input.click()
    }
    
    /**
     * Import XML from clipboard.
     */
    fun importXmlFromClipboard(
        onSuccess: (LayoutModel) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            if (js("navigator.clipboard && navigator.clipboard.readText") != null) {
                window.navigator.clipboard.readText().then(
                    onFulfilled = { text ->
                        try {
                            val layout = SimpleXmlParser.parse(text)
                            onSuccess(layout)
                        } catch (e: Exception) {
                            onError("Failed to parse XML from clipboard: ${e.message}")
                        }
                    },
                    onRejected = {
                        onError("Failed to read clipboard. Make sure you granted clipboard permissions.")
                    }
                )
            } else {
                onError("Clipboard API not available. Use Import XML instead or enable HTTPS.")
            }
        } catch (e: dynamic) {
            onError("Clipboard API not supported: ${e.message}")
        }
    }
    
    /**
     * Export layout as XML and download.
     */
    fun exportXml(layout: LayoutModel) {
        val xml = XmlLayoutGenerator.generate(layout)
        val filename = "${layout.name.replace(" ", "_").lowercase()}.xml"
        downloadText(xml, filename, "text/xml")
    }
    
    /**
     * Copy XML to clipboard.
     */
    fun copyXmlToClipboard(
        layout: LayoutModel,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val xml = XmlLayoutGenerator.generate(layout)
        
        try {
            if (js("navigator.clipboard && navigator.clipboard.writeText") != null) {
                window.navigator.clipboard.writeText(xml).then(
                    onFulfilled = { onSuccess() },
                    onRejected = { onError("Failed to copy XML to clipboard") }
                )
            } else {
                onError("Clipboard API not available. Use View XML dialog instead.")
            }
        } catch (e: dynamic) {
            onError("Clipboard API not supported: ${e.message}")
        }
    }
    
    /**
     * Show XML in modal dialog for copy/paste.
     */
    fun showXmlDialog(layout: LayoutModel) {
        val xml = XmlLayoutGenerator.generate(layout)
        
        // Create modal overlay
        val overlay = document.createElement("div") as org.w3c.dom.HTMLDivElement
        overlay.style.apply {
            position = "fixed"
            top = "0"
            left = "0"
            width = "100%"
            height = "100%"
            background = "rgba(0,0,0,0.5)"
            display = "flex"
            alignItems = "center"
            justifyContent = "center"
            zIndex = "9999"
        }
        
        // Create dialog
        val dialog = document.createElement("div") as org.w3c.dom.HTMLDivElement
        dialog.style.apply {
            background = "white"
            borderRadius = "12px"
            padding = "24px"
            width = "80%"
            maxWidth = "800px"
            maxHeight = "80vh"
            display = "flex"
            flexDirection = "column"
            boxShadow = "0 20px 60px rgba(0,0,0,0.3)"
        }
        
        // Title
        val title = document.createElement("h2") as org.w3c.dom.HTMLHeadingElement
        title.textContent = "Android XML Layout"
        title.style.apply {
            margin = "0 0 16px 0"
            color = "#1f2937"
            fontSize = "24px"
            fontWeight = "700"
        }
        dialog.appendChild(title)
        
        // Textarea with XML
        val textarea = document.createElement("textarea") as HTMLTextAreaElement
        textarea.value = xml
        textarea.readOnly = true
        textarea.style.apply {
            flex = "1"
            fontFamily = "'Fira Code', 'Monaco', 'Consolas', monospace"
            fontSize = "13px"
            padding = "16px"
            border = "2px solid #e5e7eb"
            borderRadius = "8px"
            resize = "none"
            marginBottom = "16px"
        }
        dialog.appendChild(textarea)
        
        // Buttons
        val buttonContainer = document.createElement("div") as org.w3c.dom.HTMLDivElement
        buttonContainer.style.cssText = "display: flex; gap: 12px; justify-content: flex-end;"
        
        val copyButton = document.createElement("button") as org.w3c.dom.HTMLButtonElement
        copyButton.textContent = "📋 Copy to Clipboard"
        copyButton.style.cssText = "padding: 10px 20px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600;"
        copyButton.onclick = {
            try {
                // Try modern clipboard API first
                if (js("navigator.clipboard") != null) {
                    window.navigator.clipboard.writeText(xml).then(
                        onFulfilled = {
                            copyButton.textContent = "✅ Copied!"
                            window.setTimeout({
                                copyButton.textContent = "📋 Copy to Clipboard"
                            }, 2000)
                        }
                    )
                } else {
                    // Fallback: select text and use execCommand
                    textarea.select()
                    val success = js("document.execCommand('copy')") as Boolean
                    if (success) {
                        copyButton.textContent = "✅ Copied!"
                        window.setTimeout({
                            copyButton.textContent = "📋 Copy to Clipboard"
                        }, 2000)
                    } else {
                        copyButton.textContent = "❌ Copy failed"
                    }
                }
            } catch (e: dynamic) {
                console.error("Copy failed:", e)
                copyButton.textContent = "❌ Copy failed"
            }
        }
        
        val closeButton = document.createElement("button") as org.w3c.dom.HTMLButtonElement
        closeButton.textContent = "Close"
        closeButton.style.cssText = "padding: 10px 20px; background: #e5e7eb; color: #374151; border: none; border-radius: 8px; cursor: pointer; font-size: 14px; font-weight: 600;"
        closeButton.onclick = {
            document.body?.removeChild(overlay)
        }
        
        buttonContainer.appendChild(copyButton)
        buttonContainer.appendChild(closeButton)
        dialog.appendChild(buttonContainer)
        
        overlay.appendChild(dialog)
        document.body?.appendChild(overlay)
        
        // Close on overlay click
        overlay.onclick = { event ->
            if (event.target == overlay) {
                document.body?.removeChild(overlay)
            }
        }
        
        // Select all text for easy copying
        textarea.select()
    }
    
    private fun downloadText(content: String, filename: String, mimeType: String) {
        val blob = org.w3c.files.Blob(
            arrayOf(content),
            org.w3c.files.BlobPropertyBag(mimeType)
        )
        
        // Use DOM URL API
        val urlApi = js("window.URL || window.webkitURL")
        val url = urlApi.createObjectURL(blob) as String
        
        val a = document.createElement("a") as org.w3c.dom.HTMLAnchorElement
        a.href = url
        a.download = filename
        a.click()
        
        urlApi.revokeObjectURL(url)
    }
}
