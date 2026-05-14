# DarkKeyboard ⌨️

**A modern Android IME with Gboard-style proportions and PC functionality**

Mobile-optimized keyboard layouts with consistent letter sizes, smart auto-centering, sticky modifiers, and modern Material Design 3 settings - the perfect balance between mobile usability and desktop power.

---

## ✨ Features

### 🎯 Enhanced Precision (v1.1.0)

**Hit Zone Expansion**
- Touch detection area 20% larger than visual bounds
- Captures "almost correct" touches that would normally miss
- Reduces typing errors significantly
- Matches modern keyboard behavior (Gboard/SwiftKey)

**Optimized Spacing**
- 6dp visible gap between rows (reduced accidental touches)
- 1dp padding inside keys (sleek appearance)
- Consistent muscle memory across all layouts

### 🎨 Two Professional Layouts

**QWERTY Standard** - Mobile-optimized with Gboard proportions
- Row 1: Numbers 1-0 @ 10% each (toggleable extension row)
- Row 2: QWERTY @ 10% each
- Row 3: ASDFGHJKL @ 10% each (auto-centered with 5% padding)
- Row 4: Shift (15%) + ZXCVBNM @ 10% each + Delete (15%)
- Row 5: Ctrl (15%) + ?123 (15%) + Space (35%) + . (15%) + Enter (20%)
- **All letters same size (10%)** - Consistent muscle memory

**PC Compact** - 6-row layout with navigation
- Extension row for numbers
- Tab key beside 'a'
- Arrow keys in bottom row
- Fn/Ctrl/Alt modifiers
- Perfect for terminal work and coding

### 🎯 Smart Features

**Auto-Centering System**
- Rows that don't use 100% width are automatically centered
- ASDFGHJKL perfectly centered with equal padding
- Future-proof for custom layouts

**Sticky Modifiers**
- Ctrl, Alt, Fn remain active after press
- Visual status bar showing active modifiers: `[ Ctrl + Shift + Alt + Fn ]`
- Shift changes letters to uppercase in real-time
- Ctrl+Backspace deletes entire word

**Punctuation Popup**
- Long press on . , ? ! for quick access
- Swipe to select without lifting finger
- Visual highlighting of selected option
- Numbers (1-0) have symbol popups too

**Live Layout Editor (Experimental)**
- Create and edit custom XML layouts in-app
- Load custom layouts from device storage
- Fallback to built-in layouts on error
- Perfect for advanced users and developers

**Modern Settings (Material Design 3)**
- Layout selector with visual cards
- Number row toggle (show/hide extension row)
- Modifier status bar toggle
- Instant keyboard reload on changes

### ⚙️ Technical Excellence
- **Hit zone expansion** - 20% larger touch detection for precision
- **Optimized spacing** - 6dp gaps reduce row confusion
- **Custom XML parser** - TypedArray-based with support for 6 rows
- **RDP compatible** - Physical KeyEvents for Ctrl+Shift+V, etc.
- **Memory leak free** - Proper listener cleanup
- **Crash resistant** - try-catch on all InputConnection operations
- **Lightweight** - Clean Kotlin architecture (~2,000 LOC)
- **Modern Compose UI** - Settings built with Jetpack Compose
- **Pure Android** - No external dependencies

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
- **Target:** Android 14+ (API 35)
- **Build:** Kotlin 2.0.21 + Java 17
- **Gradle:** 8.8+

---

## 🏗️ Architecture

### Core Components

| File | Lines | Purpose |
|------|-------|---------|
| **DarkIME2.kt** | ~380 | InputMethodService - lifecycle, RDP compatibility, error handling |
| **SimpleKeyboard.kt** | ~355 | XML parser with gap calculation and auto-centering |
| **SimpleKeyboardView.kt** | ~570 | Custom View - rendering with hit expansion + touch detection |
| **Key.kt** | ~60 | Data class - key properties with expanded hit zones |
| **SettingsActivity.kt** | ~680 | Compose settings with layout editor |
| **PopupPreview.kt** | ~185 | Punctuation popup with swipe selection |

