# Parser Investigation Summary

## Context7 Research Results

Investigué cómo parsear layouts XML de Android Keyboard usando Context7 para verificar que el parser está implementado correctamente.

## Findings

### 1. Parser Implementation - ✅ CORRECTO

El parser de DarkKeyboard **YA está implementado correctamente** según el código de HackersKeyboard:

```kotlin
// SimpleKeyboard.kt línea 128-131
val a = context.obtainStyledAttributes(
    Xml.asAttributeSet(parser),
    R.styleable.Keyboard_Key
)

// Lectura de atributos
val code = a.getInt(R.styleable.Keyboard_Key_codes, Int.MIN_VALUE)
val isModifier = a.getBoolean(R.styleable.Keyboard_Key_isModifier, false)
val isSticky = a.getBoolean(R.styleable.Keyboard_Key_isSticky, false)
val isRepeatable = a.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false)
a.recycle()
```

Esto es **idéntico** a como lo hace HackersKeyboard (Keyboard.java línea 459-483).

### 2. Styleable Attributes - ✅ CORRECTO

El attrs-keyboard.xml está correctamente declarado:

```xml
<declare-styleable name="Keyboard_Key">
    <attr name="codes" format="integer|string" />
    <attr name="keyLabel" format="string" />
    <attr name="isModifier" format="boolean" />
    <attr name="isSticky" format="boolean" />
    <attr name="isRepeatable" format="boolean" />
    <!-- ... -->
</declare-styleable>
```

### 3. Real Problem - Límite de Filas

El problema NO es el parser, sino que había un límite de 5 filas:

```kotlin
// SimpleKeyboard.kt línea 91 (ANTES)
if (rowCount >= 5) {
    currentRow = null  // ❌ Ignoraba filas 6+
}

// AHORA (FIJO)
if (rowCount >= 6) {
    currentRow = null  // ✅ Permite 6 filas
}
```

### 4. Keyboard Layout Structure (kbd_full.xml)

```
Row 1 (línea 29):  extension=true - F1-F12
Row 2 (línea 52):  rowEdgeFlags=top - ~`1234567890-=
Row 3 (línea 131): TAB + QWERTY
Row 4 (línea 207): ASDFGHJKL;'
Row 5 (línea 278): ZXCVBNM,./
Row 6 (línea 353): Bottom row con modificadores
```

Tab está en Row 3, Enter probablemente en Row 5 o 6.

## Solution Applied

✅ Aumentado límite de filas de 5 a 6 (commit 2a6cf3d)
✅ Agregados logs de debugging para verificar qué filas se parsean
✅ Parser ya funciona correctamente con TypedArray

## Next Steps

1. Instalar APK y verificar logs con `adb logcat | grep SimpleKeyboard`
2. Verificar que Tab y Enter se ven en el teclado
3. Si no se ven, revisar los logs para confirmar qué filas se están parseando

## Referencias

- HackersKeyboard Keyboard.java línea 459-483: Parser con obtainAttributes
- Android TypedArray: https://developer.android.com/reference/android/content/res/TypedArray
- Context7 Android Core docs sobre keyboard layouts
