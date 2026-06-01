package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.*

/**
 * Modern toolbar with editor controls - Material Design inspired
 */
@Composable
fun Toolbar(state: LayoutEditorState) {
    // Main toolbar container
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("flex-direction", "column")
                property("background", "linear-gradient(135deg, #667eea 0%, #764ba2 100%)")
                property("box-shadow", "0 4px 6px rgba(0,0,0,0.1)")
                property("flex-shrink", "0")
            }
        }
    ) {
        // Top row: Title + status
        Div(
            attrs = {
                style {
                    property("display", "flex")
                    property("justify-content", "space-between")
                    property("align-items", "center")
                    property("padding", "16px 24px 8px 24px")
                }
            }
        ) {
            // Title
            Div(
                attrs = {
                    style {
                        property("display", "flex")
                        property("align-items", "center")
                        property("gap", "12px")
                    }
                }
            ) {
                Span(
                    attrs = {
                        style {
                            property("font-size", "24px")
                            property("color", "white")
                            property("font-weight", "700")
                            property("letter-spacing", "-0.5px")
                        }
                    }
                ) {
                    Text("DarkKeyboard Editor")
                }
                Span(
                    attrs = {
                        style {
                            property("background", "rgba(255,255,255,0.2)")
                            property("color", "white")
                            property("padding", "4px 10px")
                            property("border-radius", "12px")
                            property("font-size", "12px")
                            property("font-weight", "600")
                        }
                    }
                ) {
                    Text(state.layout.name)
                }
            }
            
            // Status badge
            StatusBadge(state)
        }
        
        // Bottom row: Controls
        Div(
            attrs = {
                style {
                    property("display", "flex")
                    property("gap", "12px")
                    property("padding", "8px 24px 16px 24px")
                    property("align-items", "center")
                    property("flex-wrap", "wrap")
                }
            }
        ) {
            // Mode buttons group
            Div(
                attrs = {
                    style {
                        property("display", "flex")
                        property("gap", "4px")
                        property("background", "rgba(255,255,255,0.15)")
                        property("padding", "4px")
                        property("border-radius", "8px")
                    }
                }
            ) {
                ModeButton(
                    label = "✋ Select",
                    active = state.mode == EditorMode.SELECT,
                    onClick = { state.setMode(EditorMode.SELECT) }
                )
                
                ModeButton(
                    label = "+ Add",
                    active = state.mode == EditorMode.ADD,
                    onClick = { state.setMode(EditorMode.ADD) }
                )
                
                ModeButton(
                    label = "🗑 Delete",
                    active = state.mode == EditorMode.DELETE,
                    onClick = { state.setMode(EditorMode.DELETE) }
                )
                
                ModeButton(
                    label = "⇔ Resize",
                    active = state.mode == EditorMode.RESIZE,
                    onClick = { state.setMode(EditorMode.RESIZE) }
                )
            }
            
            // Preset selector
            PresetSelector(state)
            
            // Divider
            Div(
                attrs = {
                    style {
                        property("width", "2px")
                        property("height", "32px")
                        property("background", "rgba(255,255,255,0.2)")
                        property("margin", "0 8px")
                    }
                }
            )
            
            // XML buttons group
            Div(
                attrs = {
                    style {
                        property("display", "flex")
                        property("gap", "8px")
                    }
                }
            ) {
                ActionButton(
                    label = "📤 Import XML",
                    onClick = { 
                        XmlImportExport.importXml(
                            onSuccess = { layout ->
                                state.updateLayout(layout)
                                console.log("XML imported: ${layout.name}")
                            },
                            onError = { error ->
                                console.error("Import XML failed: $error")
                            }
                        )
                    }
                )
                
                ActionButton(
                    label = "📋 Paste XML",
                    onClick = { 
                        XmlImportExport.importXmlFromClipboard(
                            onSuccess = { layout ->
                                state.updateLayout(layout)
                                console.log("XML pasted: ${layout.name}")
                            },
                            onError = { error ->
                                console.error("Paste XML failed: $error")
                            }
                        )
                    }
                )
                
                ActionButton(
                    label = "📱 View XML",
                    onClick = { 
                        XmlImportExport.showXmlDialog(state.layout)
                    }
                )
            }
            
            // Action buttons group (JSON)
            Div(
                attrs = {
                    style {
                        property("display", "flex")
                        property("gap", "8px")
                        property("margin-left", "auto")
                    }
                }
            ) {
                ActionButton(
                    label = "📥 Import JSON",
                    onClick = { 
                        FileImporter.importJson(
                            onSuccess = { layout ->
                                state.updateLayout(layout)
                                console.log("Layout imported: ${layout.name}")
                            },
                            onError = { error ->
                                console.error("Import failed: $error")
                            }
                        )
                    }
                )
                
                ActionButton(
                    label = "📄 Copy JSON",
                    onClick = { 
                        FileExporter.copyToClipboard(
                            state.layout,
                            onSuccess = { console.log("Layout copied to clipboard!") },
                            onError = { error -> console.error("Copy failed: $error") }
                        )
                    }
                )
                
                ActionButton(
                    label = "💾 Export JSON",
                    onClick = { 
                        FileExporter.downloadJson(state.layout)
                        state.markSaved()
                    }
                )
                
                ActionButton(
                    label = "💾 Download XML",
                    primary = true,
                    onClick = { 
                        XmlImportExport.exportXml(state.layout)
                        state.markSaved()
                    }
                )
            }
        }
    }
}

