package org.dark.keyboard

import android.content.Context
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.util.Log
import android.util.TypedValue
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
            screenHeight: Int
        ): SimpleKeyboard {
            val density = context.resources.displayMetrics.density
            val defaultHeightDp = 205.6f * 1.33f
            val defaultHeightPx = defaultHeightDp * density
            val maxHeight = screenHeight * 0.46f
            val keyboardHeight = min(defaultHeightPx, maxHeight).toInt()
            val verticalGapPx = (1.5f * density).toInt()

            val numberRowHeight = (keyboardHeight * 0.17f).toInt()
            val rowHeight = (keyboardHeight * 0.22f).toInt()
            val bottomRowHeight = (keyboardHeight * 0.19f).toInt()
            val defaultKeyWidth = screenWidth / 10

            val parser = context.resources.getXml(xmlResId)
            val rows = mutableListOf<KeyboardRow>()
            var currentRow: KeyboardRow? = null
            var currentX = 0
            var currentY = 0
            var rowCount = 0
            var keyboardDefaultWidth = defaultKeyWidth
            var keyboardHorizontalGap = 0
            var keyboardKeyHeight = rowHeight
            var keyboardVerticalGap = verticalGapPx

            try {
                var eventType = parser.eventType
                while (eventType != XmlResourceParser.END_DOCUMENT) {
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
                                    val isExtension = parser.getAttributeBooleanValue(
                                        null, "extension", false
                                    )
                                    // IGNORAR keyboardMode completamente - solo tomar primeras 5 filas
                                    val hasKeyboardMode = hasAttribute(parser, "keyboardMode")
                                    val rowKeys = mutableListOf<Key>()
                                    
                                    // Si ya tenemos 5 filas, ignorar el resto
                                    if (rowCount >= 5) {
                                        currentRow = null
                                    } else if (hasKeyboardMode && rowCount >= 4) {
                                        // Si es la 5ta fila y tiene keyboardMode, tomar solo la primera
                                        if (currentRow == null) {
                                            currentRow = KeyboardRow(
                                                keys = rowKeys,
                                                isExtension = isExtension,
                                                keyboardMode = 0
                                            )
                                            currentX = 0
                                            currentRow!!.defaultKeyHeight = bottomRowHeight
                                            currentRow!!.defaultKeyWidth = keyboardDefaultWidth
                                            currentRow!!.y = currentY
                                        } else {
                                            currentRow = null // Ignorar filas bottom adicionales
                                        }
                                    } else {
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
                                        val codesAttr = getAttrValue(parser, "codes")
                                        val labelAttr = getAttrValue(parser, "keyLabel")
                                        val shiftLabelAttr = getAttrValue(parser, "shiftLabel")
                                        val outputTextAttr = getAttrValue(parser, "keyOutputText")
                                        val isModifier = parser.getAttributeBooleanValue(null, "isModifier", false)
                                        val isSticky = parser.getAttributeBooleanValue(null, "isSticky", false)
                                        val isRepeatable = parser.getAttributeBooleanValue(null, "isRepeatable", false)
                                        val edgeFlags = parser.getAttributeIntValue(null, "keyEdgeFlags", 0)

                                        var code: Int
                                        if (codesAttr != null) {
                                            code = resolveKeyCode(context, codesAttr)
                                        } else if (labelAttr != null && labelAttr.length == 1) {
                                            code = labelAttr[0].code
                                        } else {
                                            code = 0
                                        }
                                        if (outputTextAttr != null && code == 0) {
                                            code = 0
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
                                    }
                                }
                            }
                        }
                        XmlResourceParser.END_TAG -> {
                            when (parser.name) {
                                "Row" -> {
                                    currentRow?.let { row ->
                                        if (row.keyboardMode == -1 || row.keyboardMode == 0) {
                                            rows.add(row)
                                            currentY += row.defaultKeyHeight + keyboardVerticalGap
                                            rowCount++
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

        private fun resolveKeyCode(context: Context, codeStr: String): Int {
            return try {
                codeStr.toInt()
            } catch (e: NumberFormatException) {
                if (codeStr.startsWith("@")) {
                    try {
                        val resId = codeStr.substring(1).toInt()
                        context.resources.getInteger(resId)
                    } catch (ex: Exception) {
                        0
                    }
                } else {
                    0
                }
            }
        }
    }
}