# Lecciones Aprendidas - Parser XML de Teclados Android

> **Proyecto:** DarkKeyboard  
> **Fecha:** 2026-05-23  
> **Propósito:** Evitar repetir los mismos errores al crear/modificar layouts XML y al generar XML desde el web editor.

---

## 1. Namespace Correcto (CRÍTICO)

**PROBLEMA:**
```xml
<!-- ❌ INCORRECTO - todos los atributos custom devuelven NULL -->
<Keyboard xmlns:android="http://schemas.android.com/apk/res/android">
```

Con `res/android`, el parser de Android **NO reconoce atributos custom** (`keyLabel`, `isModifier`, `extension`, `keyboardMode`). Resultado: teclas vacías, parser roto.

**SOLUCIÓN:**
```xml
<!-- ✅ CORRECTO -->
<Keyboard xmlns:android="http://schemas.android.com/apk/res-auto">
```

El namespace `res-auto` es necesario para atributos definidos en `attrs-keyboard.xml`.

---

## 2. Usar TypedArray para Leer Atributos

**PROBLEMA:**
```kotlin
// ❌ INCORRECTO - devuelve null para resource references
val keyboardMode = parser.getAttributeValue(null, "keyboardMode")
val keyboardMode = parser.getAttributeValue("http://...", "keyboardMode")
```

Cuando el atributo es una referencia como `@+id/mode_symbols`, `getAttributeValue()` devuelve `null`.

**SOLUCIÓN:**
```kotlin
// ✅ CORRECTO - usa TypedArray
val rowAttrs = context.obtainStyledAttributes(
    Xml.asAttributeSet(parser),
    R.styleable.Keyboard_Row
)

val keyboardModeId = rowAttrs.getResourceId(R.styleable.Keyboard_Row_keyboardMode, 0)
val isExtension = rowAttrs.getBoolean(R.styleable.Keyboard_Row_extension, false)
val hasKeyboardMode = keyboardModeId != 0

rowAttrs.recycle() // ¡Importante! Siempre recycle()
```

**Por qué funciona:** `TypedArray` resuelve referencias automáticamente. `getResourceId()` devuelve el ID del resource, no null. `getBoolean()` devuelve el valor correcto del atributo custom.

**Referencia:** `app/src/main/java/org/dark/keyboard/SimpleKeyboard.kt` línea 100-112

---

## 3. Declarar Atributos Custom en attrs-keyboard.xml

Todos los atributos custom DEBEN estar declarados en `res/values/attrs-keyboard.xml`:

```xml
<declare-styleable name="Keyboard_Row">
    <attr name="keyboardMode" format="reference" />
    <attr name="extension" format="boolean" />
    <attr name="rowEdgeFlags">
        <flag name="top" value="4" />
        <flag name="bottom" value="8" />
    </attr>
</declare-styleable>

<declare-styleable name="Keyboard_Key">
    <attr name="keyLabel" format="string" />
    <attr name="codes" format="integer" />
    <attr name="keyWidth" format="dimension|fraction" />
    <attr name="isModifier" format="boolean" />
    <attr name="isSticky" format="boolean" />
    <attr name="isRepeatable" format="boolean" />
</declare-styleable>
```

**Sin estas declaraciones, TypedArray NO puede leer los atributos.**

---

## 4. Skip Rows con keyboardMode

**PROBLEMA:** HackersKeyboard usa múltiples rows con `keyboardMode` diferentes para layouts alternativos (con/sin botón settings). Mostrar todas causa duplicados.

**SOLUCIÓN:**
```kotlin
// Al parsear Row START_TAG:
val hasKeyboardMode = keyboardModeId != 0

if (hasKeyboardMode) {
    Log.d(TAG, "WILL SKIP this row (has keyboardMode)")
    currentRow = null  // Skip esta row completamente
} else {
    currentRow = KeyboardRow(...)
}

// Al parsear Row END_TAG:
currentRow?.let { row ->
    rows.add(row)
    rowCount++
}
```

**Referencia:** `SimpleKeyboard.kt` línea 117-129, 220-229

---

## 5. getDisplayLabel() con Fallback

**PROBLEMA:** Teclas pueden tener solo `keyLabel`, solo `codes`, o ambos. Si solo tienen `codes`, necesitamos convertir el code a char.

