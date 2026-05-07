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
    
    fun showPunctuationPopup(anchorView: View, key: Key, onCharSelected: (Char) -> Unit) {
        // Define punctuation options based on the key
        val options = when {
            key.label == "." -> listOf('.', ',', '?', '!', ':', ';')
            key.label == "," -> listOf(',', '.', '?', '!', ':', ';')
            key.label == "?" -> listOf('?', '!', '.', ',', ':', ';')
            key.label == "!" -> listOf('!', '?', '.', ',', ':', ';')
            else -> return
        }
        
        dismiss()
        
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
        
        options.forEach { char ->
            val textView = TextView(context).apply {
                text = char.toString()
                textSize = 24f
                setTextColor(Color.WHITE)
                setPadding(paddingH, paddingV, paddingH, paddingV)
                gravity = Gravity.CENTER
                
                // Highlight if it's the main character
                if (char == options[0]) {
                    background = createSelectedBackground()
                }
                
                setOnClickListener {
                    onCharSelected(char)
                    dismiss()
                }
            }
            layout.addView(textView)
        }
        
        // Create and show popup
        popupWindow = PopupWindow(
            layout,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            elevation = 8f * density
            
            // Show above the key
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            showAtLocation(
                anchorView,
                Gravity.NO_GRAVITY,
                location[0] - (50 * density).toInt(),
                location[1] - (80 * density).toInt()
            )
        }
    }
    
    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }
    
    fun isShowing(): Boolean {
        return popupWindow?.isShowing == true
    }
    
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
