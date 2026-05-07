package org.dark.keyboard
open class SwipeTracker {
    fun reset() {}
    fun computeCurrentVelocity(units: Int) {}
    fun getXVelocity() = 0f
    fun getYVelocity() = 0f
    fun addMovement(event: android.view.MotionEvent?) {}
    fun getGestureDetector() = null
}