**SOLUCIÓN:**
```kotlin
private fun getDisplayLabel(key: Key): String? {
    // 1. Intentar usar label si existe
    val label = key.label
    if (label != null && label.isNotEmpty()) return label

    // 2. Mapear codes especiales a símbolos
    return when (key.code) {
        Key.CODE_SHIFT -> "⇧"
        Key.CODE_DELETE -> "⌫"
        Key.CODE_ENTER -> "↵"
        Key.CODE_SPACE -> " "
        Key.CODE_MODE_CHANGE -> "?123"
        Key.CODE_CTRL_LEFT -> "Ctrl"
        Key.CODE_TAB -> "Tab"
        Key.CODE_F1 -> "F1"
        else -> {
            // 3. FALLBACK: convertir code ASCII a char
            if (key.code > 0 && key.code < 128) {
                key.code.toChar().toString()
            } else {
                null  // No se puede mostrar
            }
        }
    }
}
```

**Referencia:** `SimpleKeyboardView.kt` línea 405-446

---

## 6. Parsear Codes Correctamente

```kotlin
val codeFromXml = a.getInt(R.styleable.Keyboard_Key_codes, Int.MIN_VALUE)

var code: Int
if (codeFromXml != Int.MIN_VALUE) {
    code = codeFromXml  // Usar code del XML
} else if (labelAttr != null && labelAttr.length == 1) {
    code = labelAttr[0].code  // Usar ASCII del character
} else {
    code = 0  // Sin code
}
```

**Referencia:** `SimpleKeyboard.kt` línea 168, 182-189

---

## 7. Web Editor: Códigos Numéricos vs @integer/key_* (CRÍTICO)

**PROBLEMA:** Custom Layout carga el XML como **texto plano** (InputStream), no como recurso Android. Las referencias `@integer/key_shift` NO se resuelven en texto plano.

```xml
<!-- ❌ No funciona en Custom Layout -->
<Key android:codes="@integer/key_shift" android:keyLabel="⇧" />
```

**SOLUCIÓN — usar códigos numéricos directos:**
```xml
<!-- ✅ Funciona en Custom Layout -->
<Key android:codes="-1" android:keyLabel="⇧" android:isModifier="true" />
<Key android:codes="-5" android:keyLabel="⌫" android:isModifier="true" />
<Key android:codes="-113" android:keyLabel="Ctrl" android:isModifier="true" />
<Key android:codes="-2" android:keyLabel="?123" />
<Key android:codes="32" android:keyLabel=" " />
<Key android:codes="10" android:keyLabel="↵" />
```

**Códigos de referencia (Key.kt):**

| Key | Code | Label sugerido |
|-----|------|----------------|
| Shift | -1 | ⇧ |
| Delete | -5 | ⌫ |
| Ctrl | -113 | Ctrl |
| Symbol/Mode Change | -2 | ?123 |
| Space | 32 | (espacio) |
| Enter | 10 | ↵ |
| Tab | 9 | Tab |
| Fn | -119 | Fn |
| Alt | -57 | Alt |

**Referencia:** `app/src/main/java/org/dark/keyboard/Key.kt`

---

## 8. Web Editor: Row Grouping con Tolerancia

**PROBLEMA:** Las teclas en el preset tienen valores Y calculados con `keyHeight * rowIndex`. Por redondeo de `Int`, teclas de la misma fila pueden tener valores Y ligeramente diferentes (1-2px). El `groupBy { it.y }` las separa en filas diferentes.

```kotlin
// ❌ INCORRECTO - filas separadas por 1px de diferencia
keys.groupBy { it.y }
```

**SOLUCIÓN — agrupar con tolerancia de 5px:**
```kotlin
// ✅ CORRECTO
val tolerance = 5
val grouped = mutableListOf<MutableList<KeyModel>>()

keys.sortedBy { it.y }.forEach { key ->
    val existingRow = grouped.find { row ->
        val firstKey = row.firstOrNull()
        firstKey != null && abs(firstKey.y - key.y) <= tolerance
    }
    
    if (existingRow != null) {
        existingRow.add(key)
    } else {
        grouped.add(mutableListOf(key))
    }
}
```

**Referencia:** `shared/src/commonMain/kotlin/org/dark/keyboard/shared/xml/XmlLayoutGenerator.kt`

---

## 9. Web Editor: Escape XML

