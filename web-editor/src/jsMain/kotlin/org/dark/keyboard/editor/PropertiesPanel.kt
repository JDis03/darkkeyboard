package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.*
import org.dark.keyboard.shared.model.KeyModel

/**
 * Properties panel with toggle button
 */
@Composable
fun PropertiesPanelWithToggle(state: LayoutEditorState) {
    var isVisible by remember { mutableStateOf(true) }
    
    // Container for toggle button + panel
    Div(
        attrs = {
            style {
                property("position", "relative")
                property("display", "flex")
                property("flex-shrink", "0")
            }
        }
    ) {
        // Toggle button
        Button(
            attrs = {
                onClick { isVisible = !isVisible }
                style {
                    property("position", "absolute")
                    property("left", "0px")
                    property("top", "50%")
                    property("transform", "translateY(-50%)")
                    property("width", "40px")
                    property("height", "80px")
                    property("background", "#2196F3")
                    property("color", "white")
                    property("border", "none")
                    property("border-radius", "8px 0 0 8px")
                    property("cursor", "pointer")
                    property("display", "flex")
                    property("align-items", "center")
                    property("justify-content", "center")
                    property("font-size", "20px")
                    property("box-shadow", "-2px 2px 4px rgba(0,0,0,0.2)")
                    property("z-index", "1001")
                }
            }
        ) {
            Text(if (isVisible) "›" else "‹")
        }
        
        // Properties panel
        Div(
            attrs = {
                style {
                    property("width", if (isVisible) "300px" else "0px")
                    property("min-width", if (isVisible) "300px" else "0px")
                    property("max-width", if (isVisible) "300px" else "0px")
                    property("margin-left", "40px")
                    property("background", "white")
                    property("border-left", if (isVisible) "1px solid #e0e0e0" else "none")
                    property("padding", if (isVisible) "16px" else "0")
                    property("overflow", if (isVisible) "auto" else "hidden")
                    property("transition", "all 0.3s ease")
                }
            }
        ) {
            if (isVisible) {
                PropertiesPanel(state)
            }
        }
    }
}

/**
 * Properties panel content (without toggle)
 */
@Composable
fun PropertiesPanel(state: LayoutEditorState) {
    val selectedKey = state.selectedKey
    
    H3(
        attrs = {
            style {
                property("margin", "0 0 16px 0")
                property("font-size", "18px")
                property("font-weight", "500")
            }
        }
    ) {
        Text("Properties")
    }
    
    if (selectedKey != null) {
        KeyProperties(selectedKey, state)
    } else {
        NoSelection()
    }
}

@Composable
fun NoSelection() {
    Div(
        attrs = {
            style {
                property("color", "#666")
                property("font-size", "14px")
                property("text-align", "center")
                property("padding", "40px 0")
            }
        }
    ) {
        P { Text("No key selected") }
        P(
            attrs = {
                style {
                    property("font-size", "12px")
                    property("margin-top", "8px")
                }
            }
        ) {
            Text("Click on a key to view and edit its properties")
        }
    }
}

