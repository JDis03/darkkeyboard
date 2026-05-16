package org.dark.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow

/**
 * Panel flotante de clipboard que muestra el historial de elementos copiados.
 * Se abre desde el botón clipboard de SuggestionBarView.
 */
class ClipboardPopup(
    private val context: Context,
    private val onPaste: (String) -> Unit
) {
    private val density = context.resources.displayMetrics.density
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var popup: PopupWindow? = null

    fun show(anchor: View) {
        dismiss()

        val items = getClipboardItems()
        val view = ClipboardPanelView(context, items) { text ->
            onPaste(text)
            dismiss()
        }

        val width = (300 * density).toInt()
        val height = ViewGroup.LayoutParams.WRAP_CONTENT

        popup = PopupWindow(view, width, height, true).apply {
            isOutsideTouchable = true
            elevation = 8f * density
            showAsDropDown(anchor, 0, 0)
        }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    private fun getClipboardItems(): List<String> {
        val items = mutableListOf<String>()
        val clip = clipboardManager.primaryClip ?: return items
        for (i in 0 until clip.itemCount) {
            val text = clip.getItemAt(i)?.text?.toString()
            if (!text.isNullOrBlank()) items.add(text)
        }
        return items
    }
}

/**
 * Custom View para el panel de clipboard
 */
private class ClipboardPanelView(
    context: Context,
    private val items: List<String>,
    private val onSelect: (String) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val itemHeight = 44f * density
    private val padding = 12f * density
    private val radius = 8f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF263238.toInt()
    }
    private val itemBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37474F.toInt()
    }
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF455A64.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFECEFF1.toInt()
        textSize = 13f * density
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF78909C.toInt()
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
    }
    private val dividerPaint = Paint().apply {
        color = 0xFF37474F.toInt()
        strokeWidth = density
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF90A4AE.toInt()
        textSize = 11f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var pressedIndex = -1
    private val itemRects = mutableListOf<RectF>()
    private val titleHeight = 28f * density

    init {
        val totalItems = if (items.isEmpty()) 1 else items.size
        val h = titleHeight + totalItems * itemHeight + padding
        minimumHeight = h.toInt()
        minimumWidth = (300 * density).toInt()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        itemRects.clear()
        items.forEachIndexed { i, _ ->
            val top = titleHeight + i * itemHeight + padding / 2
            itemRects.add(RectF(padding / 2, top, w - padding / 2, top + itemHeight - padding / 4))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val totalItems = if (items.isEmpty()) 1 else items.size
        val h = (titleHeight + totalItems * itemHeight + padding).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Background
        canvas.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, bgPaint)

        // Title
        canvas.drawText("Clipboard", w / 2, titleHeight * 0.7f, titlePaint)
        canvas.drawLine(0f, titleHeight, w, titleHeight, dividerPaint)

        if (items.isEmpty()) {
            canvas.drawText("Clipboard vacío", w / 2, titleHeight + itemHeight * 0.6f, hintPaint)
            return
        }

        // Items
        items.forEachIndexed { i, text ->
            val rect = itemRects.getOrNull(i) ?: return@forEachIndexed
            val paint = if (pressedIndex == i) pressedPaint else itemBgPaint
            canvas.drawRoundRect(rect, 4f * density, 4f * density, paint)

            // Truncate text if too long
            val maxWidth = rect.width() - padding * 2
            val displayText = if (textPaint.measureText(text) > maxWidth) {
                var end = text.length
                while (end > 0 && textPaint.measureText(text, 0, end) + textPaint.measureText("…") > maxWidth) end--
                text.substring(0, end) + "…"
            } else text

            val textY = rect.centerY() + (textPaint.textSize - textPaint.descent()) / 2
            canvas.drawText(displayText, rect.left + padding, textY, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = itemRects.indexOfFirst { it.contains(x, y) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val idx = pressedIndex
                pressedIndex = -1
                invalidate()
                if (idx >= 0 && idx < items.size) onSelect(items[idx])
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedIndex = -1
                invalidate()
            }
        }
        return false
    }
}
