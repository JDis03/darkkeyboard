package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*

/**
 * Canvas area where keys are rendered and edited
 */
@Composable
fun CanvasArea(state: LayoutEditorState) {
    Div(
        attrs = {
            style {
                property("flex", "1")
                property("min-width", "0")
                property("overflow", "auto")
                property("background", "#f5f5f5")
                property("display", "flex")
                property("justify-content", "center")
                property("align-items", "center")
                property("padding", "40px")
            }
        }
    ) {
        // Keyboard canvas
        KeyboardCanvas(state)
    }
}

@Composable
fun KeyboardCanvas(state: LayoutEditorState) {
    val layout = state.layout
    val scale = 0.8 // Scale down for better viewing
    val gridSize = 10 // Grid size in px
    val dragState = rememberDragState()
    val resizeState = rememberResizeState()
    
    // Track mouse position for ADD mode preview
    var previewX by remember { mutableStateOf<Int?>(null) }
    var previewY by remember { mutableStateOf<Int?>(null) }
    
    Div(
        attrs = {
            // Handle click on empty canvas to add key (ADD mode)
            onClick { event ->
                if (state.mode == EditorMode.ADD) {
                    val rect = event.currentTarget.asDynamic().getBoundingClientRect()
                    val mouseX = ((event.clientX as Double) - (rect.left as Double)) / scale
                    val mouseY = ((event.clientY as Double) - (rect.top as Double)) / scale
                    
                    // Snap to grid
                    val snappedX = ((mouseX.toInt() / gridSize) * gridSize).coerceIn(0, layout.width - 80)
                    val snappedY = ((mouseY.toInt() / gridSize) * gridSize).coerceIn(0, layout.height - 80)
                    
                    // Create new key with sensible defaults
                    val newKey = org.dark.keyboard.shared.model.KeyModel(
                        label = "?",
                        code = 0,
                        x = snappedX,
                        y = snappedY,
                        width = 80,
                        height = 80,
                        shiftLabel = null,
                        popupKeys = emptyList(),
                        isModifier = false,
                        isSticky = false,
                        repeatable = true,
                        edgeFlags = 0
                    )
                    
                    state.addKey(newKey)
                    state.selectKey(newKey)
                }
            }
            
            // Handle mouse move for dragging OR resizing OR preview
            onMouseMove { event ->
                val rect = event.currentTarget.asDynamic().getBoundingClientRect()
                val mouseX = (event.clientX as Double) - (rect.left as Double)
                val mouseY = (event.clientY as Double) - (rect.top as Double)
                
                when {
                    dragState.isDragging && state.mode == EditorMode.SELECT -> {
                        dragState.updateDrag(mouseX, mouseY, scale)
                    }
                    resizeState.isResizing && state.mode == EditorMode.RESIZE -> {
                        resizeState.updateResize(mouseX, mouseY, scale)?.let { dims ->
                            // Update will happen on mouse up
                        }
                    }
                    state.mode == EditorMode.ADD -> {
                        // Update preview position
                        val scaledX = (mouseX / scale).toInt()
                        val scaledY = (mouseY / scale).toInt()
                        previewX = ((scaledX / gridSize) * gridSize).coerceIn(0, layout.width - 80)
                        previewY = ((scaledY / gridSize) * gridSize).coerceIn(0, layout.height - 80)
                    }
                }
            }
            
            // Clear preview when mouse leaves
            onMouseLeave { event ->
                previewX = null
                previewY = null
                if (dragState.isDragging) {
                    dragState.endDrag()
                }
            }
            
            // Handle touch move for dragging OR resizing (mobile)
            onTouchMove { event ->
                val touch = event.touches.item(0).asDynamic()
                val rect = event.currentTarget.asDynamic().getBoundingClientRect()
                val touchX = (touch.clientX as Double) - (rect.left as Double)
                val touchY = (touch.clientY as Double) - (rect.top as Double)
                
                when {
                    dragState.isDragging && state.mode == EditorMode.SELECT -> {
                        dragState.updateDrag(touchX, touchY, scale)
                        event.preventDefault()
                    }
                    resizeState.isResizing && state.mode == EditorMode.RESIZE -> {
                        resizeState.updateResize(touchX, touchY, scale)?.let { dims ->
                            // Update will happen on touch end
                        }
                        event.preventDefault()
                    }
                }
            }
            
            // Handle mouse up to end drag or resize
            onMouseUp { event ->
                when {
                    dragState.isDragging -> {
                        dragState.draggingKey?.let { key ->
                            val (newX, newY) = dragState.getDraggedPosition(gridSize)
                            
                            // Enforce boundaries
                            val boundedX = newX.coerceIn(0, layout.width - key.width)
                            val boundedY = newY.coerceIn(0, layout.height - key.height)
                            
                            // Update key position
                            val newKey = key.copy(x = boundedX, y = boundedY)
                            state.updateKey(key, newKey)
                        }
                        dragState.endDrag()
                    }
                    resizeState.isResizing -> {
                        resizeState.resizingKey?.let { key ->
                            val rect = event.currentTarget.asDynamic().getBoundingClientRect()
                            val mouseX = (event.clientX as Double) - (rect.left as Double)
                            val mouseY = (event.clientY as Double) - (rect.top as Double)
                            
                            resizeState.updateResize(mouseX, mouseY, scale)?.let { dims ->
                                // Enforce boundaries (make sure key stays within canvas)
                                val maxX = (layout.width - dims.width).coerceAtLeast(0)
                                val maxY = (layout.height - dims.height).coerceAtLeast(0)
                                val boundedX = dims.x.coerceIn(0, maxX)
                                val boundedY = dims.y.coerceIn(0, maxY)
                                
                                // Update key dimensions
                                val newKey = key.copy(
                                    x = boundedX,
                                    y = boundedY,
                                    width = dims.width,
                                    height = dims.height
                                )
                                state.updateKey(key, newKey)
                            }
                        }
                        resizeState.endResize()
                    }
                }
            }
            
            // Handle touch end (mobile)
            onTouchEnd { event ->
                when {
                    dragState.isDragging -> {
                        dragState.draggingKey?.let { key ->
                            val (newX, newY) = dragState.getDraggedPosition(gridSize)
                            
                            // Enforce boundaries
                            val boundedX = newX.coerceIn(0, layout.width - key.width)
                            val boundedY = newY.coerceIn(0, layout.height - key.height)
                            
                            // Update key position
                            val newKey = key.copy(x = boundedX, y = boundedY)
                            state.updateKey(key, newKey)
                        }
                        dragState.endDrag()
                    }
                    resizeState.isResizing -> {
                        resizeState.resizingKey?.let { key ->
                            // Use changedTouches instead of touches (touches is empty on touchend)
                            val touch = event.changedTouches.item(0)?.asDynamic()
                            if (touch != null) {
                                val rect = event.currentTarget.asDynamic().getBoundingClientRect()
                                val touchX = (touch.clientX as Double) - (rect.left as Double)
                                val touchY = (touch.clientY as Double) - (rect.top as Double)
                                
                                resizeState.updateResize(touchX, touchY, scale)?.let { dims ->
                                    // Enforce boundaries (make sure key stays within canvas)
                                    val maxX = (layout.width - dims.width).coerceAtLeast(0)
                                    val maxY = (layout.height - dims.height).coerceAtLeast(0)
                                    val boundedX = dims.x.coerceIn(0, maxX)
                                    val boundedY = dims.y.coerceIn(0, maxY)
                                    
                                    // Update key dimensions
                                    val newKey = key.copy(
                                        x = boundedX,
                                        y = boundedY,
                                        width = dims.width,
                                        height = dims.height
                                    )
                                    state.updateKey(key, newKey)
                                }
                            }
                        }
                        resizeState.endResize()
                    }
                }
            }
            
            style {
                property("position", "relative")
                property("width", "${layout.width * scale}px")
                property("height", "${layout.height * scale}px")
                property("background", "white")
                
                // Grid pattern background
                property("background-image", 
                    "repeating-linear-gradient(0deg, transparent, transparent ${gridSize * scale - 1}px, #e0e0e0 ${gridSize * scale - 1}px, #e0e0e0 ${gridSize * scale}px), " +
                    "repeating-linear-gradient(90deg, transparent, transparent ${gridSize * scale - 1}px, #e0e0e0 ${gridSize * scale - 1}px, #e0e0e0 ${gridSize * scale}px)")
                
                property("border", "2px solid #ccc")
                property("border-radius", "8px")
                property("box-shadow", "0 4px 12px rgba(0,0,0,0.1)")
            }
        }
    ) {
        // Render each key
        layout.keys.forEach { key ->
            val isDragging = dragState.draggingKey == key
            val isResizing = resizeState.resizingKey == key
            
            // Calculate display position and size
            val (displayX, displayY) = if (isDragging) {
                val (snappedX, snappedY) = dragState.getDraggedPosition(gridSize)
                snappedX to snappedY
            } else {
                key.x to key.y
            }
            
            KeyView(
                key = key,
                displayX = displayX,
                displayY = displayY,
                scale = scale,
                isSelected = state.selectedKey == key,
                isDragging = isDragging,
                isResizing = isResizing,
                editorState = state,
                dragState = dragState,
                resizeState = resizeState,
                onClick = { 
                    when (state.mode) {
                        EditorMode.SELECT, EditorMode.RESIZE -> {
                            state.selectKey(key)
                        }
                        EditorMode.DELETE -> {
                            state.removeKey(key)
                        }
                        EditorMode.ADD -> {
                            // Do nothing - adding keys happens on empty canvas click
                        }
                    }
                }
            )
        }
        
        // Preview for ADD mode
        if (state.mode == EditorMode.ADD && previewX != null && previewY != null) {
            Div(
                attrs = {
                    style {
                        property("position", "absolute")
                        property("left", "${previewX!! * scale}px")
                        property("top", "${previewY!! * scale}px")
                        property("width", "${80 * scale}px")
                        property("height", "${80 * scale}px")
                        property("border", "2px dashed #4CAF50")
                        property("background", "rgba(76, 175, 80, 0.1)")
                        property("border-radius", "6px")
                        property("pointer-events", "none")
                        property("display", "flex")
                        property("align-items", "center")
                        property("justify-content", "center")
                        property("color", "#4CAF50")
                        property("font-size", "24px")
                        property("font-weight", "600")
                    }
                }
            ) {
                Text("?")
            }
        }
        
        // Canvas info overlay
        Div(
            attrs = {
                style {
                    property("position", "absolute")
                    property("top", "8px")
                    property("right", "8px")
                    property("padding", "4px 8px")
                    property("background", "rgba(0,0,0,0.6)")
                    property("color", "white")
                    property("border-radius", "4px")
                    property("font-size", "12px")
                    property("font-family", "monospace")
                }
            }
        ) {
            Text("${layout.width}×${layout.height}px")
        }
    }
}

