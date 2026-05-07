# DarkKeyboard ⌨️

**A modern Android IME with full PC keyboard layout**

Complete 5-row keyboard with dedicated number row, function keys, modifiers (Ctrl, Alt, Fn), and navigation keys - all optimized for mobile devices.

---

## ✨ Features

### Layout & Design
- **5-row PC layout** - Numbers, QWERTY, ASDFGHJKL, ZXCVBNM+Shift, Control row
- **Symbol mode** - Quick access to @#$%&*-+()!"':;/?=_
- **Clean UI** - Minimal design with rounded keys and proper spacing
- **Adaptive height** - Automatically adjusts to screen size (max 38% screen height)

### Functionality
- **Modifier keys** - Ctrl, Alt, Shift with sticky state support
- **Special keys** - Esc, Tab, Enter, Backspace, Space
- **Layout switching** - ?123 button toggles between alphabetic and symbol modes
- **Touch detection** - Accurate multitouch support

### Technical
- **Custom XML parser** - TypedArray-based parser for keyboard layouts
- **Lightweight** - ~900 lines of code vs 6,000+ in similar projects
- **Modern Kotlin** - Clean architecture with data classes
- **No dependencies** - Pure Android SDK implementation

---

## 🚀 Quick Start

### Build from source

```bash
git clone https://github.com/JDis03/darkkeyboard.git
cd darkkeyboard
./gradlew assembleDebug
```

**Output:** `app/build/outputs/apk/debug/app-debug.apk`

### Install

```bash
./gradlew installDebug
```

Or install the APK manually via ADB or file manager.

### Enable keyboard

1. Go to **Settings → System → Languages & input → On-screen keyboard → Manage keyboards**
2. Enable **DarkKeyboard**
3. Open any app with text input
4. Tap the keyboard switcher icon in the navigation bar
5. Select **DarkKeyboard**

---

## 📋 Requirements

- **Android 5.0+** (API 21 minimum)
- **Target:** Android 14 (API 34)
- **Build:** Kotlin 1.8.22 + Java 11

---

## 🏗️ Architecture

### Core Components

| File | Lines | Purpose |
|------|-------|---------|
| **SimpleKeyboard.kt** | 306 | XML parser with TypedArray - reads keyboard layout files |
| **SimpleKeyboardView.kt** | 333 | Custom View - rendering + touch detection |
| **DarkIME2.kt** | 234 | InputMethodService - lifecycle + layout switching |
| **Key.kt** | 56 | Data class - key properties and constants |

### Keyboard Layouts (XML)

| File | Description |
|------|-------------|
| `kbd_pc.xml` | Main alphabetic layout - 5 rows with modifiers |
| `kbd_symbols_simple.xml` | Symbol layout - numbers + symbols + bottom row |

### Key Technical Details

**Parser:**
- Uses `XmlResourceParser` + `TypedArray` for correct attribute resolution
- Namespace: `xmlns:android="http://schemas.android.com/apk/res-auto"` (critical!)
- Skips rows with `keyboardMode` (alternative layout variations)
- Attributes defined in `res/values/attrs-keyboard.xml`

**Rendering:**
- Custom `onDraw()` with Canvas API
- Label fallback: XML label → ASCII code → special symbol mapping
- Colors: Dark keys with light text, rounded corners

**Input:**
- `onTouchEvent()` with multitouch support
- Modifier state tracking (Ctrl/Shift/Alt)
- Layout switching via `switchLayout()`

---

## 🐛 Debugging

See [PARSER_LESSONS_LEARNED.md](PARSER_LESSONS_LEARNED.md) for:
- Common XML parsing issues and solutions
- Namespace problems (res-auto vs res/android)
- TypedArray usage patterns
- Testing commands
- Complete debugging checklist

**Quick logs:**

```bash
# Clear logs
adb logcat -c

# View parser activity
adb logcat -d | grep "SimpleKeyboard"

# View rendering activity
adb logcat -d | grep "SimpleKeyboardView"

# Check for errors
adb logcat -d | grep -E "Error|Exception|FATAL"
```

---

## 🎯 Project Goals

### What DarkKeyboard IS:
- Lightweight, maintainable IME with PC layout
- Learning project for Android IME development
- Modern Kotlin implementation with clean architecture
- Functional keyboard for developers and power users

### What DarkKeyboard is NOT:
- Feature parity with Hacker's Keyboard (no themes, languages, extensive customization)
- Production-ready for general users (experimental project)
- Replacement for GBoard, SwiftKey, or similar keyboards

---

## 📜 License

**Apache License 2.0**

This project is based on the Android Open Source Project (AOSP) LatinIME and inherits its Apache 2.0 license.

---

## 🙏 Credits

### Original Source
This keyboard is built from scratch but uses layout definitions and key code constants adapted from:

- **[Hacker's Keyboard](https://github.com/klausw/hackerskeyboard)** by Klaus Weidner
  - Original 5-row PC keyboard layout for Android
  - XML layout structure (`kbd_*.xml` files)
  - Key code definitions and modifier handling patterns
  - Apache 2.0 License

### Lineage
```
Android AOSP LatinIME (Gingerbread era)
    ↓
Hacker's Keyboard (2011-2022)
    ↓
DarkKeyboard (2026) - Modern Kotlin rewrite
```

**Key differences from Hacker's Keyboard:**
- Complete Kotlin rewrite (~900 LOC vs 6,000+)
- Simplified parser (no multi-language support, no themes)
- Modern Android APIs (API 34 target)
- Custom TypedArray-based XML parser
- No native code dependencies

---

## 📊 Stats

- **Total code:** ~900 lines (Kotlin + XML)
- **Code reduction:** 85%+ vs Hacker's Keyboard
- **Build time:** ~10 seconds (clean build)
- **APK size:** ~300KB (debug build)
- **Supported layouts:** 2 (alphabetic + symbols)

---

## 🔮 Future Ideas

- [ ] Visual feedback for active modifiers (Ctrl/Alt/Shift)
- [ ] Customizable key heights and widths
- [ ] Haptic feedback option
- [ ] Sound on keypress
- [ ] Additional symbol layouts
- [ ] Arrow keys layout
- [ ] Function key row toggle

**Note:** This is a learning/experimental project. Pull requests welcome but no guarantee of maintenance.

---

**Made with ❤️ for developers who miss real keyboards on mobile**
