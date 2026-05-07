# Lecciones Aprendidas - Parser XML de Teclados Android

## Problema General

Al crear un teclado Android (IME) que parsea layouts XML personalizados, encontramos múltiples problemas que causaban:
- Filas duplicadas o faltantes
- Teclas vacías (sin labels)
- Atributos no parseados correctamente
- Layouts que funcionaban en un archivo pero no en otro

## Soluciones Críticas

### 1. ⚠️ NAMESPACE CORRECTO (CRÍTICO)

**PROBLEMA:**
```xml
<!-- ❌ INCORRECTO -->
<Keyboard xmlns:android="http://schemas.android.com/apk/res/android">
```

Con este namespace, **TODOS los atributos custom devuelven NULL**:
- `android:keyLabel` → null
- `android:extension` → no reconocido
- `android:keyboardMode` → null
- Resultado: teclas vacías, parser roto

**SOLUCIÓN:**
```xml
<!-- ✅ CORRECTO -->
<Keyboard xmlns:android="http://schemas.android.com/apk/res-auto">
```

El namespace `res-auto` es necesario para atributos custom definidos en `attrs-keyboard.xml`.

**Referencia:** kbd_pc.xml línea 3

---

### 2. ⚠️ USAR TypedArray PARA LEER ATRIBUTOS

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

**Referencia:** SimpleKeyboard.kt línea 100-112

**Por qué funciona:**
- `TypedArray` resuelve referencias automáticamente
- `getResourceId()` devuelve el ID del resource, no null
- `getBoolean()` devuelve el valor correcto del atributo custom

---

### 3. ⚠️ DECLARAR ATRIBUTOS CUSTOM EN attrs-keyboard.xml

**REQUISITO:**
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
    <!-- etc -->
</declare-styleable>
```

**Sin estas declaraciones, TypedArray NO puede leer los atributos.**

**Referencia:** app/src/main/res/values/attrs-keyboard.xml

---

### 4. ⚠️ SKIP ROWS CON keyboardMode

**PROBLEMA:**
HackersKeyboard usa múltiples rows con `keyboardMode` diferentes para mostrar layouts alternativos (con/sin botón settings). Nuestro parser simple NO soporta modes, entonces mostrar todas causa duplicados.

**SOLUCIÓN:**
```kotlin
// Al parsear Row START_TAG:
val hasKeyboardMode = keyboardModeId != 0

if (hasKeyboardMode) {
    Log.d(TAG, "  -> WILL SKIP this row (has keyboardMode)")
    currentRow = null  // Skip esta row completamente
} else {
    // Crear row normalmente
    currentRow = KeyboardRow(...)
}
```

**Al parsear Row END_TAG:**
```kotlin
currentRow?.let { row ->
    // Solo agregar si currentRow NO es null (no fue skipeada)
    rows.add(row)
    rowCount++
}
```

**Referencia:** SimpleKeyboard.kt línea 117-129, 220-229

---

### 5. ⚠️ getDisplayLabel() CON FALLBACK

**PROBLEMA:**
Teclas pueden tener:
- Solo `keyLabel` (ejemplo: `android:keyLabel="@"`)
- Solo `codes` (ejemplo: `android:codes="64"`)
- Ambos

Si solo tienen `codes`, necesitamos convertir el code a char para mostrar.

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
        // etc...
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

**Referencia:** SimpleKeyboardView.kt línea 184-210

---

### 6. ⚠️ PARSEAR CODES CORRECTAMENTE

**Para teclas con label simple:**
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

**Referencia:** SimpleKeyboard.kt línea 168, 182-189

---

## Checklist para Crear Nuevo Layout XML

✅ **1. Usar namespace correcto:**
```xml
<Keyboard xmlns:android="http://schemas.android.com/apk/res-auto">
```

✅ **2. Declarar atributos en attrs-keyboard.xml** (si son nuevos)

✅ **3. NO usar keyboardMode** (a menos que quieras que se skipee)

✅ **4. Para teclas con símbolos:**
```xml
<!-- Opción A: Solo label (recomendado) -->
<Key android:keyLabel="@" />

<!-- Opción B: Label + code explícito -->
<Key android:codes="64" android:keyLabel="@" />
```

✅ **5. Escapar caracteres especiales XML:**
```xml
<Key android:keyLabel="&amp;" />  <!-- & -->
<Key android:keyLabel="&lt;" />   <!-- < -->
<Key android:keyLabel="&gt;" />   <!-- > -->
<Key android:keyLabel="&quot;" /> <!-- " -->
```

✅ **6. Marcar row de números como extension:**
```xml
<Row android:extension="true">
    <Key android:keyLabel="1" />
    <!-- ... -->
</Row>
```

✅ **7. Marcar row bottom:**
```xml
<Row android:rowEdgeFlags="bottom">
    <!-- ... -->
