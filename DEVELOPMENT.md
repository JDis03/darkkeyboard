# Development Guide

Guide for developing and contributing to DarkKeyboard.

## 📁 Project Structure

```
darkkeyboard/
├── app/src/main/
│   ├── java/org/dark/keyboard/
│   │   ├── DarkIME2.kt              # Main IME service (lifecycle, layout switching)
│   │   ├── SimpleKeyboard.kt        # XML parser (TypedArray-based, 6-row support)
│   │   ├── SimpleKeyboardView.kt    # Custom View (rendering, touch, modifiers)
│   │   ├── Key.kt                   # Key data class + constants
│   │   ├── PopupPreview.kt          # Punctuation popup with swipe
│   │   ├── SettingsActivity.kt      # Modern Compose settings UI
│   │   └── Theme.kt                 # Material Design 3 theme
│   │
│   ├── res/
│   │   ├── xml/
│   │   │   ├── kbd_pc.xml           # QWERTY Standard (5 rows, Gboard-style)
│   │   │   ├── kbd_compact.xml      # PC Compact (6 rows, arrows)
│   │   │   └── kbd_symbols_simple.xml # Symbol layout
│   │   ├── values/
│   │   │   ├── attrs-keyboard.xml   # Custom XML attributes
│   │   │   ├── keycodes.xml         # Key code definitions
│   │   │   ├── dimens.xml           # Key heights, gaps, etc.
│   │   │   └── strings.xml          # Layout names, descriptions
│   │   └── layout/
│   │       └── keyboard_view.xml    # Root layout (keyboard + status bar)
│   │
│   └── AndroidManifest.xml          # IME service declaration
│
├── CHANGELOG.md                      # Version history
├── DEVELOPMENT.md                    # This file
├── PARSER_LESSONS_LEARNED.md        # Parser troubleshooting guide
└── README.md                         # Main documentation
```

---

## 🏗️ Architecture Overview

### IME Pipeline Flow

```
User Touch Event
    ↓
SimpleKeyboardView.onTouchEvent()
    ↓
Key detection (which key was pressed?)
    ↓
Modifier state update (Shift/Ctrl/Alt/Fn)
    ↓
OnKeyListener.onKey(code, shift, ctrl, alt, fn)
    ↓
DarkIME2.handleKey()
    ↓
InputConnection.commitText() or sendKeyEvent()
    ↓
Text appears in app
```

### Component Responsibilities

| Component | Responsibility |
|-----------|----------------|
| **DarkIME2** | IME lifecycle, layout switching, preference handling, modifier meta state |
| **SimpleKeyboard** | Parse XML layouts, create Key objects, auto-centering logic |
| **SimpleKeyboardView** | Render keys, handle touch, manage modifier state, show popups |
| **Key** | Data class with position, size, label, code |
| **PopupPreview** | Long press popup, swipe selection, visual feedback |
| **SettingsActivity** | Compose UI, preference management, layout selection |

---

## 🔧 Common Development Tasks

### Adding a New Key

1. **Add keycode to `keycodes.xml`:**
```xml
<integer name="key_my_new_key">-200</integer>
```

2. **Add key to layout XML:**
```xml
<Key 
    android:codes="@integer/key_my_new_key"
    android:keyLabel="Label"
    android:keyWidth="10%p" />
```

3. **Handle key in `DarkIME2.handleKey()`:**
```kotlin
Key.CODE_MY_NEW_KEY -> {
    // Your logic here
    sendKeyEvent(KeyEvent.KEYCODE_SOMETHING, metaState)
}
```

4. **Add constant to `Key.kt`:**
```kotlin
const val CODE_MY_NEW_KEY = -200
```

### Creating a New Layout

1. **Create XML file** in `res/xml/kbd_*.xml`
2. **Follow this structure:**
```xml
<?xml version="1.0" encoding="utf-8"?>
<Keyboard
    xmlns:android="http://schemas.android.com/apk/res-auto"
    android:keyWidth="10%p"
    android:horizontalGap="0px"
    android:verticalGap="@dimen/key_bottom_gap">
    
    <Row android:extension="true">
        <!-- Extension row (optional, toggleable) -->
    </Row>
    
    <Row>
        <!-- Regular row -->
    </Row>
</Keyboard>
```

