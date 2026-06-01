## Update: Bug 3 Fixed ✅

**Date**: 2026-05-20
**Status**: Bug 3 resolved, others identified for future work

The undo duplication bug ("oosea", "eescribi", "ccorrige") has been **successfully fixed** by changing `deleteSurroundingText(r.corrected.length, 0)` to `deleteSurroundingText(r.corrected.length + 1, 0)` to account for the space after the corrected word.

**Tested on device**: Xiaomi 15T - confirmed working.

See "Results Summary" in tasks.md for details.

---

## Why

**Problem**: The desync fix and autocorrect system have multiple bugs that cause letter duplication. These bugs occur during normal typing, making the keyboard frustrating to use.

**Observed bugs**:

### Bug 1: Desync fix causes cascading letter duplication
- User types "borra" → backspace → composing empties
- User types "e" → desync fix commits "e" directly (bypass composing)
- User types "e" again → desync fix commits "e" **AGAIN** (still in desync state)
- Result: "borra**ee**" instead of "borrae"

**Real example from logs**:
```
[06:51:35.199] D/DarkIME: Desync: commit 'e' after word 'borra'
[06:51:36.502] D/DarkIME: Desync: commit 'e' after word 'borra'  ← DUPLICATE!
```

### Bug 2: Autocorrect with empty composing leaves partial duplicates
- User types "palabra" → deletes to "palab"
- Presses space → autocorrect detects "palab" → "palabra"
- System tries: `deleteSurroundingText(5, 0)` + `commitText("palabra ")`
- `deleteSurroundingText` only deletes 4 chars, leaves "p"
- Result: "**p**palabra" (partial duplicate)

**Real example from logs**:
```
[21:33:09.623] D/DarkIME: onSpace called: textBeforeCursor='...palabr', composingWord='', skip=false
[21:33:09.646] I/DarkIME: Autocorrect: 'palabr' → 'palabras'
```
(After this, user sees "ppalabras")

### Bug 3: Undo after autocorrect leaves first letter duplicated
- User types "**osea**"
- Presses space → autocorrects to "**oscar** "
- User presses backspace (undo)
- System does: `deleteSurroundingText(5, 0)` to delete "oscar"
- **Bug**: `deleteSurroundingText` only deletes "scar" (4 chars), leaves "**o**"
- Then: `setComposingText("osea", 1)` puts "osea" in composing
- Result: "**oo**sea" (first letter duplicated)

**Real examples from logs**:
```
[06:50:58.269] I/DarkIME: Undo: 'oscar' → 'osea'
```
User reported seeing "**oo**sea" after undo.

Same bug occurs with:
- "escribi" → "escribió" → undo → "**ee**scribi"
- "corrige" → "corrigió" → undo → "**cc**orrige"

### Bug 4: Fix for "ppalabra" gets bypassed by desync fix
- We implemented a fix to delete duplicate committed char (lines 644-655)
- But desync fix (lines 624-628) does `return` **before** reaching that code
- Result: Fix never executes when desync is active

**Code flow**:
```kotlin
// Line 623: Desync fix activates
if (existingWord.length >= 2) {
    ic.commitText(char, 1)
    return  ← BYPASSES the delete-duplicate fix below!
}

// Line 644: Delete duplicate fix (NEVER REACHED when desync active)
if (currentComposing.isEmpty() && textBefore.endsWith(char)) {
    ic.deleteSurroundingText(1, 0)
    ...
}
```

**Impact**:
- User types text: "El bug sigue **ee**scribi **oo**sea di espacio se **cc**orrige"
- Multiple letters duplicated throughout typing
- Extremely frustrating, breaks typing flow
- Happens in multiple apps (tested in Notes, Chrome, WhatsApp)

**Root causes**:
1. Desync fix doesn't transition OUT of desync state properly
2. Desync fix bypasses the delete-duplicate fix
3. `deleteSurroundingText` is unreliable (Android API bug)
4. Undo doesn't account for the space after correction

## What Changes

This is a **STUDY change** - we need to:

1. **Analyze all desync/autocorrect code paths** systematically
2. **Document the state machine** (when does desync activate/deactivate?)
3. **Create a comprehensive test suite** covering all edge cases
4. **Design a unified fix** that handles all bugs consistently

**Specific investigations needed**:

### Investigation 1: Desync state machine
- When does `existingWord.length >= 2` activate?
- When does it deactivate?
- Why does it trigger multiple times in a row?
- Should it transition to composing after first char?

### Investigation 2: deleteSurroundingText reliability
- How often does it fail? (add telemetry)
- Which apps/fields fail most?
- Can we work around it? (alternatives: sendKeyEvent, multiple attempts)

### Investigation 3: Fix execution order
- Why does desync fix return before delete-duplicate fix?
- Should delete-duplicate fix run BEFORE desync fix?
- How to unify both fixes?

### Investigation 4: Undo space handling
- Should undo delete word + space? (current: only word)
- Or should correction not add space? (current: adds space)
- HeliBoard pattern: what does it do?

## Capabilities

### New Capabilities
- `desync-state-machine`: Document the complete state machine for desync detection and recovery

### Modified Capabilities
- `composing-lifecycle`: Need to clarify when desync fix activates/deactivates
- `autocorrect-decision`: Need to handle unreliable `deleteSurroundingText`
- `cursor-tracking`: Desync affects cursor position expectations

## Impact

**Code affected**:
- `DarkIME2.kt` lines 613-630: Desync fix logic
- `DarkIME2.kt` lines 639-664: Delete duplicate fix
- `DarkIME2.kt` lines 439-473: Autocorrect without composing
- `DarkIME2.kt` lines 368-396: Undo correction handler

**Approach**:
- **Phase 1**: Study (this change) - analyze, document, design
- **Phase 2**: Implement comprehensive fix (separate change)
- **Phase 3**: Test extensively on multiple apps/fields

**Estimated effort**:
- Study phase: ~4 hours (code analysis + tests + design doc)
- Implementation: ~8 hours (unified fix + extensive testing)
- Total: ~12 hours (split across multiple sessions)

**Risks**:
- This is a complex, interconnected system
- Fixing one bug might expose others
- Need extensive device testing (can't rely on unit tests alone)
- May need to redesign desync fix entirely

**Dependencies**:
- Access to device for testing
- Logs from multiple apps/scenarios
- HeliBoard codebase for reference patterns
