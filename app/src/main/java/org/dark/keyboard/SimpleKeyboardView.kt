package org.dark.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

class SimpleKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Special keycodes from HackersKeyboard (negative to avoid conflicts)
        const val KEYCODE_ALT_LEFT = -57
        const val KEYCODE_PAGE_UP = -92
        const val KEYCODE_PAGE_DOWN = -93
        const val KEYCODE_ESCAPE = -111
        const val KEYCODE_FORWARD_DEL = -112
        const val KEYCODE_CTRL_LEFT = -113
        const val KEYCODE_CAPS_LOCK = -115
        const val KEYCODE_SCROLL_LOCK = -116
        const val KEYCODE_META_LEFT = -117
        const val KEYCODE_FN = -119
        const val KEYCODE_SYSRQ = -120
        const val KEYCODE_BREAK = -121
        const val KEYCODE_HOME = -122
        const val KEYCODE_END = -123
        const val KEYCODE_INSERT = -124
        const val KEYCODE_FKEY_F1 = -131
        const val KEYCODE_FKEY_F2 = -132
        const val KEYCODE_FKEY_F3 = -133
        const val KEYCODE_FKEY_F4 = -134
        const val KEYCODE_FKEY_F5 = -135
        const val KEYCODE_FKEY_F6 = -136
        const val KEYCODE_FKEY_F7 = -137
        const val KEYCODE_FKEY_F8 = -138
        const val KEYCODE_FKEY_F9 = -139
        const val KEYCODE_FKEY_F10 = -140
        const val KEYCODE_FKEY_F11 = -141
        const val KEYCODE_FKEY_F12 = -142
        const val KEYCODE_NUM_LOCK = -143
        const val KEYCODE_DPAD_UP = -19
        const val KEYCODE_DPAD_DOWN = -20
        const val KEYCODE_DPAD_LEFT = -21
        const val KEYCODE_DPAD_RIGHT = -22
        const val KEYCODE_DPAD_CENTER = -23
    }

    private var keyboard: SimpleKeyboard? = null
    private var shiftActive = false
    private var ctrlActive = false
    private var altActive = false
    private var fnActive = false

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val keyModifierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.parseColor("#B0BEC5")
        isFakeBoldText = true
    }
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A237E")
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var pressedKey: Key? = null
    private var repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null

    var onKeyListener: OnKeyListener? = null

    interface OnKeyListener {
        fun onKey(code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean, fn: Boolean)
        fun onText(text: CharSequence)
    }

    fun setKeyboard(kb: SimpleKeyboard) {
        keyboard = kb
        requestLayout()
        invalidate()
    }

    fun isShiftActive() = shiftActive
    fun isCtrlActive() = ctrlActive
    fun isAltActive() = altActive
    fun isFnActive() = fnActive

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = keyboard?.totalHeight ?: (205.6f * 1.33f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val kb = keyboard ?: return
        val density = resources.displayMetrics.density
        
        canvas.drawColor(Color.parseColor("#263238"))

        kb.rows.forEach { row ->
            val rowHeight = row.defaultKeyHeight.toFloat()
            val fontSize = (rowHeight * 0.38f).coerceAtLeast(14f * density)
            val labelFontSize = (rowHeight * 0.3f).coerceAtLeast(11f * density)
            keyTextPaint.textSize = fontSize
            keyModifierPaint.textSize = labelFontSize

            row.keys.forEach { key ->
                val isPressed = key == pressedKey
                drawKey(canvas, key, isPressed, fontSize, labelFontSize, rowHeight)
            }
        }
    }

    private fun drawKey(
        canvas: Canvas,
        key: Key,
        isPressed: Boolean,
        fontSize: Float,
        labelFontSize: Float,
        rowHeight: Float
    ) {
        val pad = 2f * resources.displayMetrics.density
        val rect = RectF(
            key.x.toFloat() + pad,
            key.y.toFloat() + pad,
            (key.x + key.width).toFloat() - pad,
            (key.y + key.height).toFloat() - pad
        )

        if (isPressed || (key.isSticky && isModifierActive(key))) {
            keyBgPaint.color = Color.parseColor("#1565C0")
        } else if (key.isModifier) {
            keyBgPaint.color = Color.parseColor("#37474F")
        } else {
            keyBgPaint.color = Color.parseColor("#455A64")
        }

        val radius = 4f * resources.displayMetrics.density
        canvas.drawRoundRect(rect, radius, radius, keyBgPaint)
        canvas.drawRoundRect(rect, radius, radius, keyBorderPaint)

        val cx = rect.centerX()
        val cy = rect.centerY()

        val label = getDisplayLabel(key) ?: return

        val paint = if (key.isModifier) {
            keyModifierPaint.apply { textSize = labelFontSize }
        } else {
            keyTextPaint.apply { textSize = fontSize }
        }

        val textY = cy + (paint.textSize - paint.descent()) / 2
        canvas.drawText(label, 0, label.length, cx, textY, paint)
    }

    private fun isModifierActive(key: Key): Boolean {
        return when (key.code) {
            Key.CODE_SHIFT -> shiftActive
            Key.CODE_CTRL_LEFT -> ctrlActive
            else -> false
        }
    }

    private fun getDisplayLabel(key: Key): String? {
        val label = key.label
        if (label != null && label.isNotEmpty()) return label

        return when (key.code) {
            Key.CODE_SHIFT -> "⇧"
            Key.CODE_DELETE -> "⌫"
            Key.CODE_ENTER -> "↵"
            Key.CODE_SPACE -> " "
            Key.CODE_TAB -> "Tab"
            Key.CODE_MODE_CHANGE -> "?123"
            Key.CODE_SETTINGS -> "⚙"
            Key.CODE_F1 -> "F1"
            Key.CODE_CTRL_LEFT -> "Ctrl"
            Key.CODE_FN -> "Fn"
            else -> null
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKey(event.x.toInt(), event.y.toInt())
                pressedKey = key
                if (key?.isRepeatable == true) {
                    scheduleRepeat(key)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val key = findKey(event.x.toInt(), event.y.toInt())
                if (key != pressedKey) {
                    cancelRepeat()
                    pressedKey = key
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelRepeat()
                pressedKey?.let { key ->
                    handleKeyPress(key)
                }
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelRepeat()
                pressedKey = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun findKey(x: Int, y: Int): Key? {
        return keyboard?.allKeys?.find { it.contains(x, y) }
    }

    private fun handleKeyPress(key: Key) {
        Log.d("SimpleKeyboardView", "handleKeyPress: code=${key.code}, label=${key.label}")
        when (key.code) {
            Key.CODE_SHIFT -> {
                shiftActive = !shiftActive
                invalidate()
            }
            Key.CODE_DELETE -> {
                onKeyListener?.onKey(Key.CODE_DELETE, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_MODE_CHANGE -> {
                // Notificar al IME para cambiar de layout
                onKeyListener?.onKey(Key.CODE_MODE_CHANGE, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_ENTER -> {
                onKeyListener?.onKey(Key.CODE_ENTER, shiftActive, ctrlActive, altActive, fnActive)
                shiftActive = false
                invalidate()
            }
            Key.CODE_TAB -> {
                onKeyListener?.onKey(Key.CODE_TAB, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_CTRL_LEFT -> {
                ctrlActive = !ctrlActive
                invalidate()
            }
            KEYCODE_ALT_LEFT -> {
                altActive = !altActive
                invalidate()
            }
            KEYCODE_META_LEFT -> {
                // Meta key (no specific action for now, could add metaActive state)
                onKeyListener?.onKey(KEYCODE_META_LEFT, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_SETTINGS -> {
                onKeyListener?.onKey(Key.CODE_SETTINGS, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_CLOSE -> {
                onKeyListener?.onKey(Key.CODE_CLOSE, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_SWITCH_INPUT -> {
                onKeyListener?.onKey(Key.CODE_SWITCH_INPUT, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_F1 -> {
                onKeyListener?.onKey(Key.CODE_F1, shiftActive, ctrlActive, altActive, fnActive)
            }
            Key.CODE_SPACE -> {
                onKeyListener?.onKey(Key.CODE_SPACE, shiftActive, ctrlActive, altActive, fnActive)
                shiftActive = false
                invalidate()
            }
            else -> {
                val label = if (shiftActive && key.shiftLabel != null) {
                    key.shiftLabel
                } else {
                    key.label
                }
                if (label != null && label.length == 1 && key.code == 0) {
                    onKeyListener?.onText(label)
                } else {
                    onKeyListener?.onKey(key.code, shiftActive, ctrlActive, altActive, fnActive)
                }
                if (!key.isSticky && !key.isModifier) {
                    shiftActive = false
                    invalidate()
                }
            }
        }
    }

    private fun scheduleRepeat(key: Key) {
        cancelRepeat()
        repeatRunnable = object : Runnable {
            override fun run() {
                handleKeyPress(key)
                repeatHandler.postDelayed(this, 50L)
            }
        }
        repeatHandler.postDelayed(repeatRunnable!!, 400L)
    }

    private fun cancelRepeat() {
        repeatRunnable?.let {
            repeatHandler.removeCallbacks(it)
            repeatRunnable = null
        }
    }
}