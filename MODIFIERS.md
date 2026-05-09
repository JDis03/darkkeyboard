# How Modifiers Work in DarkKeyboard

## Big Picture

```
User touches Ctrl, then V
        │
        ▼
SimpleKeyboardView.onTouchEvent()
        │
        ├─ ACTION_DOWN on Ctrl → modifierState.toggleCtrl()
        ├─ ACTION_DOWN on V   → handleKeyPress("V")
        └─ ACTION_UP          → handleKeyPress calls onKeyListener.onKey(118, shift, ctrl=true, alt, fn)
                                        │
                                        ▼
                              DarkIME2.handleKey(code=118, ctrl=true)
                                        │
                                        ├─ buildMetaState() → META_CTRL_ON
                                        ├─ sendModifierDown(KEYCODE_CTRL_LEFT)  ← if chording enabled
                                        ├─ sendKeyEvent(KEYCODE_V, meta)         ← DOWN + UP
                                        └─ sendModifierUp(KEYCODE_CTRL_LEFT)    ← if chording enabled
```

## Layer 1: ModifierState (the brain)

`ModifierState.kt` holds 4 `MutableStateFlow<Boolean>`: shift, ctrl, alt, fn.

```kotlin
class ModifierState {
    private val _shift = MutableStateFlow(false)
    val shift: StateFlow<Boolean> = _shift.asStateFlow()
    
    fun toggleShift() { _shift.value = !_shift.value }
    fun setCtrl(v: Boolean) { _ctrl.value = v }
    fun clearAll() { /* set all to false */ }
}
```

**Why StateFlow?** So DarkIME2 can reactively observe changes without callbacks:

```kotlin
// In DarkIME2.observeModifierFlows():
combine(modifierState.shift, modifierState.ctrl, modifierState.alt, modifierState.fn)
{ s, c, a, f -> updateModifierStatus(s, c, a, f) }.collect { }
```

This replaces the old `OnModifierChangeListener` callback. Flow is lifecycle-safe (cancelled in `onDestroy()` via `imeScope.cancel()`).

## Layer 2: SimpleKeyboardView (touch → state)

When a finger touches a modifier key, `handleKeyPress` toggles the state:

```kotlin
Key.CODE_CTRL_LEFT -> { modifierState.toggleCtrl(); invalidate() }
Key.CODE_SHIFT    -> { modifierState.toggleShift(); invalidate() }
```

When a finger touches a **regular key** (letter, number, punctuation), two things happen:

### A. Code derivation

Many XML layout keys don't have `android:codes=""` — their code is `0`. The view derives the actual code from the label:

```kotlin
val codeToSend = if (key.code == 0 && label != null && label.isNotEmpty()) {
    label[0].code  // 'v' → 118, 'c' → 99
} else {
    key.code
}
```

### B. Auto-release

Like Hacker's Keyboard, all sticky modifiers release when you press a non-modifier key:

```kotlin
if (!key.isSticky && !key.isModifier) {
    modifierState.clearAll()  // shift=false, ctrl=false, alt=false, fn=false
    invalidate()
}
```

This is why Ctrl+V works as "press Ctrl → press V → done" without needing to press Ctrl again.

## Layer 3: DarkIME2.handleKey (dispatch to app)

`handleKey` receives the key code with modifier flags. For **character keys with modifiers**:

```kotlin
// code in 'a'..'z' with Ctrl or Alt active:
val keycode = KeyEvent.KEYCODE_A + (code.toChar().lowercaseChar() - 'a')
sendModifiedKeyDownUp(keycode, shift, ctrl, alt, fn)
```

For **special keys** (Enter, Tab, Esc, arrows, F-keys):
```kotlin
sendModifiedKeyDownUp(KeyEvent.KEYCODE_ENTER, shift, ctrl, alt, fn)
```

## Layer 4: sendModifiedKeyDownUp (the actual key injection)

This method sends a complete physical keyboard sequence:

```kotlin
private fun sendModifiedKeyDownUp(key: Int, shift, ctrl, alt, fn) {
    val eventTime = System.currentTimeMillis()
    val meta = buildMetaState(shift, ctrl, alt, fn)

    // 1. Modifier DOWN (if chording enabled)
    sendModifierDown(ic, eventTime, ctrl, KeyEvent.KEYCODE_CTRL_LEFT)
    sendModifierDown(ic, eventTime, alt, KeyEvent.KEYCODE_ALT_LEFT)

    // 2. Target key DOWN + UP with meta state
    ic.sendKeyEvent(KeyEvent(eventTime, eventTime, DOWN, key, 0, meta))
    ic.sendKeyEvent(KeyEvent(eventTime, eventTime, UP, key, 0, meta))

    // 3. Modifier UP (if chording enabled)
    sendModifierUp(ic, eventTime, alt, KeyEvent.KEYCODE_ALT_LEFT)
    sendModifierUp(ic, eventTime, ctrl, KeyEvent.KEYCODE_CTRL_LEFT)
}
```

## The Chording Mechanism

**Two modes** controlled by `chording_ctrl_key` preference:

| Setting | What happens |
|---------|-------------|
| `"0"` (None) | Ctrl only goes as **meta state** inside the KeyEvent. Clean, works for most apps. |
| `"113"` (Left Ctrl) | Ctrl is sent as a **separate physical KeyEvent** before and after the target key. Required by remote desktop / terminal apps. |

### sendModifierDown / sendModifierUp

```kotlin
private fun sendModifierDown(ic, eventTime, active: Boolean, keycode: Int) {
    if (!active) return
    val chordingMode = prefs.getString("chording_ctrl_key", "0") != "0"
    if (chordingMode) {
        ic.sendKeyEvent(KeyEvent(eventTime, eventTime, DOWN, keycode, 0, 0))
    }
}
```

The modifier is sent with **meta=0** — it's a raw key press of `KEYCODE_CTRL_LEFT`, not a meta state annotation on another key. This is what remote desktop protocols need to see.

## buildMetaState

Builds the Android KeyEvent meta bitmask:

```kotlin
private fun buildMetaState(shift, ctrl, alt, fn): Int {
    var m = 0
    if (shift) m = m or META_SHIFT_ON or META_SHIFT_LEFT_ON
    if (ctrl)  m = m or META_CTRL_ON or META_CTRL_LEFT_ON
    if (alt)   m = m or META_ALT_ON or META_ALT_LEFT_ON
    return m
}
```

## When Things Go Wrong (Debug Checklist)

| Symptom | Check |
|---------|-------|
| Ctrl+C doesn't copy | Is `chording_ctrl_key` set to Left Ctrl? Does the target app handle KeyEvents? |
| Ctrl+Shift+V sends "V" | Is `hasModifiers` check working in handleKeyPress? Is code derived from label? |
| Modifiers stay stuck | Is `clearAll()` called for non-modifier keys? |
| Status bar not updating | Is `observeModifierFlows()` set up in `onCreateInputView`? |
| Flow crashes on destroy | Is `imeScope.cancel()` in `onDestroy`? |

## Key Files

| File | Role |
|------|------|
| `ModifierState.kt` | MutableStateFlow holder, toggle/clear logic |
| `SimpleKeyboardView.kt:handleKeyPress` | Touch → modifier toggles or key dispatch |
| `DarkIME2.kt:handleKey` | Key + modifiers → app injection |
| `DarkIME2.kt:sendModifiedKeyDownUp` | Physical keyboard sequence injection |
| `DarkIME2.kt:observeModifierFlows` | Flow collection → status bar update |
| `SettingsActivity.kt:ChordingKeyDialog` | UI for chording_ctrl_key preference |