**PROBLEMA:** Caracteres especiales en popupCharacters (`&`, `@`, `#`) rompen el XML si no se escapan.

```xml
<!-- ❌ XML inválido - & rompe el parser -->
<Key android:keyLabel="7" android:popupCharacters="{&" />

<!-- ❌ @ y # necesitan escape en XML -->
<Key android:keyLabel="2" android:popupCharacters="@~" />
```

**SOLUCIÓN:**
```xml
<!-- ✅ XML válido -->
<Key android:keyLabel="7" android:popupCharacters="{&amp;" />
<Key android:keyLabel="2" android:popupCharacters="\@~" />
<Key android:keyLabel="3" android:popupCharacters="\#-" />
```

**Código del escape:**
```kotlin
private fun escapeXml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("@", "\\@")
        .replace("#", "\\#")
}
```

**Referencia:** `shared/src/commonMain/kotlin/org/dark/keyboard/shared/xml/XmlLayoutGenerator.kt`

---

## 10. Web Editor: isModifier en Delete

**PROBLEMA:** El generador no marcaba Delete como `isModifier="true"`, pero el parser de Android y el render lo requieren.

```xml
<!-- ❌ Delete sin isModifier -->
<Key android:codes="-5" android:keyLabel="⌫" android:isRepeatable="true" />

<!-- ✅ Delete con isModifier -->
<Key android:codes="-5" android:keyLabel="⌫" android:isModifier="true" android:isRepeatable="true" />
```

**Solución en el generador:**
```kotlin
val shouldBeModifier = key.isModifier || key.code == KeyModel.CODE_DELETE
if (shouldBeModifier) {
    sb.append(" android:isModifier=\"true\"")
}
```

---

## 11. Web Editor: Formato de Porcentajes

**PROBLEMA:** Porcentajes como `11.9%p` en vez de `12%p` por errores de redondeo en la conversión pixels→porcentaje.

**SOLUCIÓN — redondear al entero más cercano si la diferencia es < 0.1%:**
```kotlin
private fun formatPercentage(pixels: Int, screenWidth: Int): String {
    val percentage = (pixels.toFloat() / screenWidth * 100)
    val rounded = round(percentage)
    if (abs(percentage - rounded) < 0.1f) {
        return "${rounded.toInt()}%p"
    }
    val oneDecimal = (percentage * 10).toInt() / 10.0
    return "$oneDecimal%p"
}
```

---

## 12. Web Editor: Space Key Code

**PROBLEMA:** El generador omitía el `android:codes` para Space porque `label=" "` y `code=32` coinciden. Pero el parser de Android necesita el code explícito para reconocer `CODE_SPACE`.

```kotlin
// ❌ INCORRECTO - Space sin code
private fun shouldIncludeCode(key: KeyModel): Boolean {
    val labelChar = key.label.firstOrNull()
    if (labelChar != null && labelChar.code == key.code) {
        return false  // " " tiene code 32 → omite el code
    }
    return true
}
```

**SOLUCIÓN — siempre incluir code para Space, Enter, Tab:**
```kotlin
private fun shouldIncludeCode(key: KeyModel): Boolean {
    if (key.isModifier || key.code < 0) return true
    if (key.code == 32 || key.code == 10 || key.code == 9) return true // Space/Enter/Tab
    // ... resto de la lógica
}
```

---

## 13. Crash: suggestionEngine en onDestroy

**PROBLEMA:** `suggestionEngine` es `lateinit` y puede no estar inicializado al llamar `onDestroy()`.

```kotlin
// ❌ Crash si suggestionEngine no se inicializó
override fun onDestroy() {
    (suggestionEngine as? DictSuggestionEngine)?.savePersistedData()
    suggestionEngine.close()  // BOOM: UninitializedPropertyAccessException
    super.onDestroy()
}
```

**SOLUCIÓN — verificar inicialización:**
```kotlin
// ✅ Verificar antes de acceder
override fun onDestroy() {
    if (::suggestionEngine.isInitialized) {
        (suggestionEngine as? DictSuggestionEngine)?.savePersistedData()
        suggestionEngine.close()
    }
    prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    imeScope.cancel()
    super.onDestroy()
}
```

**Referencia:** `app/src/main/java/org/dark/keyboard/DarkIME2.kt` línea 947-956

---

## Checklist para Crear Nuevo Layout XML