3. **Add to `DarkIME2.getLayoutResourceId()`:**
```kotlin
"mynewlayout" -> R.xml.kbd_mynewlayout
```

4. **Add to settings dialog** in `SettingsActivity.kt`

### Modifying Proportions

**Global defaults** in `SimpleKeyboard.kt`:
```kotlin
val defaultKeyWidth = screenWidth / 10  // 10% default
val verticalGapPx = (1.5f * density).toInt()
```

**Per-row heights** in `SimpleKeyboard.kt`:
```kotlin
val numberRowHeight = (availableHeightForRows * 0.18f).toInt()
val rowHeight = (availableHeightForRows * 0.21f).toInt()
val bottomRowHeight = (availableHeightForRows * 0.19f).toInt()
```

**Per-key widths** in layout XML:
```xml
<Key android:keyWidth="15%p" />  <!-- 15% of screen width -->
```

### Adding a Setting

1. **Add state in `SettingsActivity.kt`:**
```kotlin
var myNewSetting by remember { 
    mutableStateOf(prefs.getBoolean("my_new_setting", true)) 
}
```

2. **Add UI:**
```kotlin
SettingSwitchItem(
    icon = Icons.Default.Star,
    title = "My New Setting",
    subtitle = "Description here",
    checked = myNewSetting,
    onCheckedChange = { checked ->
        myNewSetting = checked
        prefs.edit().putBoolean("my_new_setting", checked).apply()
    }
)
```

3. **Use in `DarkIME2.kt`:**
```kotlin
val myNewSetting = prefs.getBoolean("my_new_setting", true)
```

4. **Add listener if needs keyboard reload:**
```kotlin
if (key == "my_new_setting") {
    reloadKeyboard()
}
```

---

## 🐛 Debugging

### Enable Logcat Filtering

```bash
# Clear logs
adb logcat -c

# View parser logs
adb logcat SimpleKeyboard:D *:S

# View IME logs
adb logcat DarkIME2:I *:S

# View all DarkKeyboard logs
adb logcat -d | grep -E "SimpleKeyboard|DarkIME2|SimpleKeyboardView"

# Check for errors
adb logcat -d | grep -E "Error|Exception|FATAL"
```

### Common Issues

**Keys not showing up:**
- Check namespace in XML: `xmlns:android="http://schemas.android.com/apk/res-auto"`
- Check `attrs-keyboard.xml` has all attributes defined
- Check TypedArray is used for reading `codes` attribute
- Check row count limit (max 6 rows)

**Keys in wrong position:**
- Check auto-centering logic in `SimpleKeyboard.kt` END_TAG handler
- Check `keyWidth` percentages sum correctly
- Check `horizontalGap` and `verticalGap` values

**Modifiers not working:**
- Check `isModifier="true"` in XML
- Check `isSticky="true"` for sticky behavior
- Check modifier state in `SimpleKeyboardView.kt`
- Check meta state building in `DarkIME2.handleKey()`

**Preference changes not reflected:**
- Check listener in `DarkIME2.onCreate()`
- Check `reloadKeyboard()` is called
- Check preference key matches exactly
- Restart keyboard (close/reopen input field)

---

## 🧪 Testing Workflow

### 1. Build & Install
```bash
./gradlew installDebug
```

### 2. Test in Emulator
- Open any text field
- Switch to DarkKeyboard
- Test all keys, modifiers, layouts

### 3. Check Logs
```bash
adb logcat -c && adb logcat SimpleKeyboard:D DarkIME2:I *:S
```

### 4. Test Edge Cases
- Rotate device (portrait/landscape)
- Different text field types (email, URL, number)
- Long press keys
- Modifier combinations (Ctrl+Shift+A)
- Layout switching (ABC ↔ ?123)
- Settings changes (toggle number row, change layout)