</Row>
```

---

## Debugging Tips

### Ver qué atributos detecta el parser:
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

### Ver qué se dibuja:
```kotlin
private fun getDisplayLabel(key: Key): String? {
    val label = key.label
    if (label != null && label.isNotEmpty()) {
        Log.d(TAG, "getDisplayLabel: label=$label")
        return label
    }
    // ...
}
```

---

## Errores Comunes y Soluciones

| Error | Causa | Solución |
|-------|-------|----------|
| Teclas vacías (filas dibujadas sin contenido) | Namespace incorrecto o labels null | Usar `res-auto` + verificar getDisplayLabel() |
| Filas duplicadas | Rows con keyboardMode no skipeadas | Skip rows con `keyboardModeId != 0` |
| `attribute not found` en build | Atributo no declarado en attrs-keyboard.xml | Agregar a declare-styleable |
| `code=0` para todas las keys | TypedArray no usado o recycle() antes de leer | Usar obtainStyledAttributes() correctamente |
| Parser solo ve N-1 rows | XML corrupto o problema estructural | Crear XML simple desde cero |
| Labels con caracteres especiales no aparecen | XML no escapado | Usar `&amp;` `&lt;` `&gt;` `&quot;` |

---

## Archivos de Referencia Funcionales

### ✅ BUENOS EJEMPLOS (copiar de aquí):

- **kbd_pc.xml** - Layout alfabético completo y funcional
  - Namespace correcto
  - 5 filas sin keyboardMode
  - Extension row, edge flags, todos los atributos
  
- **kbd_symbols_simple.xml** - Layout símbolos simple y funcional
  - Namespace correcto
  - 4 filas sin keyboardMode
  - Símbolos básicos
  
- **SimpleKeyboard.kt** - Parser con TypedArray
  - Línea 100-112: Leer Row attributes
  - Línea 147-180: Leer Key attributes
  - Línea 117-129: Skip logic para keyboardMode

- **SimpleKeyboardView.kt** - Renderer
  - Línea 184-210: getDisplayLabel() con fallbacks
  - Línea 113-131: onDraw() loop

### ❌ EVITAR:

- **kbd_symbols.xml (original HackersKeyboard)** - Tiene rows con keyboardMode que causan problemas
- Usar `parser.getAttributeValue()` directamente sin TypedArray
- Namespace `http://schemas.android.com/apk/res/android`

---

## Resumen de la Sesión de Debugging

### Iteraciones hasta la solución:

1. ❌ Intentar leer keyboardMode con `getAttributeValue(null, "keyboardMode")` → devuelve null
2. ❌ Intentar con namespace completo → sigue null
3. ❌ Iterar atributos por nombre → detecta pero no lee valor
4. ✅ Usar TypedArray + getResourceId() → **FUNCIONA**
5. ❌ Agregar bottom row en medio del XML → parser no la ve
6. ❌ Mover bottom row al final → parser sigue sin verla
7. ❌ Copiar kbd_symbols completo de HackersKeyboard → problemas estructurales
8. ✅ Crear kbd_symbols_simple.xml desde cero → funciona pero keys vacías
9. ❌ Usar namespace `res/android` → todos los atributos null
10. ✅ Cambiar a namespace `res-auto` → **TODO FUNCIONA**

**Lección principal:** El namespace es CRÍTICO. Sin `res-auto`, nada más importa.

---

## Testing Checklist

Cuando hagas cambios al parser o XML, verifica:

- [ ] ¿El teclado alfabético (kbd_pc) sigue funcionando?
- [ ] ¿El teclado de símbolos (kbd_symbols_simple) muestra todas las teclas?
- [ ] ¿El botón ?123 cambia correctamente entre layouts?
- [ ] ¿El botón ABC vuelve al alfabético?
- [ ] ¿Los modificadores (Ctrl, Shift) se detectan?
- [ ] ¿Los símbolos especiales (@#$%&) se muestran correctamente?
- [ ] ¿No hay filas duplicadas?
- [ ] ¿Las teclas tienen el tamaño correcto (no aplastadas)?
- [ ] ¿Los logs de parser muestran las rows/keys esperadas?

---

## Comandos Útiles para Testing

```bash
# Clean build completo
./gradlew clean && ./gradlew installDebug

# Ver logs del parser
adb logcat -c
# [Usar teclado]
adb logcat -d | grep "SimpleKeyboard" | grep "ROW START"

# Ver keys parseadas
adb logcat -d | grep "SimpleKeyboard" | grep "Added key"

# Ver labels en render
adb logcat -d | grep "SimpleKeyboardView" | grep "getDisplayLabel"

# Verificar si hay errores
adb logcat -d | grep -E "Error|Exception|FATAL"
```

---

**Documento creado:** 2026-05-07  
**Proyecto:** DarkKeyboard  
**Propósito:** Evitar repetir los mismos errores al crear/modificar layouts XML
