package org.dark.keyboard.shared.xml

import org.dark.keyboard.shared.model.KeyModel
import org.dark.keyboard.shared.model.LayoutModel

/**
 * Simple XML parser for Android keyboard layouts using regex.
 * Multiplatform compatible (no Android dependencies).
 * 
 * Based on SimpleKeyboard.parseXml() logic.
 */
object SimpleXmlParser {
    
    fun parse(xml: String, screenWidth: Int = 1080, screenHeight: Int = 720): LayoutModel {
        // Parse keyboard attributes
        val keyboardAttrs = parseKeyboardAttrs(xml, screenWidth, screenHeight)
        
        // Parse all rows
        val rows = parseAllRows(xml, keyboardAttrs, screenWidth)
        
        // Calculate layout dimensions
        val totalHeight = if (rows.isNotEmpty()) {
            rows.last().y + rows.last().height + keyboardAttrs.verticalGap
        } else {
            screenHeight
        }
        
        return LayoutModel(
            name = extractLayoutName(xml),
            version = 1,
            width = screenWidth,
            height = totalHeight,
            keys = rows.flatMap { it.keys }
        )
    }
    
    private data class KeyboardAttrs(
        val defaultKeyWidth: Int,
        val defaultKeyHeight: Int,
        val horizontalGap: Int,
        val verticalGap: Int
    )
    
    private data class RowInfo(
        val keys: List<KeyModel>,
        val y: Int,
        val height: Int
    )
    
    private fun parseKeyboardAttrs(xml: String, screenWidth: Int, screenHeight: Int): KeyboardAttrs {
        val keyboardTag = """<Keyboard[^>]*>""".toRegex().find(xml)?.value ?: ""
        
        val keyWidth = extractAttr(keyboardTag, "keyWidth")
            ?.let { parseDimension(it, screenWidth) }
            ?: (screenWidth / 10)
        
        val keyHeight = extractAttr(keyboardTag, "keyHeight")
            ?.let { parseDimension(it, screenHeight) }
            ?: 160
        
        val hGap = extractAttr(keyboardTag, "horizontalGap")
            ?.let { parseDimension(it, screenWidth) }
            ?: 0
        
        val vGap = extractAttr(keyboardTag, "verticalGap")
            ?.let { parseDimension(it, screenHeight) }
            ?: 8
        
        return KeyboardAttrs(keyWidth, keyHeight, hGap, vGap)
    }
    
    private fun parseAllRows(xml: String, attrs: KeyboardAttrs, screenWidth: Int): List<RowInfo> {
        val rows = mutableListOf<RowInfo>()
        var currentY = 0
        
        // Find all <Row>...</Row> blocks (multiline)
        val rowRegex = """<Row([^>]*)>([\s\S]*?)</Row>""".toRegex()
        
        val rowMatches = rowRegex.findAll(xml)
        rowMatches.forEach { rowMatch ->
            val rowAttrs = rowMatch.groupValues[1]
            val rowContent = rowMatch.groupValues[2]
            
            // Skip rows with keyboardMode (alternate layouts)
            val hasKeyboardMode = rowAttrs.contains("keyboardMode")
            if (hasKeyboardMode) return@forEach
            
            val isExtension = rowAttrs.contains("extension=\"true\"")
            val rowEdgeFlags = parseRowEdgeFlags(rowAttrs)
            
            // Parse keys in this row
            val rowKeys = parseKeysInRow(rowContent, attrs, screenWidth, currentY, rowEdgeFlags)
            
            if (rowKeys.isNotEmpty()) {
                // Center row if it doesn't use full width
                val centeredKeys = centerRow(rowKeys, screenWidth)
                
                val rowHeight = rowKeys.firstOrNull()?.height ?: attrs.defaultKeyHeight
                rows.add(RowInfo(centeredKeys, currentY, rowHeight))
                currentY += rowHeight + attrs.verticalGap
            }
        }
        
        return rows
    }
    
    private fun parseKeysInRow(
        rowContent: String,
        attrs: KeyboardAttrs,
        screenWidth: Int,
        yPosition: Int,
        rowEdgeFlags: Int
    ): List<KeyModel> {
        val keys = mutableListOf<KeyModel>()
        var currentX = 0
        
        // Find all <Key ... /> tags (supports multiline attributes)
        val keyRegex = """<Key\s+([\s\S]+?)/>""".toRegex()
        val keyMatches = keyRegex.findAll(rowContent).toList()
        
        keyMatches.forEachIndexed { index, keyMatch ->
            val keyAttrs = keyMatch.groupValues[1]
            
            val label = extractAttr(keyAttrs, "keyLabel")
            val codeStr = extractAttr(keyAttrs, "codes")
            val shiftLabel = extractAttr(keyAttrs, "shiftLabel")
            val popupChars = extractAttr(keyAttrs, "popupCharacters")
            
            val keyWidth = extractAttr(keyAttrs, "keyWidth")
                ?.let { parseDimension(it, screenWidth) }
                ?: attrs.defaultKeyWidth
            
            val hGap = extractAttr(keyAttrs, "horizontalGap")
                ?.let { parseDimension(it, screenWidth) }
                ?: attrs.horizontalGap
            
            val code = parseKeyCode(codeStr, label)
            
            val isModifier = keyAttrs.contains("isModifier=\"true\"")
            val isSticky = keyAttrs.contains("isSticky=\"true\"")
            val isRepeatable = keyAttrs.contains("isRepeatable=\"true\"")
            
            var edgeFlags = parseKeyEdgeFlags(keyAttrs)
            if (index == 0) edgeFlags = edgeFlags or KeyModel.EDGE_LEFT
            if (index == keyMatches.size - 1) edgeFlags = edgeFlags or KeyModel.EDGE_RIGHT
            edgeFlags = edgeFlags or rowEdgeFlags
            
            keys.add(
                KeyModel(
                    label = label ?: "",
                    code = code,
                    x = currentX + hGap,
                    y = yPosition,
                    width = keyWidth - hGap,
                    height = attrs.defaultKeyHeight,
                    shiftLabel = shiftLabel,
                    popupKeys = popupChars?.map { it.toString() } ?: emptyList(),
                    isModifier = isModifier,
                    isSticky = isSticky,
                    repeatable = isRepeatable,
                    edgeFlags = edgeFlags
                )
            )
            
            currentX += keyWidth
        }
        
        return keys
    }
    
