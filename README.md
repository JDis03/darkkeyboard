# DarkKeyboard ⌨️

**Modern Android IME with neural autocorrect, next-word prediction, and PC functionality**

Mobile keyboard with T5 neural suggestions, smart autocorrect, multi-language support (ES/EN), Gboard-style UI, and developer-friendly features. Privacy-first with local ML models.

---

## ✨ Features

### 🤖 Smart Typing & AI

**Neural Autocorrect**
- T5 encoder model (34MB TFLite INT8) for contextual re-ranking
- Confidence-based correction (conservative/balanced/aggressive modes)
- Safety guards: no correction for ALL_CAPS, digits, personal dictionary words
- Undo with backspace (session-based rejection memory)
- Only corrects words NOT in dictionary (typedFreq > 0 → skip)

**Next-Word Prediction**
- Bigram-based prediction with automatic user learning
- Frequency overlay (learns from your typing patterns)
- Forgetting curve (like AOSP UserHistoryDictionary)
- Privacy-first: all local, no cloud sync, no telemetry

**Multi-Language Support**
- Spanish + English dictionaries (8K+ words each)
- Per-language user frequency tracking
- Easy language switching in settings
- Separate autocorrect settings per language

**App-Specific Behavior (AppInputProfile)**
- **WebView** (Chrome/Vivaldi): no IC composing (fixes underline bug), autocorrect ON
- **Terminal** (Termux): autocorrect OFF, composing OFF
- **Password fields**: all features OFF (security)
- **Standard apps**: full autocorrect + suggestions + composing

### 🎯 Enhanced Precision

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
| **DictSuggestionEngine.kt** | ~500 | Multi-lang suggestions with frequency overlay + bigram learning |
| **TFLiteReRanker.kt** | ~180 | T5 neural re-ranking (contextual scoring with embeddings) |
| **CompactTrie.kt** | ~200 | Trie with O(1) freq lookup + edit distance search |
| **AutocorrectEngine.kt** | ~650 | Autocorrect state machine + safety guards + undo |
| **DarkIME2.kt** | ~1100 | InputMethodService + autocorrect integration + AppInputProfile |
| **SimpleKeyboardView.kt** | ~570 | Custom View - rendering with hit expansion + touch detection |
| **SimpleKeyboard.kt** | ~355 | XML parser with gap calculation and auto-centering |
| **SettingsActivity.kt** | ~680 | Compose settings with layout editor + autocorrect config |
| **PopupPreview.kt** | ~185 | Punctuation popup with swipe selection |

### ML Pipeline

| File | Purpose |
|------|---------|
| **ml_pipeline/train_model.py** | T5 encoder training + TFLite INT8 export |
| **suggestions_model.tflite** | 34MB quantized model (downloaded separately) |
| **spiece.model** | SentencePiece tokenizer (~800KB) |
| **dict_es.txt / dict_en.txt** | Spanish/English dictionaries (8K-10K words) |

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

- **Total code:** ~5,000+ lines (Kotlin + Python ML pipeline)
- **Core files:** 15+ Kotlin files + ML pipeline
- **Build time:** ~10-15 seconds (clean build)
- **APK size:** ~45MB (with TFLite model) / ~2MB (without model)
- **Model size:** 34MB (T5 encoder INT8 quantized)
- **Dictionaries:** 2 languages (ES: 8K words, EN: 10K words)
- **Supported layouts:** 3 (QWERTY Standard, PC Compact, Symbols)
- **Autocorrect modes:** 3 (conservative/balanced/aggressive)
- **Hit zone expansion:** 20% beyond visual bounds
- **Vertical gap:** 6dp between rows

---

## 🗺️ Roadmap

See [CHANGELOG.md](CHANGELOG.md) for detailed version history and full feature list.

### ✅ Completed (v1.2.0)
- ✅ **Neural autocorrect** with T5 encoder (34MB TFLite)
- ✅ **Next-word prediction** with bigram learning
- ✅ **Multi-language support** (Spanish + English)
- ✅ **Frequency overlay** (learns from user typing)
- ✅ **App-specific behavior** (WebView/Terminal/Password profiles)
- ✅ **Undo autocorrect** with backspace
- ✅ QWERTY Standard + PC Compact layouts
- ✅ Hit zone expansion (+20%)
- ✅ Sticky modifiers + visual status bar
- ✅ Punctuation popup with swipe
- ✅ Modern Compose settings UI
- ✅ RDP compatibility (physical KeyEvents)
- ✅ Live layout editor

### 🚧 In Progress (v1.3.0)
- [ ] **Smart typing features** (auto-cap, double-space→period, smart punctuation)
- [ ] Clipboard manager with history
- [ ] Cursor control (swipe spacebar to move cursor)
- [ ] Theme system (light/dark/custom colors)
- [ ] Improved symbol layout

### 🔮 Future (v2.0.0+)
- [ ] **Glide typing** (swipe to type) - HIGH PRIORITY
- [ ] Trigramas (3-word context for better prediction)
- [ ] Voice typing integration
- [ ] Emoji/GIF search
- [ ] Floating/one-handed mode
- [ ] Custom keyboard height slider
- [ ] Haptic feedback + sound settings
- [ ] Export/import settings

---

**Made with ❤️ for developers who miss real keyboards on mobile**