---

## 📦 Release Process

### Version Numbering

Follow [Semantic Versioning](https://semver.org/):
- **Major (X.0.0)**: Breaking changes
- **Minor (1.X.0)**: New features
- **Patch (1.0.X)**: Bug fixes

### Creating a Release

1. **Update version** in `build.gradle.kts`:
```kotlin
versionCode = 101  // Increment by 1
versionName = "1.0.1"
```

2. **Update `CHANGELOG.md`:**
```markdown
## [1.0.1] - 2026-05-XX

### Added
- New feature description

### Fixed
- Bug fix description
```

3. **Commit changes:**
```bash
git add -A
git commit -m "chore: bump version to 1.0.1"
```

4. **Create git tag:**
```bash
git tag -a v1.0.1 -m "Release v1.0.1"
git push origin main
git push origin v1.0.1
```

5. **Build release APK:**
```bash
./gradlew assembleRelease
```

6. **Create GitHub release:**
- Go to Releases → New Release
- Select tag v1.0.1
- Copy changelog section
- Attach APK from `app/build/outputs/apk/release/`

---

## 🎨 Code Style

### Kotlin
- Use 4 spaces for indentation
- Max line length: 120 characters
- Use `val` over `var` when possible
- Prefer data classes for simple data structures
- Use meaningful variable names

### XML
- Use 4 spaces for indentation
- Use `android:` prefix for standard attributes
- Keep attributes alphabetically sorted
- Comment complex layouts

### Logging
- Use descriptive tags: `SimpleKeyboard`, `DarkIME2`, etc.
- Log.d() for debug info
- Log.i() for important events
- Log.e() for errors

---

## 📚 Resources

### Essential Reading
- [PARSER_LESSONS_LEARNED.md](PARSER_LESSONS_LEARNED.md) - Parser troubleshooting
- [CHANGELOG.md](CHANGELOG.md) - Version history and features
- [Android IME Guide](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)

### Useful Android APIs
- `InputMethodService` - Base class for IME
- `InputConnection` - Interface to send text to apps
- `KeyEvent` - System key events
- `XmlResourceParser` - Parse XML resources
- `TypedArray` - Read XML attributes with proper type resolution

---

## 🤝 Contributing

### Pull Request Process
1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Make changes following code style
4. Test thoroughly
5. Update documentation if needed
6. Commit with clear messages
7. Push and create PR

### Commit Message Format
```
<type>: <description>

[optional body]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `docs`: Documentation only
- `style`: Formatting changes
- `test`: Adding tests
- `chore`: Maintenance tasks

**Examples:**
```
feat: add emoji keyboard layout
fix: correct ASDF row centering
refactor: simplify parser logic
docs: update README with new features
```

---

## 🔍 Key Implementation Details

### Auto-Centering Logic

In `SimpleKeyboard.kt`, when closing a Row:
```kotlin
val totalRowWidth = row.keys.sumOf { it.width + keyboardHorizontalGap }
if (totalRowWidth < screenWidth) {
    val offset = (screenWidth - totalRowWidth) / 2
    row.keys.forEach { key ->
        key.x += offset
    }
}
```

### Extension Row Toggle

In `SimpleKeyboard.kt`, when parsing Row:
```kotlin
if (isExtension && !showExtensionRow) {
    currentRow = null  // Skip this row
}
```

### Modifier State Management

In `SimpleKeyboardView.kt`:
```kotlin
private var shiftPressed = false
private var ctrlPressed = false
private var altPressed = false
private var fnPressed = false

// Toggle on modifier key press
if (key.isSticky) {
    when (key.code) {
        Key.CODE_SHIFT -> shiftPressed = !shiftPressed
        Key.CODE_CTRL_LEFT -> ctrlPressed = !ctrlPressed
        // etc...
    }
}
```

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/JDis03/darkkeyboard/issues)
- **Discussions**: [GitHub Discussions](https://github.com/JDis03/darkkeyboard/discussions)
- **Email**: (if applicable)

---

**Happy coding! 🚀**