- [ ] **1. Namespace correcto:** `xmlns:android="http://schemas.android.com/apk/res-auto"`
- [ ] **2. Atributos en attrs-keyboard.xml** (si son nuevos)
- [ ] **3. NO usar keyboardMode** (se skipea)
- [ ] **4. Códigos numéricos, no @integer** (para Custom Layout)
- [ ] **5. Escapar XML:** `&amp;` `&lt;` `&gt;` `&quot;` `\@` `\#`
- [ ] **6. isModifier="true"** en: Shift, Delete, Ctrl, Symbol, Space, Enter
- [ ] **7. Marcar row de números como extension**
- [ ] **8. Marcar row bottom con rowEdgeFlags="bottom"**
- [ ] **9. Porcentajes que sumen ≤ 100%**

---

## Debugging Tips

### Ver atributos que detecta el parser:
```kotlin
val attrCount = parser.attributeCount
for (i in 0 until attrCount) {
    Log.d(TAG, "attr[$i]: ${parser.getAttributeName(i)} = ${parser.getAttributeValue(i)}")
}
```

### Ver cuántas rows parsea:
```kotlin
if (eventType == XmlResourceParser.START_TAG && parser.name == "Row") {
    tagCount++
    Log.d(TAG, "Parser saw Row START_TAG #$tagCount")
}
```

### Ver qué keys se agregan:
```kotlin
Log.d(TAG, "Added key: label=$labelAttr, code=$code, x=${key.x}, width=${key.width}")
```

---

## Errores Comunes y Soluciones

| Error | Causa | Solución |
|-------|-------|----------|
| Teclas vacías (filas sin contenido) | Namespace incorrecto | Usar `res-auto` |
| Filas duplicadas | Rows con keyboardMode no skipeadas | Skip rows con `keyboardModeId != 0` |
| `attribute not found` en build | Atributo no en attrs-keyboard.xml | Agregar a declare-styleable |
| `code=0` para todas las keys | TypedArray no usado | Usar `obtainStyledAttributes()` |
| Custom Layout: teclas no aparecen | `@integer/key_*` no resuelve | Usar códigos numéricos (`-1`, `-5`, etc.) |
| Custom Layout: XML roto | `&` sin escapar | Usar `&amp;` |
| Web editor: filas separadas | groupBy exacto por Y | Tolerancia de 5px |
| Web editor: `11.9%p` en vez de `12%p` | Error de redondeo | Redondear al entero si diff < 0.1% |
| Crash al cerrar IME | lateinit no inicializado | `::prop.isInitialized` antes de acceder |

---

## Archivos de Referencia

| Archivo | Rol |
|---------|-----|
| `app/src/main/res/xml/kbd_pc.xml` | Layout alfabético completo (funcional, original) |
| `app/src/main/res/xml/kbd_symbols_simple.xml` | Layout símbolos simple (funcional) |
| `app/src/main/java/org/dark/keyboard/SimpleKeyboard.kt` | Parser con TypedArray |
| `app/src/main/java/org/dark/keyboard/SimpleKeyboardView.kt` | Renderer (onDraw, getDisplayLabel, drawIcon) |
| `app/src/main/java/org/dark/keyboard/Key.kt` | Key model + code constants |
| `app/src/main/res/values/attrs-keyboard.xml` | Declaración de atributos custom |
| `app/src/main/java/org/dark/keyboard/DarkIME2.kt` | IME Service (reloadKeyboard, onDestroy) |
| `shared/.../xml/XmlLayoutGenerator.kt` | Generador XML (web editor) |
| `shared/.../xml/SimpleXmlParser.kt` | Parser XML multiplatform (web editor) |
| `shared/.../presets/PresetLayouts.kt` | Presets (QWERTY Standard) |
| `shared/.../model/KeyModel.kt` | Key model + code constants (shared) |

---

## Comandos Útiles

```bash
# Compilar web editor
./gradlew :web-editor:compileKotlinJs

# Compilar shared
./gradlew :shared:compileKotlinJvm

# Build APK Android
./gradlew :app:assembleDebug

# Ver logs del parser
adb logcat -c && adb logcat | grep "SimpleKeyboard"

# Ver keys parseadas
adb logcat -d | grep "SimpleKeyboard" | grep "Added key"

# Verificar si hay errores
adb logcat -d | grep -E "Error|Exception|FATAL"
```
