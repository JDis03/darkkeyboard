package org.dark.keyboard

import android.content.Context
import android.content.res.Resources
import timber.log.Timber
import android.util.TypedValue
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

class SimpleKeyboard(
    val rows: List<KeyboardRow>,
    val totalWidth: Int,
    val totalHeight: Int
) {
    val allKeys: List<Key> get() = rows.flatMap { it.keys }

    companion object {


        fun fromXml(
            context: Context,
            xmlResId: Int,
            screenWidth: Int,
            screenHeight: Int,
            showExtensionRow: Boolean = true
        ): SimpleKeyboard {
            val parser = context.resources.getXml(xmlResId)
            return parseXml(context, parser, screenWidth, screenHeight, showExtensionRow)
        }

        fun fromXml(
            context: Context,
            inputStream: InputStream,
            screenWidth: Int,
            screenHeight: Int,
            showExtensionRow: Boolean = true
        ): SimpleKeyboard {
            val xmlString = inputStream.bufferedReader().readText()
            Timber.d("Parsing custom layout XML (${xmlString.length} chars):\n${xmlString.take(300)}")
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(xmlString.reader())
            return parseXml(context, parser, screenWidth, screenHeight, showExtensionRow)
        }

        private fun parseXml(
            context: Context,
            parser: XmlPullParser,
            screenWidth: Int,
            screenHeight: Int,
            showExtensionRow: Boolean
        ): SimpleKeyboard {
            val density = context.resources.displayMetrics.density
            
            // Fórmula original de HeliBoard, pero reducida para dejar espacio para botones
            val defaultHeightDp = 205.6f * 1.33f
            val defaultHeightPx = defaultHeightDp * density
            // Reducir de 46% a 38% para dejar ~8% para botones del sistema
            val maxHeight = screenHeight * 0.38f
            val keyboardHeight = min(defaultHeightPx, maxHeight).toInt()
            
            Timber.d("Keyboard height: $keyboardHeight px (${keyboardHeight / density} dp), screen: $screenHeight px")
            val verticalGapPx = (0.5f * density).toInt()
            val horizontalGapPx = (0.5f * density).toInt()

            // Distribución: 5 filas + 4 gaps
            // NO restar gaps del total - las filas usan su altura completa menos el gap individual
            val availableHeightForRows = keyboardHeight
            
            // Distribución de altura disponible: 16%, 21%, 21%, 21%, 21%
            // Todas las filas DEBEN sumar 100% o menos para caber en keyboardHeight
            // Bottom row igual que middle rows - la mejora viene de mejor hit detection
            val numberRowHeight = (availableHeightForRows * 0.16f).toInt()
            val rowHeight = (availableHeightForRows * 0.21f).toInt()  
            val bottomRowHeight = (availableHeightForRows * 0.21f).toInt()
            
            Timber.d("Height calc: keyboard=$keyboardHeight, available=$availableHeightForRows")
            Timber.d("Row heights: number=$numberRowHeight, normal=$rowHeight, bottom=$bottomRowHeight")
            val defaultKeyWidth = screenWidth / 10

            val rows = mutableListOf<KeyboardRow>()
            var currentRow: KeyboardRow? = null
            var currentX = 0
            var currentY = 0
            var rowCount = 0
            var hasBottomRowWithMode = false  // Flag para solo tomar 1 row bottom con keyboardMode
            var keyboardDefaultWidth = defaultKeyWidth
            var keyboardHorizontalGap = horizontalGapPx
            var keyboardKeyHeight = rowHeight
            var keyboardVerticalGap = verticalGapPx

            try {
                var eventType = parser.eventType
                var tagCount = 0
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "Row") {
                        tagCount++
                        Timber.d("Parser saw Row START_TAG #$tagCount")
                    }
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "Keyboard" -> {
                                    for (i in 0 until parser.attributeCount) {
                                        when (parser.attrName(i)) {
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
                                                val gapFromXml = parseDimension(
                                                    parser.getAttributeValue(i), screenHeight, 0
                                                )
                                                keyboardVerticalGap = max(keyboardVerticalGap, gapFromXml)
                                            }
                                        }
                                    }
                                }
                                "Row" -> {
                                    val hasKeyboardMode = getAttrValue(parser, "keyboardMode") != null
                                    val isExtension = getAttrValue(parser, "extension").toBoolean(default = false)
                                    
                                     Timber.d("ROW START: rowCount=$rowCount, hasKeyboardMode=$hasKeyboardMode")
                                    
                                    val rowKeys = mutableListOf<Key>()
                                    
                                    // Skip rows con keyboardMode - son alternativas que Android debería filtrar
                                    // Pero nuestro parser simple no soporta modes, entonces skip
                                    if (hasKeyboardMode) {
                                         Timber.d("  -> WILL SKIP this row (has keyboardMode)")
                                        currentRow = null
                                    } else if (isExtension && !showExtensionRow) {
                                         Timber.d("  -> WILL SKIP extension row (user preference)")
                                        currentRow = null
                                    } else if (rowCount >= 6) {
                                         Timber.d("  -> WILL SKIP: already have 6 rows")
                                        currentRow = null
                                    } else {
                                         Timber.d("  -> WILL CREATE row")
                                        currentRow = KeyboardRow(
                                            keys = rowKeys,
                                            isExtension = isExtension,
                                            keyboardMode = 0,
                                            verticalGap = keyboardVerticalGap,
                                            horizontalGap = keyboardHorizontalGap
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

                                        val codeStr = getAttrValue(parser, "codes")
                                        var codeFromXml = when {
                                            codeStr != null -> codeStr.toIntOrNull() ?: Int.MIN_VALUE
                                            else -> Int.MIN_VALUE
                                        }
                                        if (codeFromXml == Int.MIN_VALUE && codeStr != null && codeStr.startsWith("@")) {
                                            val a = context.obtainStyledAttributes(
                                                Xml.asAttributeSet(parser), R.styleable.Keyboard_Key)
                                            codeFromXml = a.getInt(R.styleable.Keyboard_Key_codes, Int.MIN_VALUE)
                                            a.recycle()
                                        }

                                        val labelAttr = getAttrValue(parser, "keyLabel")
                                        val shiftLabelAttr = getAttrValue(parser, "shiftLabel")
                                        val outputTextAttr = getAttrValue(parser, "keyOutputText")
                                        val popupCharsAttr = getAttrValue(parser, "popupCharacters")

                                        val isModifier = getAttrValue(parser, "isModifier").toBoolean(default = false)
                                        val isSticky = getAttrValue(parser, "isSticky").toBoolean(default = false)
                                        val isRepeatable = getAttrValue(parser, "isRepeatable").toBoolean(default = false)
                                        val edgeFlags = getAttrValue(parser, "keyEdgeFlags")?.toIntOrNull() ?: 0

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
                                             Timber.d("Parsed special key: label=$labelAttr code=$code")
                                        }

                                        val key = Key(
                                            label = labelAttr,
                                            code = code,
                                            shiftLabel = shiftLabelAttr,
                                            popupCharacters = popupCharsAttr,
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
                                         Timber.d("Added key: label=$labelAttr, code=$code, x=${key.x}, width=${key.width}")
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "Row" -> {
                                    currentRow?.let { row ->
                                         Timber.d("Closing Row: keyboardMode=${row.keyboardMode}, keys=${row.keys.size}, rowCount=$rowCount")
                                        if (row.keyboardMode == -1 || row.keyboardMode == 0) {
                                            // Centrar row si no usa todo el ancho
                                            val totalRowWidth = row.keys.sumOf { it.width + keyboardHorizontalGap }
                                            if (totalRowWidth < screenWidth) {
                                                val offset = (screenWidth - totalRowWidth) / 2
                                                 Timber.d("  -> Centering row: totalWidth=$totalRowWidth, offset=$offset")
                                                row.keys.forEach { key ->
                                                    key.x += offset
                                                }
                                            }
                                            
                                            rows.add(row)
                                            currentY += row.defaultKeyHeight + keyboardVerticalGap
                                            rowCount++
                                             Timber.d("  -> Added row #$rowCount with ${row.keys.size} keys")
                                        } else {
                                             Timber.d("  -> SKIPPED row (keyboardMode=${row.keyboardMode})")
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
                Timber.e(e, "Error parsing keyboard XML")
            }

            val actualHeight = if (rows.isNotEmpty()) {
                val lastRow = rows.last()
                lastRow.y + lastRow.defaultKeyHeight + keyboardVerticalGap
            } else {
                keyboardHeight
            }

            val totalKeys = rows.sumOf { it.keys.size }
            Timber.e("=== PARSED: ${rows.size} rows, $totalKeys keys, height=$actualHeight, width=$screenWidth ===")
            if (rows.isNotEmpty()) {
                rows.forEachIndexed { i, r ->
                    Timber.e("  Row[$i]: ${r.keys.size} keys, y=${r.y}, height=${r.defaultKeyHeight}, isExtension=${r.isExtension}")
                    r.keys.take(3).forEachIndexed { j, k ->
                        Timber.e("    Key[$j]: label='${k.label}' code=${k.code} x=${k.x} y=${k.y} w=${k.width} h=${k.height}")
                    }
                }
            }

            return SimpleKeyboard(rows, screenWidth, actualHeight)
        }

        private fun String?.toBoolean(default: Boolean): Boolean = when (this?.lowercase()) {
            "true" -> true
            "false" -> false
            else -> default
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

        private fun hasAttribute(parser: XmlPullParser, attrName: String): Boolean {
            for (i in 0 until parser.attributeCount) {
                if (parser.attrName(i) == attrName) {
                    return true
                }
            }
            return false
        }

        private const val ANDROID_NS = "http://schemas.android.com/apk/res-auto"

        private fun XmlPullParser.attrName(index: Int): String =
            getAttributeName(index).substringAfterLast(':')

        private fun getAttrValue(parser: XmlPullParser, attrName: String): String? {
            for (i in 0 until parser.attributeCount) {
                if (parser.attrName(i) == attrName) return parser.getAttributeValue(i)
            }
            return null
        }


    }
}