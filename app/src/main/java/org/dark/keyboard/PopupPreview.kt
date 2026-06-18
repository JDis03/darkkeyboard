package org.dark.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import timber.log.Timber
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class PopupPreview(private val context: Context) {

    private var popupWindow: PopupWindow? = null
    private var currentOptions: List<Char> = emptyList()
    private var optionViews: MutableList<TextView> = mutableListOf()
    private var selectedIndex: Int = 0

    companion object {
        private const val MAX_PER_ROW = 8
        private const val CELL_SIZE_DP = 40f
        private const val TEXT_SIZE_SP = 18f
        private const val PADDING_DP = 6f
        private const val CORNER_DP = 10f

        private val numberSymbols: Map<Char, List<Char>> = mapOf(
            '1' to listOf('!', '`'),
            '2' to listOf('@', '~'),
            '3' to listOf('#', '-'),
            '4' to listOf('$', '_'),
            '5' to listOf('%', '='),
            '6' to listOf('^', '+'),
            '7' to listOf('{', '&'),
            '8' to listOf('}', '*'),
            '9' to listOf('(', '['),
            '0' to listOf(')', ']')
        )
    }

    fun showPunctuationPopup(anchorView: View, key: Key, onCharSelected: (Char) -> Unit) {
        currentOptions = when {
            !key.popupCharacters.isNullOrEmpty() -> parsePopupChars(key.popupCharacters!!)
            key.label == "." -> listOf(',', '?', '!', '"', '\'', '@', ';', '(', ')', '&', '/', ':', '<', '>', '|', '\\')
            key.label == "," -> listOf('.', '?', '!', ';', ':', '"', '\'')
            key.label == "?" -> listOf('!', '.', ',', ':', ';')
            key.label == "!" -> listOf('?', '.', ',', ':', ';')
            key.label != null && key.label.length == 1 && key.label[0].isDigit() -> {
                numberSymbols[key.label[0]] ?: return
            }
            else -> return
        }

        selectedIndex = 0
        showPopup(anchorView, key, onCharSelected)
    }

    /**
     * Parse popup characters string, handling XML-unescaped chars.
     * Each char in the string is a separate popup option.
     */
    private fun parsePopupChars(raw: String): List<Char> = raw.toList()

    private fun showPopup(anchorView: View, key: Key, onCharSelected: (Char) -> Unit) {
        dismiss()
        optionViews.clear()

        if (key.x < 0 || key.y < 0) {
            Timber.w("Invalid key coordinates: x=${key.x}, y=${key.y}")
            return
        }

        val density = context.resources.displayMetrics.density
        val cellPx = (CELL_SIZE_DP * density).toInt()
        val padPx = (PADDING_DP * density).toInt()
        val cornerPx = CORNER_DP * density

        // Split into rows if more than MAX_PER_ROW
        val rows = currentOptions.chunked(MAX_PER_ROW)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padPx, padPx, padPx, padPx)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2C2C2E"))
                cornerRadius = cornerPx
            }
            elevation = 12f * density
        }

        var flatIndex = 0
        rows.forEach { rowChars ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            rowChars.forEach { char ->
                val idx = flatIndex++
                val tv = TextView(context).apply {
                    text = char.toString()
                    textSize = TEXT_SIZE_SP
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    minWidth = cellPx
                    minHeight = cellPx
                    setPadding(padPx, padPx, padPx, padPx)
                    background = if (idx == 0) createSelectedBackground(density, cornerPx) else null
                    setOnClickListener {
                        onCharSelected(char)
                        dismiss()
                    }
                }
                optionViews.add(tv)
                rowLayout.addView(tv)
            }
            container.addView(rowLayout)
        }

        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val popupWidth = container.measuredWidth
        val popupHeight = container.measuredHeight

        // Get anchorView position in window for correct absolute coordinates
        val windowLocation = IntArray(2)
        anchorView.getLocationInWindow(windowLocation)
        val viewLeft = windowLocation[0]
        val viewTop = windowLocation[1]

        // Center popup above the key
        val keyCenterX = viewLeft + key.x + key.width / 2
        var popupX = keyCenterX - popupWidth / 2

        // Clamp to screen edges
        val screenWidth = context.resources.displayMetrics.widthPixels
        popupX = popupX.coerceIn(0, (screenWidth - popupWidth).coerceAtLeast(0))

        val popupY = viewTop + key.y - popupHeight - (4 * density).toInt()

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            elevation = 12f * density
            isClippingEnabled = false
            try {
                showAtLocation(anchorView, Gravity.NO_GRAVITY, popupX, popupY)
            } catch (e: Exception) {
                Timber.w(e, "Failed to show popup")
                popupWindow = null
            }
        }
    }

    fun handleMove(rawX: Float, rawY: Float): Char? {
        val popup = popupWindow ?: return null
        if (!popup.isShowing) return null
        val contentView = popup.contentView as? LinearLayout ?: return null
        if (currentOptions.isEmpty()) return null

        val location = IntArray(2)
        contentView.getLocationOnScreen(location)
        val popupLeft = location[0].toFloat()
        val popupRight = popupLeft + contentView.width.toFloat()
        val popupTop = location[1].toFloat()
        val popupBottom = popupTop + contentView.height.toFloat()

        // Extended vertical range — finger can slide down to key level
        val extendedTop = popupTop - contentView.height * 2f
        val extendedBottom = popupBottom + contentView.height * 2f

        if (rawX < popupLeft || rawX > popupRight) return null
        if (rawY < extendedTop || rawY > extendedBottom) return null

        // Figure out which row the finger is in
        val rows = currentOptions.chunked(MAX_PER_ROW)
        val rowHeight = contentView.height.toFloat() / rows.size
        val rowIdx = ((rawY - popupTop) / rowHeight).toInt().coerceIn(0, rows.size - 1)
        val row = rows[rowIdx]

        val rowOffset = rowIdx * MAX_PER_ROW
        val slotWidth = contentView.width.toFloat() / row.size
        val colIdx = ((rawX - popupLeft) / slotWidth).toInt().coerceIn(0, row.size - 1)
        val newIndex = rowOffset + colIdx

        if (newIndex != selectedIndex) {
            selectedIndex = newIndex
            val density = context.resources.displayMetrics.density
            val cornerPx = CORNER_DP * density
            optionViews.forEachIndexed { i, view ->
                view.background = if (i == selectedIndex) createSelectedBackground(density, cornerPx) else null
            }
        }
        return currentOptions[selectedIndex]
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    fun isShowing(): Boolean = popupWindow?.isShowing == true

    fun getFirstOption(): Char? = currentOptions.firstOrNull()

    private fun createSelectedBackground(density: Float, cornerPx: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#1565C0"))
            cornerRadius = cornerPx
        }
    }
}
