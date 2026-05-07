# DarkKeyboard

Android IME moderno con layout PC completo - 5 filas con números, QWERTY y modificadores (Ctrl, Alt, Esc, Tab, F-keys).

## Features

- ✅ **Layout PC completo**: 5 filas (números + 3 letras + bottom row con modificadores)
- ✅ **Modificadores funcionales**: Ctrl, Alt, Meta, Fn con estado sticky
- ✅ **Teclas especiales**: Esc, Tab, Home, End, PgUp/PgDn, Insert, F1-F12
- ✅ **Atajos de teclado**: Ctrl+C/V/X/Z/A, Ctrl+Backspace (delete word), etc.
- ✅ **Parser XML funcional**: Lee layouts de HackersKeyboard correctamente
- ✅ **Arquitectura limpia**: ~600 líneas vs HackersKeyboard 6,336 líneas (91% menos código)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Architecture

- **SimpleKeyboard.kt** (264 líneas): Parser XML con TypedArray, lee kbd_full.xml
- **SimpleKeyboardView.kt** (281 líneas): View que dibuja teclas y detecta touch
- **DarkIME2.kt** (57 líneas): InputMethodService con manejo de modificadores
- **Key.kt**: Data class con keycodes de HackersKeyboard

## Technical Details

### Parser XML
Usa `TypedArray` para leer correctamente:
- `@integer/key_*` references (códigos negativos como -113 para Ctrl)
- Atributos custom: `isModifier`, `isSticky`, `isRepeatable` desde attrs-keyboard.xml

### Keycodes
Compatible con HackersKeyboard:
- Ctrl: -113, Alt: -57, Meta: -117, Fn: -119
- F1-F12: -131 a -142
- Esc: -111, Tab: 9, Enter: 10

### Layout
`kbd_full.xml` (408 líneas) - 5 filas completas con modificadores visibles

## Requirements

- Android 5.0+ (API 21+)
- Target: Android 14 (API 34)
- Kotlin 1.8.22 + Java 11

## License

Apache 2.0 (heredado de Android AOSP LatinIME y HackersKeyboard)
