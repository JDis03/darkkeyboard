package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.dark.keyboard.shared.model.KeyModel

/**
 * State for drag and drop operations
 */
class DragState {
    /**
     * Currently dragging key (null if not dragging)
     */
    var draggingKey by mutableStateOf<KeyModel?>(null)
        private set
    
    /**
     * Initial mouse position when drag started
     */
    var dragStartX by mutableStateOf(0.0)
        private set
    
    var dragStartY by mutableStateOf(0.0)
        private set
    
    /**
     * Initial key position when drag started
     */
    var keyStartX by mutableStateOf(0)
        private set
    
    var keyStartY by mutableStateOf(0)
        private set
    
    /**
     * Current offset during drag
     */
    var offsetX by mutableStateOf(0.0)
        private set
    
    var offsetY by mutableStateOf(0.0)
        private set
    
    /**
     * Whether currently dragging
     */
    val isDragging: Boolean
        get() = draggingKey != null
    
    /**
     * Start dragging a key
     */
    fun startDrag(key: KeyModel, mouseX: Double, mouseY: Double) {
        draggingKey = key
        dragStartX = mouseX
        dragStartY = mouseY
        keyStartX = key.x
        keyStartY = key.y
        offsetX = 0.0
        offsetY = 0.0
    }
    
    /**
     * Update drag position
     */
    fun updateDrag(mouseX: Double, mouseY: Double, scale: Double) {
        if (!isDragging) return
        
        // Calculate offset in scaled coordinates
        offsetX = (mouseX - dragStartX) / scale
        offsetY = (mouseY - dragStartY) / scale
    }
    
    /**
     * Get current dragged position (with grid snapping)
     */
    fun getDraggedPosition(gridSize: Int = 10): Pair<Int, Int> {
        val x = keyStartX + offsetX.toInt()
        val y = keyStartY + offsetY.toInt()
        
        // Snap to grid
        val snappedX = (x / gridSize) * gridSize
        val snappedY = (y / gridSize) * gridSize
        
        return snappedX to snappedY
    }
    
    /**
     * End drag operation
     */
    fun endDrag() {
        draggingKey = null
        offsetX = 0.0
        offsetY = 0.0
    }
}

/**
 * Remember a DragState
 */
@Composable
fun rememberDragState(): DragState {
    return remember { DragState() }
}
