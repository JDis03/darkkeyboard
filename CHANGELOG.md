# Changelog

All notable changes to DarkKeyboard will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-08

### Added
- **QWERTY Standard Layout** - Mobile-optimized layout with Gboard-inspired proportions
  - Row 1: Numbers 1-0 @ 10% each (extension row, toggleable)
  - Row 2: QWERTY @ 10% each
  - Row 3: ASDFGHJKL @ 10% each (auto-centered with 5% padding on sides)
  - Row 4: Shift (15%) + ZXCVBNM @ 10% each + Delete (15%)
  - Row 5: Ctrl (15%) + ?123 (15%) + Space (35%) + . (15%) + Enter (20%)
  
- **PC Compact Layout** - 6-row layout with navigation arrows
  - Extension row for numbers
  - Tab key beside 'a'
  - Arrow keys in bottom row
  - Fn/Ctrl/Alt modifiers
  
- **Auto-centering System** - Rows that don't use 100% width are automatically centered
  - ASDFGHJKL row perfectly centered
  - Future-proof for any custom layouts
  
- **Number Row Toggle** - User preference to show/hide extension row
  - Setting in Keyboard section
  - Instant reload on toggle
  - Persists across sessions
  
- **Punctuation Popup** - Long press on . , ? !
  - Swipe to select without lifting finger
  - Visual highlighting of selected option
  - Centered above key
  
- **Sticky Modifiers** - Ctrl, Alt, Fn remain active after press
  - Visual status bar showing active modifiers
  - Shift changes letters to uppercase in real-time
  - Ctrl+Backspace deletes word
  
- **Modern Settings UI** - Compose-based settings with Material Design 3
  - Layout selector (QWERTY Standard / PC Compact)
  - Number row toggle
  - Modifier status bar toggle
  - About section with credits
  
- **XML Keyboard Parser** - Robust parser for keyboard layouts
  - Supports up to 6 rows
  - Extension row detection
  - Custom attributes (isModifier, isSticky, isRepeatable)
  - TypedArray for resource references
  - Auto-centering logic

### Fixed
- **Consistent Letter Sizes** - All letters now 10% width for uniform muscle memory
  - QWERTY: 10%
  - ASDFGHJKL: 10%
  - ZXCVBNM: 10% (was 8.57%)
  
- **Vertical Spacing** - Gboard-style spacing between rows
  - key_bottom_gap: 0.015in (was 0.000in)
  - Easier to type without errors
  
- **Row Centering** - ASDFGHJKL row properly centered
  - 9 keys @ 10% = 90% total width
  - 5% padding on each side
  - Perfect visual alignment

### Changed
- **Space Bar** - Increased from 30% to 35% for better thumb access
- **Shift/Delete** - Reduced from 20% to 15% to accommodate consistent letter sizes
- **Ctrl Position** - Added to bottom row (differentiator vs Gboard)

## [0.1.0] - 2026-05-01 (Initial Development)

### Added
- Initial fork from Hacker's Keyboard
- Basic InputMethodService structure
- Simple keyboard view rendering
- Touch event handling
- Basic symbol layout

---

## Version Numbering

- **Major (X.0.0)**: Breaking changes, major feature releases
- **Minor (1.X.0)**: New features, non-breaking changes
- **Patch (1.0.X)**: Bug fixes, small improvements

## Planned Features (Roadmap)

### v1.1.0 (Next Release)
- [ ] Theme system (dark/light/custom colors)
- [ ] Improved symbol layout with more punctuation
- [ ] Emoji keyboard
- [ ] Clipboard manager integration

### v1.2.0
- [ ] Word suggestions/autocorrect
- [ ] Swipe typing
- [ ] Custom keyboard height
- [ ] Vibration feedback settings

### v2.0.0 (Major Release)
- [ ] Full customization system
- [ ] User-defined layouts
- [ ] Cloud sync for settings
- [ ] Multi-language support

## Links

- **Repository**: https://github.com/JDis03/darkkeyboard
- **Issues**: https://github.com/JDis03/darkkeyboard/issues
- **Releases**: https://github.com/JDis03/darkkeyboard/releases
