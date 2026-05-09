package org.dark.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class PopupPreview(private val context: Context) {
    
    private var popupWindow: PopupWindow? = null
    
    private var currentOptions: List<Char> = emptyList()
    private var optionViews: MutableList<TextView> = mutableListOf()

    companion object {
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
            key.label == "." -> listOf('.', ',', '?', '!', ':', ';')
            key.label == "," -> listOf(',', '.', '?', '!', ':', ';')
            key.label == "?" -> listOf('?', '!', '.', ',', ':', ';')
            key.label == "!" -> listOf('!', '?', '.', ',', ':', ';')
            key.label != null && key.label.length == 1 && key.label[0].isDigit() -> {
                val syms = numberSymbols[key.label[0]] ?: return
                syms
            }
            else -> return
        }

        showPopup(anchorView, key, onCharSelected)
    }

    private fun showPopup(anchorView: View, key: Key, onCharSelected: (Char) -> Unit) {
        dismiss()
        optionViews.clear()
        
        val density = context.resources.displayMetrics.density
        
        // Create popup layout
        val paddingDp = (8 * density).toInt()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(paddingDp, paddingDp, paddingDp, paddingDp)
            background = createPopupBackground()
            elevation = 8f * density
        }
        
        // Add character options
        val paddingH = (16 * density).toInt()
        val paddingV = (12 * density).toInt()
        
        currentOptions.forEachIndexed { index, char ->
            val textView = TextView(context).apply {
                text = char.toString()
                textSize = 24f
                setTextColor(Color.WHITE)
                setPadding(paddingH, paddingV, paddingH, paddingV)
                gravity = Gravity.CENTER
                minWidth = (48 * density).toInt()
                
                // Highlight if it's the main character
                if (index == 0) {
                    background = createSelectedBackground()
                }
                
                setOnClickListener {
                    onCharSelected(char)
                    dismiss()
                }
            }
            optionViews.add(textView)
            layout.addView(textView)
        }
        
        // Measure popup to center it
        layout.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        
        // Create and show popup
        popupWindow = PopupWindow(
            layout,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false  // Not focusable so we can track finger movement
        ).apply {
            elevation = 8f * density
            
            // Calculate position to center popup above the key
            val location = IntArray(2)
            key.x.let { keyX ->
                key.y.let { keyY ->
                    // Center popup horizontally on the key
                    val popupWidth = layout.measuredWidth
                    val keyWidth = key.width
                    val x = keyX + (keyWidth - popupWidth) / 2
                    
                    // Position popup above the key with some margin
                    val popupHeight = layout.measuredHeight
                    val y = keyY - popupHeight - (8 * density).toInt()
                    
                    showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y)
                }
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

        // Generous vertical range — finger can be above popup and still swipe horizontally
        val extendedTop = popupTop - contentView.height * 3f
        val extendedBottom = popupBottom + contentView.height * 1.5f

        if (rawX < popupLeft || rawX > popupRight) return null
        if (rawY < extendedTop || rawY > extendedBottom) return null

        val slotWidth = contentView.width.toFloat() / currentOptions.size
        val index = ((rawX - popupLeft) / slotWidth).toInt().coerceIn(0, currentOptions.size - 1)

        optionViews.forEachIndexed { i, view ->
            view.background = if (i == index) createSelectedBackground() else null
        }
        return currentOptions[index]
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    fun isShowing(): Boolean {
        return popupWindow?.isShowing == true
    }

    fun getFirstOption(): Char? = currentOptions.firstOrNull()
    
    private fun createPopupBackground(): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(Color.parseColor("#455A64"))
            cornerRadius = 12f * density
        }
    }
    
    private fun createSelectedBackground(): GradientDrawable {
        val density = context.resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(Color.parseColor("#1565C0"))
            cornerRadius = 8f * density
        }
    }
}
