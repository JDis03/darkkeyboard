package org.dark.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Barra superior del teclado con:
 * - Sugerencias de texto (izquierda/centro)
 * - Botón clipboard (icono)
 * - Botón settings (icono)
 */
class SuggestionBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onSuggestionClick(text: String)
        fun onClipboardClick()
        fun onSettingsClick()
    }

    var listener: Listener? = null

    private val suggestions = mutableListOf<String>()
    private val suggestionRects = mutableListOf<RectF>()
    private var clipboardRect = RectF()
    private var settingsRect = RectF()

    private val density = resources.displayMetrics.density

    // Paints
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1E2A32.toInt()
    }
    private val suggestionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0BEC5.toInt()
        textSize = 13f * density
        textAlign = Paint.Align.CENTER
    }
    private val suggestionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF263238.toInt()
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF78909C.toInt()
        textSize = 15f * density
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt()
        strokeWidth = 1f * density
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt()
    }

    private var pressedIndex = -1   // index in suggestions, or -10=clipboard, -11=settings
    private val iconSize = 32f * density
    private val padding = 8f * density
    private val suggestionRadius = 4f * density

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        layoutItems(w, h)
    }

    private fun layoutItems(w: Int, h: Int) {
        suggestionRects.clear()

        // Right side: settings icon + clipboard icon
        val settingsRight = w.toFloat() - padding / 2
        val settingsLeft = settingsRight - iconSize
        settingsRect = RectF(settingsLeft, 0f, settingsRight, h.toFloat())

        val clipRight = settingsLeft - padding / 2
        val clipLeft = clipRight - iconSize
        clipboardRect = RectF(clipLeft, 0f, clipRight, h.toFloat())

        // Left side: suggestions fill remaining space
        val suggestionsWidth = clipLeft - padding
        if (suggestions.isEmpty()) return

        val sugWidth = (suggestionsWidth / suggestions.size).coerceAtMost(180f * density)
        suggestions.forEachIndexed { i, _ ->
            val left = padding + i * sugWidth
            val top = 4f * density
            val right = left + sugWidth - 4f * density
            val bottom = h - 4f * density
            suggestionRects.add(RectF(left, top, right, bottom))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Bottom divider
        canvas.drawLine(0f, h - density, w, h - density, dividerPaint)

        // Suggestions
        suggestions.forEachIndexed { i, text ->
            val rect = suggestionRects.getOrNull(i) ?: return@forEachIndexed
            val paint = if (pressedIndex == i) pressedPaint else suggestionBgPaint
            canvas.drawRoundRect(rect, suggestionRadius, suggestionRadius, paint)
            canvas.drawText(
                text, 0, text.length,
                rect.centerX(), rect.centerY() + (suggestionPaint.textSize - suggestionPaint.descent()) / 2,
                suggestionPaint
            )
        }

        // Separator before icons
        canvas.drawLine(
            clipboardRect.left - padding / 2, padding,
            clipboardRect.left - padding / 2, h - padding,
            dividerPaint
        )

        // Clipboard icon (pressed state)
        if (pressedIndex == -10) canvas.drawRoundRect(clipboardRect, suggestionRadius, suggestionRadius, pressedPaint)
        drawClipboardIcon(canvas, clipboardRect.centerX(), clipboardRect.centerY())

        // Settings icon (pressed state)
        if (pressedIndex == -11) canvas.drawRoundRect(settingsRect, suggestionRadius, suggestionRadius, pressedPaint)
        drawSettingsIcon(canvas, settingsRect.centerX(), settingsRect.centerY())
    }

    private fun drawClipboardIcon(canvas: Canvas, cx: Float, cy: Float) {
        val s = 8f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF78909C.toInt()
            strokeWidth = 1.5f * density
            style = Paint.Style.STROKE
        }
        // Clipboard body
        canvas.drawRoundRect(RectF(cx - s * 0.7f, cy - s * 0.5f, cx + s * 0.7f, cy + s), 2f * density, 2f * density, paint)
        // Clip tab on top
        paint.style = Paint.Style.FILL_AND_STROKE
        canvas.drawRoundRect(RectF(cx - s * 0.35f, cy - s * 0.9f, cx + s * 0.35f, cy - s * 0.4f), 2f * density, 2f * density, paint)
        // Lines inside
        paint.style = Paint.Style.STROKE
        canvas.drawLine(cx - s * 0.4f, cy, cx + s * 0.4f, cy, paint)
        canvas.drawLine(cx - s * 0.4f, cy + s * 0.4f, cx + s * 0.4f, cy + s * 0.4f, paint)
    }

    private fun drawSettingsIcon(canvas: Canvas, cx: Float, cy: Float) {
        val s = 7f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF78909C.toInt()
            strokeWidth = 1.5f * density
            style = Paint.Style.STROKE
        }
        // Gear circle
        canvas.drawCircle(cx, cy, s * 0.4f, paint)
        // Gear teeth (8 lines radiating out)
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val x1 = cx + (s * 0.55f * Math.cos(angle)).toFloat()
            val y1 = cy + (s * 0.55f * Math.sin(angle)).toFloat()
            val x2 = cx + (s * 0.85f * Math.cos(angle)).toFloat()
            val y2 = cy + (s * 0.85f * Math.sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = hitTest(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val idx = pressedIndex
                pressedIndex = -1
                invalidate()
                when {
                    idx >= 0 && idx < suggestions.size -> listener?.onSuggestionClick(suggestions[idx])
                    idx == -10 -> listener?.onClipboardClick()
                    idx == -11 -> listener?.onSettingsClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): Int {
        suggestionRects.forEachIndexed { i, rect -> if (rect.contains(x, y)) return i }
        if (clipboardRect.contains(x, y)) return -10
        if (settingsRect.contains(x, y)) return -11
        return -1
    }

    fun setSuggestions(list: List<String>) {
        suggestions.clear()
        suggestions.addAll(list.take(3))
        if (width > 0) layoutItems(width, height)
        invalidate()
    }

    fun clearSuggestions() {
        suggestions.clear()
        suggestionRects.clear()
        invalidate()
    }
}