    private fun centerRow(keys: List<KeyModel>, screenWidth: Int): List<KeyModel> {
        if (keys.isEmpty()) return keys
        
        val totalWidth = keys.last().x + keys.last().width
        if (totalWidth >= screenWidth) return keys
        
        val offset = (screenWidth - totalWidth) / 2
        return keys.map { it.copy(x = it.x + offset) }
    }
    
    private fun parseKeyCode(codeStr: String?, label: String?): Int {
        if (codeStr != null) {
            // Handle @integer/key_* references
            return when {
                codeStr.startsWith("@integer/key_shift") -> KeyModel.CODE_SHIFT
                codeStr.startsWith("@integer/key_symbol") -> KeyModel.CODE_SYMBOL
                codeStr.startsWith("@integer/key_ctrl") -> KeyModel.CODE_CTRL
                codeStr.startsWith("@integer/key_alt") -> KeyModel.CODE_ALT
                codeStr.startsWith("@integer/key_fn") -> KeyModel.CODE_FN
                codeStr.startsWith("@integer/key_delete") -> KeyModel.CODE_DELETE
                codeStr.startsWith("@integer/key_return") -> KeyModel.CODE_ENTER
                codeStr.startsWith("@integer/key_space") -> KeyModel.CODE_SPACE
                codeStr.startsWith("@integer/key_tab") -> KeyModel.CODE_TAB
                else -> codeStr.toIntOrNull() ?: 0
            }
        }
        
        // Infer from label
        return label?.firstOrNull()?.code ?: 32
    }
    
    private fun parseDimension(value: String, referenceWidth: Int): Int {
        return when {
            value.endsWith("%p") -> {
                val percent = value.removeSuffix("%p").toFloatOrNull() ?: 10f
                (referenceWidth * percent / 100f).toInt()
            }
            value.endsWith("px") -> value.removeSuffix("px").toIntOrNull() ?: 0
            value.endsWith("dp") || value.endsWith("dip") -> {
                // Assume 1dp = 1px for web (no density)
                value.removeSuffix("dp").removeSuffix("dip").toIntOrNull() ?: 0
            }
            value.startsWith("@dimen/") -> 8 // Default gap
            else -> value.toIntOrNull() ?: 0
        }
    }
    
    private fun parseRowEdgeFlags(rowAttrs: String): Int {
        var flags = 0
        if (rowAttrs.contains("rowEdgeFlags=\"top\"")) flags = flags or KeyModel.EDGE_TOP
        if (rowAttrs.contains("rowEdgeFlags=\"bottom\"")) flags = flags or KeyModel.EDGE_BOTTOM
        return flags
    }
    
    private fun parseKeyEdgeFlags(keyAttrs: String): Int {
        val edgeFlagsAttr = extractAttr(keyAttrs, "keyEdgeFlags") ?: return 0
        
        var flags = 0
        if (edgeFlagsAttr.contains("left")) flags = flags or KeyModel.EDGE_LEFT
        if (edgeFlagsAttr.contains("right")) flags = flags or KeyModel.EDGE_RIGHT
        if (edgeFlagsAttr.contains("top")) flags = flags or KeyModel.EDGE_TOP
        if (edgeFlagsAttr.contains("bottom")) flags = flags or KeyModel.EDGE_BOTTOM
        
        return flags
    }
    
    private fun extractAttr(tag: String, attrName: String): String? {
        val patterns = listOf(
            """$attrName="([^"]+)"""",
            """android:$attrName="([^"]+)""""
        )
        
        for (pattern in patterns) {
            val match = pattern.toRegex().find(tag)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        
        return null
    }
    
    private fun extractLayoutName(xml: String): String {
        // Try to extract from first comment
        val commentRegex = """<!--\s*([^\n]+)\s*-->""".toRegex()
        val comment = commentRegex.find(xml)?.groupValues?.get(1)?.trim()
        
        return when {
            comment != null && !comment.startsWith("Copyright") && !comment.contains("License") -> comment
            else -> "Custom Layout"
        }
    }
}
