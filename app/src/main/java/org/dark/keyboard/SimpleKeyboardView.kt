package org.dark.keyboard

import android.content.Context
import android.graphics.Canvas
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

    var keyboardTheme: KeyboardTheme = KeyboardTheme.DARK
        set(value) {
            field = value
            applyTheme()
            invalidate()
        }

    private var keyboard: SimpleKeyboard? = null
    val modifierState = ModifierState()

    private val keyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val keyModifierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private var pressedKey: Key? = null
    private var repeatHandler = Handler(Looper.getMainLooper())
    private var repeatRunnable: Runnable? = null
    private var popupPreview: PopupPreview? = null
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isPopupShowing = false
    private var selectedPopupChar: Char? = null

    init {
        applyTheme()
    }

    private fun applyTheme() {
        val t = keyboardTheme
        keyTextPaint.color = t.textNormal
        keyModifierPaint.color = t.textModifier
        keyPressedPaint.color = t.keyPressed
        keyBorderPaint.color = t.keyBorder
    }

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

    fun isShiftActive() = modifierState.isShiftActive()
    fun isCtrlActive() = modifierState.isCtrlActive()
    fun isAltActive() = modifierState.isAltActive()
    fun isFnActive() = modifierState.isFnActive()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = keyboard?.totalHeight ?: (205.6f * 1.33f * resources.displayMetrics.density).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val kb = keyboard ?: return
        val density = resources.displayMetrics.density

        canvas.drawColor(keyboardTheme.background)

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
        val density = resources.displayMetrics.density
        val pad = 2f * density
        val rect = RectF(
            key.x.toFloat() + pad,
            key.y.toFloat() + pad,
            (key.x + key.width).toFloat() - pad,
            (key.y + key.height).toFloat() - pad
        )

        keyBgPaint.color = when {
            isPressed || (key.isSticky && isModifierActive(key)) -> keyboardTheme.keyActive
            key.isModifier -> keyboardTheme.keyModifier
            else -> keyboardTheme.keyNormal
        }

        val radius = 4f * density
        canvas.drawRoundRect(rect, radius, radius, keyBgPaint)
        canvas.drawRoundRect(rect, radius, radius, keyBorderPaint)

        val cx = rect.centerX()
        val cy = rect.centerY()

        val icon = getDisplayIcon(key)
        if (icon != null) {
            drawIcon(canvas, icon, cx, cy, fontSize, key)
            return
        }

        val label = getDisplayLabel(key) ?: return

        val paint = if (key.isModifier) {
            keyModifierPaint.apply { textSize = labelFontSize }
        } else {
            keyTextPaint.apply { textSize = fontSize }
        }

        val textY = cy + (paint.textSize - paint.descent()) / 2
        canvas.drawText(label, 0, label.length, cx, textY, paint)
    }

    private fun getDisplayIcon(key: Key): String? = when (key.code) {
        Key.CODE_SHIFT -> "shift"
        Key.CODE_DELETE -> "delete"
        Key.CODE_ENTER -> "enter"
        Key.CODE_CTRL_LEFT -> "ctrl"
        Key.CODE_SPACE -> "space"
        else -> null
    }

    private fun drawIcon(canvas: Canvas, icon: String, cx: Float, cy: Float, size: Float, key: Key) {
        val active = isModifierActive(key)
        val iconColor = when {
            active -> keyboardTheme.textActive
            key.isModifier -> keyboardTheme.textModifier
            else -> keyboardTheme.textNormal
        }
        // Clone paint per call — don't mutate shared paints
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = iconColor
            textSize = size
            isFakeBoldText = true
        }

        val s = size * 0.5f
        when (icon) {
            "shift" -> drawShiftIcon(canvas, cx, cy, s, paint)
            "delete" -> drawDeleteIcon(canvas, cx, cy, s, paint)
            "enter" -> drawEnterIcon(canvas, cx, cy, s, paint)
            "ctrl" -> drawCtrlIcon(canvas, cx, cy, s, paint)
            "space" -> drawSpaceIndicator(canvas, cx, cy, s)
        }
    }

    private fun drawShiftIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val path = android.graphics.Path()
        // Upward arrow with outline (Gboard style)
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s * 0.8f, cy + s * 0.3f)
        path.lineTo(cx + s * 0.3f, cy + s * 0.3f)
        path.lineTo(cx + s * 0.3f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.9f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.3f)
        path.lineTo(cx - s * 0.8f, cy + s * 0.3f)
        path.close()
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }

    private fun drawDeleteIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val path = android.graphics.Path()
        // Backspace arrow (Gboard style: right-pointing arrow with x)
        val arrowSize = s * 0.6f
        // Arrow pointing right with angled left side
        path.moveTo(cx - s * 0.3f, cy - arrowSize)
        path.lineTo(cx + s * 0.7f, cy - arrowSize)
        path.lineTo(cx + s * 0.7f, cy + arrowSize)
        path.lineTo(cx - s * 0.3f, cy + arrowSize)
        path.lineTo(cx - s * 0.6f, cy)
        path.close()
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)

        // X mark inside
        paint.strokeWidth = s * 0.2f
        paint.style = Paint.Style.STROKE
        paint.color = keyboardTheme.background
        val x = cx + s * 0.2f
        val y = cy
        val xs = s * 0.25f
        canvas.drawLine(x - xs, y - xs, x + xs, y + xs, paint)
        canvas.drawLine(x + xs, y - xs, x - xs, y + xs, paint)
    }

    private fun drawEnterIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        val strokeW = s * 0.22f
        paint.strokeWidth = strokeW
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND

        // Vertical line going down first
        canvas.drawLine(cx + s * 0.3f, cy - s * 0.6f, cx + s * 0.3f, cy + s * 0.3f, paint)
        // Horizontal line going left
        canvas.drawLine(cx + s * 0.3f, cy + s * 0.3f, cx - s * 0.5f, cy + s * 0.3f, paint)
        // Arrow tip (up-left corner)
        canvas.drawLine(cx - s * 0.5f, cy + s * 0.3f, cx - s * 0.25f, cy + s * 0.05f, paint)
        canvas.drawLine(cx - s * 0.5f, cy + s * 0.3f, cx - s * 0.25f, cy + s * 0.55f, paint)
    }

    private fun drawCtrlIcon(canvas: Canvas, cx: Float, cy: Float, s: Float, paint: Paint) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.18f
        paint.strokeCap = Paint.Cap.ROUND

        val r = s * 0.65f
        // "^" symbol made of two straight lines meeting at top (Gboard style)
        canvas.drawLine(cx - r, cy + s * 0.2f, cx, cy - s * 0.7f, paint)
        canvas.drawLine(cx, cy - s * 0.7f, cx + r, cy + s * 0.2f, paint)
    }

    private fun drawSpaceIndicator(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val density = resources.displayMetrics.density
        val indicatorW = s * 1.6f
        val indicatorH = 2f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = keyboardTheme.textNormal.copyAlpha(40)
        }
        canvas.drawRoundRect(
            cx - indicatorW / 2, cy + s * 0.7f,
            cx + indicatorW / 2, cy + s * 0.7f + indicatorH,
            indicatorH / 2, indicatorH / 2,
            paint
        )
    }

    private fun Int.copyAlpha(alpha: Int): Int = (this and 0x00FFFFFF) or (alpha shl 24)

    private fun isModifierActive(key: Key): Boolean = when (key.code) {
        Key.CODE_SHIFT -> modifierState.isShiftActive()
        Key.CODE_CTRL_LEFT -> modifierState.isCtrlActive()
        Key.CODE_ALT_LEFT -> modifierState.isAltActive()
        Key.CODE_FN -> modifierState.isFnActive()
        else -> false
    }

    private fun getDisplayLabel(key: Key): String? {
        // First check if we have a label
        val label = key.label
        if (label != null && label.isNotEmpty()) {
            // If shift is active and this is a letter key, show uppercase
            if (modifierState.isShiftActive() && label.length == 1 && label[0].isLetter()) {
                return label.uppercase()
            }
            return label
        }

        // Map special keycodes to symbols
        val result = when (key.code) {
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
            KEYCODE_ALT_LEFT -> "Alt"
            else -> {
                // If no label and no special code, try to use the code as char
                if (key.code > 0 && key.code < 128) {
                    val char = key.code.toChar().toString()
                    // Apply shift for letters
                    if (modifierState.isShiftActive() && char.length == 1 && char[0].isLetter()) {
                        char.uppercase()
                    } else {
                        char
                    }
                } else {
                    Log.e("SimpleKeyboardView", "No label for key: code=${key.code}")
                    null
                }
            }
        }
        return result
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val key = findKey(event.x.toInt(), event.y.toInt())
                pressedKey = key
                if (key != null) {
                    if (key.isRepeatable) {
                        scheduleRepeat(key)
                    } else if (isPunctuationKey(key)) {
                        scheduleLongPress(key)
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                // If popup is showing, track finger movement across options
                if (isPopupShowing) {
                    selectedPopupChar = popupPreview?.handleMove(event.rawX, event.rawY)
                    return true
                }
                
                val key = findKey(event.x.toInt(), event.y.toInt())
                if (key != pressedKey) {
                    cancelRepeat()
                    cancelLongPressInternal()
                    pressedKey = key
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelRepeat()
                cancelLongPressInternal()
                
                // If popup was showing, insert the selected character
                if (isPopupShowing) {
                    selectedPopupChar?.let { char ->
                        onKeyListener?.onText(char.toString())
                    }
                    popupPreview?.dismiss()
                    isPopupShowing = false
                    selectedPopupChar = null
                } else {
                    // Normal key press
                    pressedKey?.let { key ->
                        handleKeyPress(key)
                    }
                }
                
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelRepeat()
                cancelLongPressInternal()
                popupPreview?.dismiss()
                isPopupShowing = false
                selectedPopupChar = null
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
        val s = modifierState.isShiftActive()
        val c = modifierState.isCtrlActive()
        val a = modifierState.isAltActive()
        val f = modifierState.isFnActive()
        Log.d("SimpleKeyboardView", "handleKeyPress: code=${key.code}, label=${key.label}")
        when (key.code) {
            Key.CODE_SHIFT -> { modifierState.toggleShift(); invalidate() }
            Key.CODE_DELETE -> { onKeyListener?.onKey(Key.CODE_DELETE, s, c, a, f) }
            Key.CODE_MODE_CHANGE -> { onKeyListener?.onKey(Key.CODE_MODE_CHANGE, s, c, a, f) }
            Key.CODE_ENTER -> {
                onKeyListener?.onKey(Key.CODE_ENTER, s, c, a, f)
                modifierState.setShift(false); invalidate()
            }
            Key.CODE_TAB -> { onKeyListener?.onKey(Key.CODE_TAB, s, c, a, f) }
            Key.CODE_CTRL_LEFT -> { modifierState.toggleCtrl(); invalidate() }
            KEYCODE_ALT_LEFT -> { modifierState.toggleAlt(); invalidate() }
            KEYCODE_FN -> { modifierState.toggleFn(); invalidate() }
            KEYCODE_META_LEFT -> { onKeyListener?.onKey(KEYCODE_META_LEFT, s, c, a, f) }
            Key.CODE_SETTINGS -> { onKeyListener?.onKey(Key.CODE_SETTINGS, s, c, a, f) }
            Key.CODE_CLOSE -> { onKeyListener?.onKey(Key.CODE_CLOSE, s, c, a, f) }
            Key.CODE_SWITCH_INPUT -> { onKeyListener?.onKey(Key.CODE_SWITCH_INPUT, s, c, a, f) }
            Key.CODE_F1 -> { onKeyListener?.onKey(Key.CODE_F1, s, c, a, f) }
            Key.CODE_SPACE -> {
                onKeyListener?.onKey(Key.CODE_SPACE, s, c, a, f)
                modifierState.setShift(false); invalidate()
            }
            else -> {
                val label = if (s && key.shiftLabel != null) key.shiftLabel else key.label
                val hasMods = c || a || f
                if (label != null && label.length == 1 && key.code == 0 && !hasMods) {
                    onKeyListener?.onText(label)
                } else {
                    val codeToSend = if (key.code == 0 && label != null && label.isNotEmpty()) {
                        label[0].code
                    } else {
                        key.code
                    }
                    onKeyListener?.onKey(codeToSend, s, c, a, f)
                }
                if (!key.isSticky && !key.isModifier) {
                    modifierState.clearAll(); invalidate()
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
    
    private fun isPunctuationKey(key: Key?): Boolean {
        return key?.label in listOf(".", ",", "?", "!")
    }
    
    private fun scheduleLongPress(key: Key) {
        cancelLongPressInternal()
        if (popupPreview == null) {
            popupPreview = PopupPreview(context)
        }
        
        longPressRunnable = Runnable {
            popupPreview?.showPunctuationPopup(this, key) { char ->
                // This callback won't be used anymore, we handle selection on ACTION_UP
            }
            isPopupShowing = true
            selectedPopupChar = null  // Will be set by handleMove
        }
        longPressHandler.postDelayed(longPressRunnable!!, 300L)  // Reduced to 300ms for faster response
    }
    
    private fun cancelLongPressInternal() {
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
            longPressRunnable = null
        }
    }
}