### Keyboard Layouts (XML)

| File | Description |
|------|-------------|
| `kbd_pc.xml` | QWERTY Standard - 5 rows with Gboard proportions |
| `kbd_compact.xml` | PC Compact - 6 rows with arrows and modifiers |
| `kbd_symbols_simple.xml` | Symbol layout - numbers + symbols + bottom row |

### Key Technical Details

**Parser:**
- Uses `XmlResourceParser` + `TypedArray` for correct attribute resolution
- Namespace: `xmlns:android="http://schemas.android.com/apk/res-auto"` (critical!)
- Skips rows with `keyboardMode` (alternative layout variations)
- Attributes defined in `res/values/attrs-keyboard.xml`

**Rendering:**
- Custom `onDraw()` with Canvas API
- Hit zone expansion (+20% beyond visual bounds)
- 6dp visible gap between rows (applied in drawKey)
- Label fallback: XML label → ASCII code → special symbol mapping
- Colors: Dark keys with light text, rounded corners

**Input:**
- `onTouchEvent()` with multitouch support
- Hit zone expansion for precision (20% larger detection)
- Modifier state tracking (Ctrl/Shift/Alt/Fn with sticky behavior)
- Layout switching via `switchLayout()`
- Physical KeyEvent generation for RDP compatibility

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
- Lightweight, maintainable IME with PC functionality
- Learning project for modern Android IME development
- Precision-focused with hit zone expansion
- RDP/SSH compatible keyboard for remote work
- Functional keyboard for developers and power users
- Open source educational resource

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

- **Total code:** ~2,000 lines (Kotlin + XML)
- **Core files:** 10+ Kotlin files
- **Build time:** ~5-10 seconds (clean build)
- **APK size:** ~800KB (debug build)
- **Supported layouts:** 3 (QWERTY Standard, PC Compact, Symbols)
- **Hit zone expansion:** 20% beyond visual bounds
- **Vertical gap:** 6dp between rows

---

## 🗺️ Roadmap

See [CHANGELOG.md](CHANGELOG.md) for detailed version history and full feature list.

### v1.1.0 (Next Release)
- [ ] Theme system (dark/light/custom colors)
- [ ] Improved symbol layout with more punctuation
- [ ] Emoji keyboard
- [ ] Clipboard manager integration
- [ ] Haptic feedback settings
- [ ] Sound on keypress option

### v1.2.0
- [ ] Word suggestions/autocorrect
- [ ] Swipe typing
- [ ] Custom keyboard height slider
- [ ] Customizable key colors per row
- [ ] Export/import settings

### v2.0.0 (Major Release)
- [ ] Full customization system
- [ ] User-defined layouts (XML editor)
- [ ] Cloud sync for settings
- [ ] Multi-language support
- [ ] Advanced gesture controls

**Completed Features (v1.0.0 + improvements):**
- ✅ QWERTY Standard layout with Gboard proportions
- ✅ PC Compact layout with 6 rows + arrows
- ✅ Auto-centering system
- ✅ Number row toggle
- ✅ Punctuation popup with swipe
- ✅ Sticky modifiers with visual status bar
- ✅ Modern Compose settings UI
- ✅ Consistent 10% letter sizes
- ✅ **Hit zone expansion (+20%)** - Enhanced precision
- ✅ **Optimized gaps (6dp)** - Reduced row confusion
- ✅ **RDP compatibility** - Physical KeyEvents for Ctrl+V, etc.
- ✅ **Live layout editor** - Create custom XML layouts in-app
- ✅ **Memory leak fixes** - Proper listener cleanup
- ✅ **Crash resistance** - try-catch on InputConnection ops

---

**Made with ❤️ for developers who miss real keyboards on mobile**