@Composable
fun KeyProperties(key: KeyModel, state: LayoutEditorState) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("flex-direction", "column")
                property("gap", "16px")
            }
        }
    ) {
        PropertyGroup("Basic") {
            EditableTextField(
                label = "Label",
                value = key.label,
                onValueChange = { newValue ->
                    val newKey = key.copy(label = newValue)
                    state.updateKey(key, newKey)
                },
                placeholder = "Key label"
            )
            
            EditableNumberField(
                label = "Key Code",
                value = key.code,
                onValueChange = { newValue ->
                    val newKey = key.copy(code = newValue)
                    state.updateKey(key, newKey)
                }
            )
            
            PropertyRow("Position", "${key.x}, ${key.y}")
            PropertyRow("Size", "${key.width} × ${key.height}")
        }
        
        PropertyGroup("Shift & Popup") {
            EditableTextField(
                label = "Shift Label (optional)",
                value = key.shiftLabel ?: "",
                onValueChange = { newValue ->
                    val newKey = key.copy(shiftLabel = if (newValue.isEmpty()) null else newValue)
                    state.updateKey(key, newKey)
                },
                placeholder = "Leave empty for none"
            )
            
            EditableTextField(
                label = "Popup Keys (comma-separated)",
                value = key.popupKeys.joinToString(", "),
                onValueChange = { newValue ->
                    val newKeys = newValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val newKey = key.copy(popupKeys = newKeys)
                    state.updateKey(key, newKey)
                },
                placeholder = "e.g., à, á, â, ã"
            )
        }
        
        PropertyGroup("Modifiers") {
            EditableCheckbox(
                label = "Is Modifier Key",
                checked = key.isModifier,
                onCheckedChange = { checked ->
                    val newKey = key.copy(isModifier = checked)
                    state.updateKey(key, newKey)
                }
            )
            
            EditableCheckbox(
                label = "Is Sticky",
                checked = key.isSticky,
                onCheckedChange = { checked ->
                    val newKey = key.copy(isSticky = checked)
                    state.updateKey(key, newKey)
                }
            )
            
            EditableCheckbox(
                label = "Repeatable",
                checked = key.repeatable,
                onCheckedChange = { checked ->
                    val newKey = key.copy(repeatable = checked)
                    state.updateKey(key, newKey)
                }
            )
        }
        
        if (key.edgeFlags > 0) {
            PropertyGroup("Edge Flags") {
                val edges = mutableListOf<String>()
                if (key.edgeFlags and 1 != 0) edges.add("Left")
                if (key.edgeFlags and 2 != 0) edges.add("Right")
                if (key.edgeFlags and 4 != 0) edges.add("Top")
                if (key.edgeFlags and 8 != 0) edges.add("Bottom")
                PropertyRow("Edges", edges.joinToString(", "))
            }
        }
        
        // Action buttons
        Div(
            attrs = {
                style {
                    property("margin-top", "16px")
                    property("padding-top", "16px")
                    property("border-top", "1px solid #e0e0e0")
                    property("display", "flex")
                    property("gap", "8px")
                }
            }
        ) {
            Button(
                attrs = {
                    onClick { 
                        state.removeKey(key)
                    }
                    style {
                        property("flex", "1")
                        property("padding", "8px")
                        property("background", "#f44336")
                        property("color", "white")
                        property("border", "none")
                        property("border-radius", "4px")
                        property("cursor", "pointer")
                        property("font-size", "14px")
                    }
                }
            ) {
                Text("Delete Key")
            }
            
            Button(
                attrs = {
                    onClick { 
                        state.selectKey(null)
                    }
                    style {
                        property("flex", "1")
                        property("padding", "8px")
                        property("background", "#757575")
                        property("color", "white")
                        property("border", "none")
                        property("border-radius", "4px")
                        property("cursor", "pointer")
                        property("font-size", "14px")
                    }
                }
            ) {
                Text("Deselect")
            }
        }
    }
}

@Composable
fun PropertyGroup(title: String, content: @Composable () -> Unit) {
    Div(
        attrs = {
            style {
                property("background", "#fafafa")
                property("padding", "12px")
                property("border-radius", "4px")
            }
        }
    ) {
        H4(
            attrs = {
                style {
                    property("margin", "0 0 8px 0")
                    property("font-size", "14px")
                    property("font-weight", "600")
                    property("color", "#2196F3")
                }
            }
        ) {
            Text(title)
        }
        content()
    }
}

@Composable
fun PropertyRow(label: String, value: String) {
    Div(
        attrs = {
            style {
                property("display", "flex")
                property("justify-content", "space-between")
                property("align-items", "center")
                property("padding", "4px 0")
                property("font-size", "13px")
            }
        }
    ) {
        Span(
            attrs = {
                style {
                    property("color", "#666")
                    property("font-weight", "500")
                }
            }
        ) {
            Text("$label:")
        }
        Span(
            attrs = {
                style {
                    property("color", "#333")
                    property("font-family", "monospace")
                }
            }
        ) {
            Text(value)
        }
    }
}
