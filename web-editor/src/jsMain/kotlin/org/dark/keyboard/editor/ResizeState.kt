package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.dark.keyboard.shared.model.KeyModel

/**
 * Resize handle positions
 */
enum class ResizeHandle {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    RIGHT,
    BOTTOM_RIGHT,
    BOTTOM,
    BOTTOM_LEFT,
    LEFT
}

/**
 * State for resize operations
 */
class ResizeState {
    /**
     * Currently resizing key (null if not resizing)
     */
    var resizingKey by mutableStateOf<KeyModel?>(null)
        private set
    
    /**
     * Active resize handle
     */
    var activeHandle by mutableStateOf<ResizeHandle?>(null)
        private set
    
    /**
     * Initial mouse position when resize started
     */
    var resizeStartX by mutableStateOf(0.0)
        private set
    
    var resizeStartY by mutableStateOf(0.0)
        private set
    
    /**
     * Initial key dimensions when resize started
     */
    var keyStartX by mutableStateOf(0)
        private set
    
    var keyStartY by mutableStateOf(0)
        private set
    
    var keyStartWidth by mutableStateOf(0)
        private set
    
    var keyStartHeight by mutableStateOf(0)
        private set
    
    /**
     * Whether currently resizing
     */
    val isResizing: Boolean
        get() = resizingKey != null
    
    /**
     * Start resizing a key
     */
    fun startResize(key: KeyModel, handle: ResizeHandle, mouseX: Double, mouseY: Double) {
        resizingKey = key
        activeHandle = handle
        resizeStartX = mouseX
        resizeStartY = mouseY
        keyStartX = key.x
        keyStartY = key.y
        keyStartWidth = key.width
        keyStartHeight = key.height
    }
    
    /**
     * Update resize based on mouse movement
     */
    fun updateResize(mouseX: Double, mouseY: Double, scale: Double, minSize: Int = 60): ResizedDimensions? {
        if (!isResizing || activeHandle == null || resizingKey == null) return null
        
        val deltaX = ((mouseX - resizeStartX) / scale).toInt()
        val deltaY = ((mouseY - resizeStartY) / scale).toInt()
        
        console.log("Resize: mouseX=$mouseX, mouseY=$mouseY, startX=$resizeStartX, startY=$resizeStartY, deltaX=$deltaX, deltaY=$deltaY, scale=$scale")
        
        var newX = keyStartX
        var newY = keyStartY
        var newWidth = keyStartWidth
        var newHeight = keyStartHeight
        
        when (activeHandle) {
            ResizeHandle.TOP_LEFT -> {
                newX = keyStartX + deltaX
                newY = keyStartY + deltaY
                newWidth = keyStartWidth - deltaX
                newHeight = keyStartHeight - deltaY
            }
            ResizeHandle.TOP -> {
                newY = keyStartY + deltaY
                newHeight = keyStartHeight - deltaY
            }
            ResizeHandle.TOP_RIGHT -> {
                newY = keyStartY + deltaY
                newWidth = keyStartWidth + deltaX
                newHeight = keyStartHeight - deltaY
            }
            ResizeHandle.RIGHT -> {
                newWidth = keyStartWidth + deltaX
            }
            ResizeHandle.BOTTOM_RIGHT -> {
                newWidth = keyStartWidth + deltaX
                newHeight = keyStartHeight + deltaY
            }
            ResizeHandle.BOTTOM -> {
                newHeight = keyStartHeight + deltaY
            }
            ResizeHandle.BOTTOM_LEFT -> {
                newX = keyStartX + deltaX
                newWidth = keyStartWidth - deltaX
                newHeight = keyStartHeight + deltaY
            }
            ResizeHandle.LEFT -> {
                newX = keyStartX + deltaX
                newWidth = keyStartWidth - deltaX
            }
            else -> {}
        }
        
        // Enforce minimum size
        if (newWidth < minSize) {
            if (activeHandle in listOf(ResizeHandle.TOP_LEFT, ResizeHandle.LEFT, ResizeHandle.BOTTOM_LEFT)) {
                newX = keyStartX + keyStartWidth - minSize
            }
            newWidth = minSize
        }
        
        if (newHeight < minSize) {
            if (activeHandle in listOf(ResizeHandle.TOP_LEFT, ResizeHandle.TOP, ResizeHandle.TOP_RIGHT)) {
                newY = keyStartY + keyStartHeight - minSize
            }
            newHeight = minSize
        }
        
        return ResizedDimensions(newX, newY, newWidth, newHeight)
    }
    
    /**
     * End resize operation
     */
    fun endResize() {
        resizingKey = null
        activeHandle = null
    }
}

/**
 * Result of resize calculation
 */
data class ResizedDimensions(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

/**
 * Remember a ResizeState
 */
@Composable
fun rememberResizeState(): ResizeState {
    return remember { ResizeState() }
}
