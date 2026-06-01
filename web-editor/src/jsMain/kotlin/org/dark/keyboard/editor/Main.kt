package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.jetbrains.compose.web.renderComposable

/**
 * Entry point for the keyboard layout editor web application.
 * 
 * This application runs entirely in the browser using Kotlin/JS and Compose for Web.
 */
fun main() {
    renderComposable(rootElementId = "root") {
        App()
    }
}

/**
 * Root composable for the editor application.
 */
@Composable
fun App() {
    // Initialize state with minimal example
    val editorState = rememberLayoutEditorState(
        org.dark.keyboard.shared.presets.PresetLayouts.minimalExample
    )
    
    // Keyboard shortcuts handler
    KeyboardShortcuts(editorState)
    
    // Main container
    org.jetbrains.compose.web.dom.Div(
        attrs = {
            style {
                property("display", "flex")
                property("flex-direction", "column")
                property("height", "100vh")
                property("overflow", "hidden")
            }
        }
    ) {
        // Header
        Header()
        
        // Toolbar
        Toolbar(editorState)
        
        // Main content: Canvas + Properties Panel
        org.jetbrains.compose.web.dom.Div(
            attrs = {
                style {
                    property("display", "flex")
                    property("flex", "1")
                    property("overflow", "hidden")
                    property("position", "relative")
                }
            }
        ) {
            // Canvas area
            CanvasArea(editorState)
            
            // Properties panel with toggle
            PropertiesPanelWithToggle(editorState)
        }
    }
}

@Composable
fun Header() {
    org.jetbrains.compose.web.dom.Div(
        attrs = {
            style {
                property("background", "#2196F3")
                property("color", "white")
                property("padding", "16px 24px")
                property("box-shadow", "0 2px 4px rgba(0,0,0,0.1)")
                property("flex-shrink", "0")
            }
        }
    ) {
        org.jetbrains.compose.web.dom.H1(
            attrs = {
                style {
                    property("margin", "0")
                    property("font-size", "24px")
                    property("font-weight", "500")
                }
            }
        ) {
            org.jetbrains.compose.web.dom.Text("DarkKeyboard Layout Editor v2")
        }
    }
}
