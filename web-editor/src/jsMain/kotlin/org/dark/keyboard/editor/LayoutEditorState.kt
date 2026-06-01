package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel

/**
 * State holder for the layout editor.
 * 
 * Manages the current layout being edited, selected keys, and editor mode.
 */
class LayoutEditorState(initialLayout: LayoutModel) {
    /**
     * Current layout being edited
     */
    var layout by mutableStateOf(initialLayout)
        private set
    
    /**
     * Currently selected key (null if none selected)
     */
    var selectedKey by mutableStateOf<KeyModel?>(null)
        private set
    
    /**
     * Current editor mode
     */
    var mode by mutableStateOf(EditorMode.SELECT)
        private set
    
    /**
     * Whether the layout has unsaved changes
     */
    var isDirty by mutableStateOf(false)
        private set
    
    /**
     * Update the entire layout
     */
    fun updateLayout(newLayout: LayoutModel) {
        layout = newLayout
        isDirty = true
    }
    
    /**
     * Add a new key to the layout
     */
    fun addKey(key: KeyModel) {
        layout = layout.copy(keys = layout.keys + key)
        isDirty = true
    }
    
    /**
     * Update an existing key
     */
    fun updateKey(oldKey: KeyModel, newKey: KeyModel) {
        val index = layout.keys.indexOf(oldKey)
        if (index != -1) {
            val newKeys = layout.keys.toMutableList()
            newKeys[index] = newKey
            layout = layout.copy(keys = newKeys)
            isDirty = true
            
            // Update selected key if it's the one being updated
            if (selectedKey == oldKey) {
                selectedKey = newKey
            }
        }
    }
    
    /**
     * Remove a key from the layout
     */
    fun removeKey(key: KeyModel) {
        layout = layout.copy(keys = layout.keys.filter { it != key })
        if (selectedKey == key) {
            selectedKey = null
        }
        isDirty = true
    }
    
    /**
     * Select a key
     */
    fun selectKey(key: KeyModel?) {
        selectedKey = key
    }
    
    /**
     * Change editor mode
     */
    fun setMode(newMode: EditorMode) {
        mode = newMode
        // Deselect when switching modes
        if (newMode != EditorMode.SELECT) {
            selectedKey = null
        }
    }
    
    /**
     * Mark layout as saved
     */
    fun markSaved() {
        isDirty = false
    }
    
    /**
     * Update layout metadata (name, dimensions)
     */
    fun updateMetadata(
        name: String? = null,
        width: Int? = null,
        height: Int? = null
    ) {
        layout = layout.copy(
            name = name ?: layout.name,
            width = width ?: layout.width,
            height = height ?: layout.height
        )
        isDirty = true
    }
}

/**
 * Editor modes
 */
enum class EditorMode {
    /** Select and move keys */
    SELECT,
    
    /** Add new keys */
    ADD,
    
    /** Delete keys */
    DELETE,
    
    /** Resize keys */
    RESIZE
}

/**
 * Remember a LayoutEditorState
 */
@Composable
fun rememberLayoutEditorState(initialLayout: LayoutModel): LayoutEditorState {
    return remember(initialLayout) {
        LayoutEditorState(initialLayout)
    }
}
