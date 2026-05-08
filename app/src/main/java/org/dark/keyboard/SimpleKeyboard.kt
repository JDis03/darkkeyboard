package org.dark.keyboard

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import android.util.TypedValue
import android.util.Xml
import kotlin.math.min

class SimpleKeyboard(
    val rows: List<KeyboardRow>,
    val totalWidth: Int,
    val totalHeight: Int
) {
    val allKeys: List<Key> get() = rows.flatMap { it.keys }

    companion object {
        private const val TAG = "SimpleKeyboard"

        fun fromXml(
            context: Context,
            xmlResId: Int,
            screenWidth: Int,
            screenHeight: Int,
            showExtensionRow: Boolean = true
        ): SimpleKeyboard {
            val density = context.resources.displayMetrics.density
            
            // Fórmula original de HeliBoard, pero reducida para dejar espacio para botones
            val defaultHeightDp = 205.6f * 1.33f
            val defaultHeightPx = defaultHeightDp * density
            // Reducir de 46% a 38% para dejar ~8% para botones del sistema
            val maxHeight = screenHeight * 0.38f
            val keyboardHeight = min(defaultHeightPx, maxHeight).toInt()
            
            Log.d(TAG, "Keyboard height: $keyboardHeight px (${keyboardHeight / density} dp), screen: $screenHeight px")
            val verticalGapPx = (1.5f * density).toInt()

            // Distribución: 5 filas + 4 gaps
            // Restar gaps del total antes de calcular alturas de filas
            val totalGapSpace = verticalGapPx * 4
            val availableHeightForRows = keyboardHeight - totalGapSpace
            
            // Distribución de altura disponible: 18%, 21%, 21%, 21%, 19%
            val numberRowHeight = (availableHeightForRows * 0.18f).toInt()
            val rowHeight = (availableHeightForRows * 0.21f).toInt()  
            val bottomRowHeight = (availableHeightForRows * 0.19f).toInt()
            
            Log.d(TAG, "Height calc: keyboard=$keyboardHeight, gaps=$totalGapSpace, available=$availableHeightForRows")
            Log.d(TAG, "Row heights: number=$numberRowHeight, normal=$rowHeight, bottom=$bottomRowHeight")
            val defaultKeyWidth = screenWidth / 10

            val parser = context.resources.getXml(xmlResId)
            val rows = mutableListOf<KeyboardRow>()
            var currentRow: KeyboardRow? = null
            var currentX = 0
            var currentY = 0
            var rowCount = 0
            var hasBottomRowWithMode = false  // Flag para solo tomar 1 row bottom con keyboardMode
            var keyboardDefaultWidth = defaultKeyWidth
            var keyboardHorizontalGap = 0
            var keyboardKeyHeight = rowHeight
            var keyboardVerticalGap = verticalGapPx

            try {
                var eventType = parser.eventType
                var tagCount = 0
                while (eventType != XmlResourceParser.END_DOCUMENT) {
                    if (eventType == XmlResourceParser.START_TAG && parser.name == "Row") {
                        tagCount++
                        Log.d(TAG, "Parser saw Row START_TAG #$tagCount")
                    }
                    when (eventType) {
                        XmlResourceParser.START_TAG -> {
                            when (parser.name) {
                                "Keyboard" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        when (parser.getAttributeName(i)) {
                                            "keyWidth" -> {
                                                keyboardDefaultWidth = parseDimension(
                                                    parser.getAttributeValue(i), screenWidth, 0
                                                )
                                            }
                                            "keyHeight" -> {
                                                keyboardKeyHeight = parseDimension(
                                                    parser.getAttributeValue(i), screenHeight, 0
                                                )
                                            }
                                            "horizontalGap" -> {
                                                keyboardHorizontalGap = parseDimension(
                                                    parser.getAttributeValue(i), screenWidth, 0
                                                )
                                            }
                                            "verticalGap" -> {
                                                keyboardVerticalGap = parseDimension(
                                                    parser.getAttributeValue(i), screenHeight, 0
                                                )
                                            }
                                        }
                                    }
                                }
                                "Row" -> {
                                    // Use TypedArray to read Row attributes properly
                                    val rowAttrs = context.obtainStyledAttributes(
                                        Xml.asAttributeSet(parser),
                                        R.styleable.Keyboard_Row
                                    )
                                    
                                    val keyboardModeId = rowAttrs.getResourceId(R.styleable.Keyboard_Row_keyboardMode, 0)
                                    val isExtension = rowAttrs.getBoolean(R.styleable.Keyboard_Row_extension, false)
                                    val rowEdgeFlags = rowAttrs.getInt(R.styleable.Keyboard_Row_rowEdgeFlags, 0)
                                    val hasKeyboardMode = keyboardModeId != 0
                                    
                                    rowAttrs.recycle()
                                    
                                    Log.d(TAG, "ROW START: rowCount=$rowCount, hasKeyboardMode=$hasKeyboardMode, modeId=$keyboardModeId")
                                    
                                    val rowKeys = mutableListOf<Key>()
                                    
                                    // Skip rows con keyboardMode - son alternativas que Android debería filtrar
                                    // Pero nuestro parser simple no soporta modes, entonces skip
                                    if (hasKeyboardMode) {
                                        Log.d(TAG, "  -> WILL SKIP this row (has keyboardMode)")
                                        currentRow = null
                                    } else if (isExtension && !showExtensionRow) {
                                        Log.d(TAG, "  -> WILL SKIP extension row (user preference)")
                                        currentRow = null
                                    } else if (rowCount >= 6) {
                                        Log.d(TAG, "  -> WILL SKIP: already have 6 rows")
                                        currentRow = null
                                    } else {
                                        Log.d(TAG, "  -> WILL CREATE row")
                                        currentRow = KeyboardRow(
                                            keys = rowKeys,
                                            isExtension = isExtension,
                                            keyboardMode = 0
                                        )
                                        currentX = 0
                                        val thisRowHeight = when {
                                            isExtension -> numberRowHeight
                                            rowCount >= 4 -> bottomRowHeight
                                            else -> rowHeight
                                        }
                                        currentRow!!.defaultKeyHeight = thisRowHeight
                                        currentRow!!.defaultKeyWidth = keyboardDefaultWidth
                                        currentRow!!.y = currentY
                                    }
                                }
                                "Key" -> {
                                    currentRow?.let { row ->
                                        // Use TypedArray to read custom attributes from attrs-keyboard.xml
                                        val a = context.obtainStyledAttributes(
                                            Xml.asAttributeSet(parser),
                                            R.styleable.Keyboard_Key
                                        )
                                        
                                        val keyWidthAttr = getAttrValue(parser, "keyWidth")
                                        val keyWidth = if (keyWidthAttr != null) {
                                            parseDimension(keyWidthAttr, screenWidth, keyboardDefaultWidth)
                                        } else {
                                            keyboardDefaultWidth
                                        }
                                        val hGapAttr = getAttrValue(parser, "horizontalGap")
                                        val hGap = if (hGapAttr != null) {
                                            parseDimension(hGapAttr, screenWidth, keyboardHorizontalGap)
                                        } else {
                                            keyboardHorizontalGap
                                        }
                                        
                                        // Read codes using TypedArray (resolves @integer/key_* references)
                                        val codeFromXml = a.getInt(R.styleable.Keyboard_Key_codes, Int.MIN_VALUE)
                                        
                                        val labelAttr = a.getString(R.styleable.Keyboard_Key_keyLabel)
                                        val shiftLabelAttr = a.getString(R.styleable.Keyboard_Key_shiftLabel)
                                        val outputTextAttr = a.getString(R.styleable.Keyboard_Key_keyOutputText)
                                        
                                        // Read custom boolean attributes (isModifier, isSticky, isRepeatable)
                                        val isModifier = a.getBoolean(R.styleable.Keyboard_Key_isModifier, false)
                                        val isSticky = a.getBoolean(R.styleable.Keyboard_Key_isSticky, false)
                                        val isRepeatable = a.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false)
                                        val edgeFlags = a.getInt(R.styleable.Keyboard_Key_keyEdgeFlags, 0)
                                        
                                        a.recycle()

                                        var code: Int
                                        if (codeFromXml != Int.MIN_VALUE) {
                                            code = codeFromXml
                                        } else if (labelAttr != null && labelAttr.length == 1) {
                                            code = labelAttr[0].code
                                        } else {
                                            code = 0
                                        }
                                        if (outputTextAttr != null && code == 0) {
                                            code = 0
                                        }
                                        
                                        // Debug log for special keys
                                        if (code == 9 || code == 10) {
                                            Log.d(TAG, "Parsed special key: label=$labelAttr code=$code")
                                        }

                                        val key = Key(
                                            label = labelAttr,
                                            code = code,
                                            shiftLabel = shiftLabelAttr,
                                            x = currentX + hGap,
                                            y = row.y,
                                            width = keyWidth - hGap,
                                            height = row.defaultKeyHeight,
                                            isModifier = isModifier,
                                            isSticky = isSticky,
                                            isRepeatable = isRepeatable,
                                            edgeFlags = edgeFlags
                                        )
                                        row.keys.add(key)
                                        currentX += keyWidth
                                        Log.d(TAG, "Added key: label=$labelAttr, code=$code, x=${key.x}, width=${key.width}")
                                    }
                                }
                            }
                        }
                        XmlResourceParser.END_TAG -> {
                            when (parser.name) {
                                "Row" -> {
                                    currentRow?.let { row ->
                                        Log.d(TAG, "Closing Row: keyboardMode=${row.keyboardMode}, keys=${row.keys.size}, rowCount=$rowCount")
                                        if (row.keyboardMode == -1 || row.keyboardMode == 0) {
                                            // Centrar row si no usa todo el ancho
                                            val totalRowWidth = row.keys.sumOf { it.width + keyboardHorizontalGap }
                                            if (totalRowWidth < screenWidth) {
                                                val offset = (screenWidth - totalRowWidth) / 2
                                                Log.d(TAG, "  -> Centering row: totalWidth=$totalRowWidth, offset=$offset")
                                                row.keys.forEach { key ->
                                                    key.x += offset
                                                }
                                            }
                                            
                                            rows.add(row)
                                            currentY += row.defaultKeyHeight + keyboardVerticalGap
                                            rowCount++
                                            Log.d(TAG, "  -> Added row #$rowCount with ${row.keys.size} keys")
                                        } else {
                                            Log.d(TAG, "  -> SKIPPED row (keyboardMode=${row.keyboardMode})")
                                        }
                                    }
                                    currentRow = null
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing keyboard XML", e)
            } finally {
                parser.close()
            }

            val actualHeight = if (rows.isNotEmpty()) {
                val lastRow = rows.last()
                lastRow.y + lastRow.defaultKeyHeight + keyboardVerticalGap
            } else {
                keyboardHeight
            }

            Log.d(TAG, "Parsed ${rows.size} rows, ${rows.sumOf { it.keys.size }} keys, " +
                    "height=$actualHeight, width=$screenWidth")

            return SimpleKeyboard(rows, screenWidth, actualHeight)
        }

        private fun parseDimension(value: String, base: Int, default: Int): Int {
            if (value.isNullOrEmpty()) return default
            return try {
                when {
                    value.endsWith("%p") -> {
                        val pct = value.removeSuffix("%p").toFloat()
                        (base * pct / 100f).toInt()
                    }
                    value.endsWith("dp") || value.endsWith("dip") -> {
                        val dp = value.removeSuffix("dp").removeSuffix("dip").toFloat()
                        (dp * Resources.getSystem().displayMetrics.density).toInt()
                    }
                    value.endsWith("px") -> {
                        value.removeSuffix("px").toInt()
                    }
                    value.endsWith("in") -> {
                        val inches = value.removeSuffix("in").toFloat()
                        (inches * Resources.getSystem().displayMetrics.density * 160).toInt()
                    }
                    else -> {
                        value.toFloatOrNull()?.toInt() ?: default
                    }
                }
            } catch (e: Exception) {
                default
            }
        }

        private fun hasAttribute(parser: XmlResourceParser, attrName: String): Boolean {
            for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i) == attrName) {
                    return true
                }
            }
            return false
        }

        private fun getAttrValue(parser: XmlResourceParser, attrName: String): String? {
            for (i in 0 until parser.attributeCount) {
                if (parser.getAttributeName(i) == attrName) {
                    return parser.getAttributeValue(i)
                }
            }
            return null
        }


    }
}