@Composable
fun KeyView(
    key: org.dark.keyboard.shared.model.KeyModel,
    displayX: Int,
    displayY: Int,
    scale: Double,
    isSelected: Boolean,
    isDragging: Boolean,
    isResizing: Boolean,
    editorState: LayoutEditorState,
    dragState: DragState,
    resizeState: ResizeState,
    onClick: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    
    Div(
        attrs = {
            onMouseEnter { isHovered = true }
            onMouseLeave { isHovered = false }
            
            // Handle mouse down on key
            onMouseDown { event ->
                // Always trigger onClick first (handles SELECT/RESIZE/DELETE/ADD logic)
                onClick()
                
                // Then start drag if in SELECT mode
                if (editorState.mode == EditorMode.SELECT && !isDragging && !isResizing) {
                    val canvas = event.currentTarget.asDynamic().parentElement
                    val canvasRect = canvas.getBoundingClientRect()
                    val mouseX = (event.clientX as Double) - (canvasRect.left as Double)
                    val mouseY = (event.clientY as Double) - (canvasRect.top as Double)
                    
                    dragState.startDrag(key, mouseX, mouseY)
                    event.preventDefault()
                    event.stopPropagation()
                }
            }
            
            // Handle touch start on key (mobile)
            onTouchStart { event ->
                // Always trigger onClick first (handles SELECT/RESIZE/DELETE/ADD logic)
                onClick()
                
                // Then start drag if in SELECT mode
                if (editorState.mode == EditorMode.SELECT && !isDragging && !isResizing) {
                    val touch = event.touches.item(0).asDynamic()
                    val canvas = event.currentTarget.asDynamic().parentElement
                    val canvasRect = canvas.getBoundingClientRect()
                    val touchX = (touch.clientX as Double) - (canvasRect.left as Double)
                    val touchY = (touch.clientY as Double) - (canvasRect.top as Double)
                    
                    dragState.startDrag(key, touchX, touchY)
                    event.preventDefault()
                    event.stopPropagation()
                }
            }
            
            style {
                property("position", "absolute")
                property("left", "${displayX * scale}px")
                property("top", "${displayY * scale}px")
                property("width", "${key.width * scale}px")
                property("height", "${key.height * scale}px")
                
                // Border and background
                property("border", "2px solid ${when {
                    isDragging -> "#4CAF50"
                    isSelected -> "#2196F3"
                    isHovered -> "#757575"
                    else -> "#333"
                }}")
                property("background", when {
                    isDragging -> "#E8F5E9"
                    isSelected -> "#E3F2FD"
                    isHovered && key.isModifier -> "#FFF59D"
                    isHovered -> "#f5f5f5"
                    key.isModifier -> "#FFF9C4"
                    else -> "#ffffff"
                })
                
                property("border-radius", "6px")
                property("cursor", if (editorState.mode == EditorMode.SELECT) {
                    if (isDragging) "grabbing" else "grab"
                } else "pointer")
                property("display", "flex")
                property("flex-direction", "column")
                property("align-items", "center")
                property("justify-content", "center")
                property("font-size", "14px")
                property("font-weight", if (key.isModifier) "600" else "normal")
                property("user-select", "none")
                
                // Enhanced shadow for selected keys
                property("box-shadow", when {
                    isDragging -> "0 0 0 3px #4CAF50, 0 8px 16px rgba(76, 175, 80, 0.4)"
                    isSelected -> "0 0 0 3px #2196F3, 0 4px 8px rgba(33, 150, 243, 0.3)"
                    isHovered -> "0 2px 4px rgba(0,0,0,0.2)"
                    else -> "0 1px 2px rgba(0,0,0,0.1)"
                })
                
                // Smooth transitions (but not for position when dragging)
                property("transition", if (isDragging) "box-shadow 0.2s ease" else "all 0.2s ease")
                
                // Higher z-index when dragging
                property("z-index", if (isDragging) "1000" else "1")
            }
        }
    ) {
        // Shift label (top-left corner)
        key.shiftLabel?.let { shiftLabel ->
            Div(
                attrs = {
                    style {
                        property("position", "absolute")
                        property("top", "2px")
                        property("left", "4px")
                        property("font-size", "10px")
                        property("color", "#666")
                    }
                }
            ) {
                Text(shiftLabel)
            }
        }
        
        // Main label
        Span(
            attrs = {
                style {
                    property("font-size", "16px")
                }
            }
        ) {
            Text(key.label)
        }
        
        // Sticky indicator
        if (key.isSticky) {
            Div(
                attrs = {
                    style {
                        property("position", "absolute")
                        property("bottom", "2px")
                        property("right", "4px")
                        property("font-size", "10px")
                        property("color", "#2196F3")
                    }
                }
            ) {
                Text("⬤")
            }
        }
        
        // Popup keys indicator
        if (key.popupKeys.isNotEmpty()) {
            Div(
                attrs = {
                    style {
                        property("position", "absolute")
                        property("top", "2px")
                        property("right", "4px")
                        property("font-size", "10px")
                        property("color", "#666")
                    }
                }
            ) {
                Text("⋯")
            }
        }
        
        // Resize handles (only in RESIZE mode and key is selected)
        if (isSelected && editorState.mode == EditorMode.RESIZE) {
            ResizeHandles(
                scale = scale,
                resizeState = resizeState,
                onHandleDown = { handle, clientX, clientY ->
                    // Need to convert client coordinates to canvas coordinates
                    // This will be handled by the callback
                    resizeState.startResize(key, handle, clientX, clientY)
                }
            )
        }
    }
}
