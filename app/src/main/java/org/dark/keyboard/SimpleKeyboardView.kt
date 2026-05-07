package org.dark.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * View simple que dibuja el teclado y detecta touch
 */
class SimpleKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    
    private var keyboard: SimpleKeyboard? = null
    private var onKeyListener: OnKeyListener? = null
    
    private val keyPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = 0xFF2C2C2C.toInt() // Gris oscuro
    }
    
    private val keyBorderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = 0xFF444444.toInt()
        strokeWidth = 2f
    }
    
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = 0xFFFFFFFF.toInt() // Blanco
        textAlign = Paint.Align.CENTER
        textSize = 48f
    }
    
    fun setKeyboard(kb: SimpleKeyboard) {
        keyboard = kb
        requestLayout()
        invalidate()
    }
    
    fun setOnKeyListener(listener: OnKeyListener) {
        onKeyListener = listener
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = keyboard?.width ?: MeasureSpec.getSize(widthMeasureSpec)
        val height = keyboard?.height ?: 400
        setMeasuredDimension(width, height)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val kb = keyboard ?: return
        
        for (key in kb.allKeys) {
            // Dibujar fondo de tecla
            val rect = RectF(
                key.x.toFloat() + 4,
                key.y.toFloat() + 4,
                (key.x + key.width).toFloat() - 4,
                (key.y + key.height).toFloat() - 4
            )
            canvas.drawRoundRect(rect, 8f, 8f, keyPaint)
            canvas.drawRoundRect(rect, 8f, 8f, keyBorderPaint)
            
            // Dibujar label
            val label = key.label
            if (label != null) {
                val textX = key.x + key.width / 2f
                val textY = key.y + key.height / 2f + textPaint.textSize / 3
                canvas.drawText(label, textX, textY, textPaint)
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val kb = keyboard ?: return super.onTouchEvent(event)
                val key = kb.allKeys.find { k -> k.contains(event.x, event.y) }
                if (key != null) {
                    onKeyListener?.onKey(key.code, key.label)
                    performClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    override fun performClick(): Boolean {
        return super.performClick()
    }
    
    interface OnKeyListener {
        fun onKey(code: Int, label: String?)
    }
}