@Composable
fun StatusBadge(state: LayoutEditorState) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("align-items", "center")
                property("gap", "12px")
            }
        }
    ) {
        // Keys count
        Span(
            attrs = {
                style {
                    property("background", "rgba(255,255,255,0.25)")
                    property("color", "white")
                    property("padding", "6px 14px")
                    property("border-radius", "20px")
                    property("font-size", "13px")
                    property("font-weight", "600")
                }
            }
        ) {
            Text("${state.layout.keys.size} keys")
        }
        
        // Unsaved indicator
        if (state.isDirty) {
            Span(
                attrs = {
                    style {
                        property("background", "#ff6b6b")
                        property("color", "white")
                        property("padding", "6px 14px")
                        property("border-radius", "20px")
                        property("font-size", "13px")
                        property("font-weight", "600")
                        property("animation", "pulse 2s infinite")
                    }
                }
            ) {
                Text("● Unsaved")
            }
        }
    }
}

@Composable
fun ModeButton(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        attrs = {
            onClick { onClick() }
            style {
                property("padding", "10px 18px")
                property("border", "none")
                property("background", if (active) "rgba(255,255,255,0.95)" else "transparent")
                property("color", if (active) "#667eea" else "rgba(255,255,255,0.9)")
                property("border-radius", "6px")
                property("cursor", "pointer")
                property("font-size", "14px")
                property("font-weight", if (active) "600" else "500")
                property("transition", "all 0.2s ease")
                property("box-shadow", if (active) "0 2px 8px rgba(0,0,0,0.15)" else "none")
                if (!active) {
                    property("&:hover", "background: rgba(255,255,255,0.1)")
                }
            }
        }
    ) {
        Text(label)
    }
}

@Composable
fun ActionButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        attrs = {
            onClick { onClick() }
            style {
                property("padding", "10px 20px")
                property("border", "none")
                property("background", if (primary) "#10b981" else "rgba(255,255,255,0.95)")
                property("color", if (primary) "white" else "#374151")
                property("border-radius", "8px")
                property("cursor", "pointer")
                property("font-size", "14px")
                property("font-weight", "600")
                property("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                property("transition", "all 0.2s ease")
                property("&:hover", if (primary) "background: #059669" else "transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0,0,0,0.15)")
                property("&:active", "transform: translateY(0)")
            }
        }
    ) {
        Text(label)
    }
}

@Composable
fun PresetSelector(state: LayoutEditorState) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("align-items", "center")
                property("gap", "10px")
                property("background", "rgba(255,255,255,0.15)")
                property("padding", "6px 14px")
                property("border-radius", "8px")
            }
        }
    ) {
        Span(
            attrs = {
                style {
                    property("font-size", "13px")
                    property("color", "rgba(255,255,255,0.9)")
                    property("font-weight", "600")
                    property("text-transform", "uppercase")
                    property("letter-spacing", "0.5px")
                }
            }
        ) {
            Text("📦 Load:")
        }
        
        org.jetbrains.compose.web.dom.Select(
            attrs = {
                onChange { event ->
                    val presetName = event.value
                    val preset = when (presetName) {
                        "qwerty" -> org.dark.keyboard.shared.presets.PresetLayouts.qwertyPreset
                        "minimal" -> org.dark.keyboard.shared.presets.PresetLayouts.minimalExample
                        else -> return@onChange
                    }
                    state.updateLayout(preset)
                }
                style {
                    property("padding", "8px 14px")
                    property("border", "2px solid rgba(255,255,255,0.3)")
                    property("border-radius", "6px")
                    property("cursor", "pointer")
                    property("font-size", "14px")
                    property("font-weight", "500")
                    property("background", "rgba(255,255,255,0.95)")
                    property("color", "#374151")
                    property("transition", "all 0.2s ease")
                    property("&:hover", "border-color: rgba(255,255,255,0.5)")
                    property("&:focus", "outline: none; border-color: white; box-shadow: 0 0 0 3px rgba(255,255,255,0.2)")
                }
            }
        ) {
            org.jetbrains.compose.web.dom.Option("") {
                Text("Choose preset...")
            }
            org.jetbrains.compose.web.dom.Option("qwerty") {
                Text("QWERTY Standard")
            }
            org.jetbrains.compose.web.dom.Option("minimal") {
                Text("Minimal Example")
            }
        }
    }
}
