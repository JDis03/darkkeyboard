package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.w3c.dom.events.KeyboardEvent

/**
 * Keyboard shortcuts handler
 */
@Composable
fun KeyboardShortcuts(state: LayoutEditorState) {
    DisposableEffect(Unit) {
        val handler: (KeyboardEvent) -> Unit = { event ->
            when {
                // Delete selected key
                event.key == "Delete" || event.key == "Backspace" -> {
                    state.selectedKey?.let { key ->
                        state.removeKey(key)
                        event.preventDefault()
                    }
                }
                
                // Escape to deselect
                event.key == "Escape" -> {
                    state.selectKey(null)
                    event.preventDefault()
                }
                
                // Ctrl+S to save (TODO: implement save)
                event.key == "s" && (event.ctrlKey || event.metaKey) -> {
                    // TODO: Export JSON
                    event.preventDefault()
                }
                
                // Arrow keys to nudge selected key
                event.key.startsWith("Arrow") -> {
                    state.selectedKey?.let { key ->
                        val dx = when (event.key) {
                            "ArrowLeft" -> -10
                            "ArrowRight" -> 10
                            else -> 0
                        }
                        val dy = when (event.key) {
                            "ArrowUp" -> -10
                            "ArrowDown" -> 10
                            else -> 0
                        }
                        
                        val newKey = key.copy(
                            x = (key.x + dx).coerceIn(0, state.layout.width - key.width),
                            y = (key.y + dy).coerceIn(0, state.layout.height - key.height)
                        )
                        state.updateKey(key, newKey)
                        event.preventDefault()
                    }
                }
            }
        }
        
        kotlinx.browser.window.addEventListener("keydown", handler.unsafeCast<(dynamic) -> Unit>())
        
        onDispose {
            kotlinx.browser.window.removeEventListener("keydown", handler.unsafeCast<(dynamic) -> Unit>())
        }
    }
}
