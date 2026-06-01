package org.dark.keyboard.editor

import androidx.compose.runtime.*
import org.jetbrains.compose.web.dom.*

/**
 * Resize handles for a selected key
 */
@Composable
fun ResizeHandles(
    scale: Double,
    resizeState: ResizeState,
    onHandleDown: (ResizeHandle, Double, Double) -> Unit
) {
    val handleSize = 12 // Handle size in pixels
    val handles = listOf(
        ResizeHandle.TOP_LEFT to Pair(-handleSize / 2.0, -handleSize / 2.0),
        ResizeHandle.TOP to Pair(50.0, -handleSize / 2.0), // 50% = center
        ResizeHandle.TOP_RIGHT to Pair(100.0, -handleSize / 2.0),
        ResizeHandle.RIGHT to Pair(100.0, 50.0),
        ResizeHandle.BOTTOM_RIGHT to Pair(100.0, 100.0),
        ResizeHandle.BOTTOM to Pair(50.0, 100.0),
        ResizeHandle.BOTTOM_LEFT to Pair(-handleSize / 2.0, 100.0),
        ResizeHandle.LEFT to Pair(-handleSize / 2.0, 50.0)
    )
    
    handles.forEach { (handle, position) ->
        ResizeHandle(
            handle = handle,
            positionPercent = position,
            size = handleSize,
            isActive = resizeState.activeHandle == handle,
            onHandleDown = onHandleDown
        )
    }
}

@Composable
fun ResizeHandle(
    handle: ResizeHandle,
    positionPercent: Pair<Double, Double>,
    size: Int,
    isActive: Boolean,
    onHandleDown: (ResizeHandle, Double, Double) -> Unit
) {
    Div(
        attrs = {
            onMouseDown { event ->
                // Get canvas coordinates (parent's parent = KeyView -> Canvas)
                val keyView = event.currentTarget.asDynamic().parentElement
                val canvas = keyView.parentElement
                val canvasRect = canvas.getBoundingClientRect()
                val mouseX = (event.clientX as Double) - (canvasRect.left as Double)
                val mouseY = (event.clientY as Double) - (canvasRect.top as Double)
                
                onHandleDown(handle, mouseX, mouseY)
                event.preventDefault()
                event.stopPropagation()
            }
            
            onTouchStart { event ->
                val touch = event.touches.item(0).asDynamic()
                // Get canvas coordinates (parent's parent = KeyView -> Canvas)
                val keyView = event.currentTarget.asDynamic().parentElement
                val canvas = keyView.parentElement
                val canvasRect = canvas.getBoundingClientRect()
                val touchX = (touch.clientX as Double) - (canvasRect.left as Double)
                val touchY = (touch.clientY as Double) - (canvasRect.top as Double)
                
                onHandleDown(handle, touchX, touchY)
                event.preventDefault()
                event.stopPropagation()
            }
            
            style {
                property("position", "absolute")
                
                // Position based on percentage
                if (positionPercent.first < 0) {
                    property("left", "${positionPercent.first}px")
                } else if (positionPercent.first == 100.0) {
                    property("right", "-${size / 2}px")
                } else {
                    property("left", "${positionPercent.first}%")
                    property("transform", "translateX(-50%)")
                }
                
                if (positionPercent.second < 0) {
                    property("top", "${positionPercent.second}px")
                } else if (positionPercent.second == 100.0) {
                    property("bottom", "-${size / 2}px")
                } else {
                    property("top", "${positionPercent.second}%")
                    if (positionPercent.first == 50.0) {
                        property("transform", "translate(-50%, -50%)")
                    } else {
                        property("transform", "translateY(-50%)")
                    }
                }
                
                property("width", "${size}px")
                property("height", "${size}px")
                property("background", if (isActive) "#4CAF50" else "#2196F3")
                property("border", "2px solid white")
                property("border-radius", "50%")
                property("cursor", getCursorForHandle(handle))
                property("z-index", "1002")
                property("box-shadow", "0 2px 4px rgba(0,0,0,0.3)")
            }
        }
    )
}

/**
 * Get appropriate cursor for each handle
 */
fun getCursorForHandle(handle: ResizeHandle): String {
    return when (handle) {
        ResizeHandle.TOP_LEFT -> "nwse-resize"
        ResizeHandle.TOP -> "ns-resize"
        ResizeHandle.TOP_RIGHT -> "nesw-resize"
        ResizeHandle.RIGHT -> "ew-resize"
        ResizeHandle.BOTTOM_RIGHT -> "nwse-resize"
        ResizeHandle.BOTTOM -> "ns-resize"
        ResizeHandle.BOTTOM_LEFT -> "nesw-resize"
        ResizeHandle.LEFT -> "ew-resize"
    }
}
