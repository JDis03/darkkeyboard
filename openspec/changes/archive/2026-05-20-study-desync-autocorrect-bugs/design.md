## Technical Approach

This is a **study and redesign** effort. We need to understand the current system deeply before implementing fixes.

### Current System Analysis

#### State Machine - Current Behavior

```
State 1: NORMAL COMPOSING
- User types letters
- composingWord accumulates: "h" → "ho" → "hol" → "hola"
- setComposingText() shows underlined text
- Transition to State 2 on space/punctuation

State 2: AFTER SPACE (composing finished)
- User pressed space
- composingWord cleared to ""
- lastWordBeforeSpace saved
- Text committed without underline
- Transition to State 1 on next letter

State 3: DESYNC (composing empty but word exists)
- composingWord = "" BUT textBefore ends with word (no space)
- Causes: backspace deleted space, external edit, finishComposingText called
- Current code: commits chars directly (lines 624-628)
- PROBLEM: No transition back to State 1! Stays in State 3 forever
- Result: Every char gets committed directly → cascading duplicates

State 4: COMPOSING BROKEN
- setComposingText or deleteSurroundingText failed 3+ times
- All composing disabled, direct commit only
- Transition: Never exits (per-field flag)
```

#### Bug 1 Root Cause: Desync Never Exits

**Current code** (lines 613-630):
```kotlin
val currentComposing = autocorrect.getComposing()
if (currentComposing.isEmpty() && 
    textBefore.isNotEmpty() && 
    !textBefore.endsWith(" ") &&
    !textBefore.endsWith("\n")) {
    
    val existingWord = textBefore.split(Regex("\\s+")).lastOrNull() ?: ""
    if (existingWord.length >= 2 && existingWord.all { it.isLetter() }) {
        ic.commitText(char, 1)  // ← Commits "e"
        expectedSelStart += 1
        Timber.d("Desync: commit '$char' after word '$existingWord'")
        updateSuggestions()
        return  // ← EXITS! Next char sees SAME desync state!
    }
}
```

**Problem flow**:
1. User types "borra", backspace → composingWord=""
2. textBefore="...borra" (no space at end)
3. User types "e":
   - existingWord="borra" (length 5 ≥ 2) ✓
   - Commits "e" directly
   - `return` exits
   - composingWord STILL empty!
4. textBefore="...borrae" (still no space!)
5. User types "e" again:
   - existingWord="borrae" (length 6 ≥ 2) ✓
   - Commits "e" directly AGAIN
   - composingWord STILL empty!
6. Loop continues: "borra**eee**scribi"

**Solution**: After committing in desync, we must **start composing** with that char, not just commit and exit.

#### Bug 2 Root Cause: Delete Duplicate Fix Unreachable

**Current code** (lines 644-655):
```kotlin
// Line 632: After desync check
when (val res = autocorrect.onCharacter(char[0])) {
    is UpdateComposing -> {
        // Line 644: Delete duplicate fix
        if (currentComposing.isEmpty() && textBefore.endsWith(char)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.setComposingText(res.composing, 1)
            ic.endBatchEdit()
        }
    }
}
```

**Problem**: This code is **after** the desync fix which does `return`. So when desync is active (which is EXACTLY when we need this fix), it never executes!

**Solution**: Move the delete-duplicate logic BEFORE desync check, or integrate it INTO desync check.

#### Bug 3 Root Cause: deleteSurroundingText Unreliable

**Affected code** (lines 439-473):
```kotlin
} else {
    // Autocorrect without composing
    val textBeforeDelete = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
    
    if (textBeforeDelete.endsWith(result.original)) {
        ic.deleteSurroundingText(result.original.length, 0)
        val textAfterDelete = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
        val actuallyDeleted = textBeforeDelete.length - textAfterDelete.length
        
        if (actuallyDeleted == result.original.length) {
            ic.commitText("${result.corrected} ", 1)
        } else {
            // Fallback
            ic.commitText(" ${result.corrected} ", 1)
            composingBroken = true
        }
    }
}
```

**Current status**: Fix implemented but needs testing on device.

**Problem**: Even with verification, we can't FIX the Android API bug. Best we can do is detect and fallback.

#### Bug 3 Root Cause: Undo Deletes Word Only, Not Space

**Problem discovered**: 
- User types "congele" → space → autocorrects to "congreso "
- Text: "...congreso " (cursor AFTER the space)
- User presses backspace (undo)
- Code did: `deleteSurroundingText(8, 0)` to delete "congreso"
- But cursor is AFTER space, so it deletes: " congres" (space + 7 letters)
- Leaves: "c" (first letter remains!)
- Then: `setComposingText("congele", 1)`
- Result: "**c**congele" (first letter duplicated)

**Original code** (lines 374-391):
```kotlin
val suffix = "${r.corrected} "  // ← Checks for space
if (textBefore.endsWith(suffix)) {
    ic.beginBatchEdit()
    if (ic.deleteSurroundingText(r.corrected.length, 0)) {  // ← BUG: Only deletes word!
        ic.setComposingText(r.original, 1)
        ...
    }
    ic.endBatchEdit()
}
```

**Problem**: Checks that space exists in text, but only deletes word length, not word + space.

**Solution implemented**:
```kotlin
// Delete word + space (cursor is AFTER the space)
ic.deleteSurroundingText(r.corrected.length + 1, 0)

// Verify it actually deleted everything
val textAfter = ic.getTextBeforeCursor(100, 0)?.toString() ?: ""
val actuallyDeleted = textBeforeUndo.length - textAfter.length
val expectedToDelete = r.corrected.length + 1  // word + space

if (actuallyDeleted == expectedToDelete) {
    // Success!
    ic.setComposingText(r.original, 1)
} else {
    // Fallback: log warning and compose anyway
}
```

**Status**: ✅ **FIXED** - Tested on device, "ccongele" bug resolved.

---

## Proposed Solution: Unified Desync Fix

### Design Goals

1. **Single responsibility**: Desync fix should ONLY handle first char after desync, then transition to composing
2. **No bypassing**: Delete-duplicate logic must run for ALL first chars when composing is empty
3. **Clear state transitions**: Document when/why each state activates
4. **Testable**: Each path must have unit tests

### New Desync Fix Design

```kotlin
// ── Phase 1: Handle duplicate committed char (ALWAYS runs first) ────
val currentComposing = autocorrect.getComposing()
var charToProcess = char

if (currentComposing.isEmpty() && textBefore.endsWith(char)) {
    // User is retyping a char that was just committed (e.g., after finishComposingText)
    // Delete the old committed char before starting new composing
    ic.beginBatchEdit()
    val deleted = ic.deleteSurroundingText(1, 0)
    ic.endBatchEdit()
    
    if (deleted) {
        Timber.d("Deleted duplicate committed char '$char'")
    } else {
        Timber.w("Failed to delete duplicate char '$char'")
        // Continue anyway - composing will overlap
    }
}

// ── Phase 2: Check if we're in desync state ────────────────────────
val isDesync = currentComposing.isEmpty() && 
               textBefore.isNotEmpty() && 
               !textBefore.endsWith(" ") &&
               !textBefore.endsWith("\n")

if (isDesync) {
    val existingWord = textBefore.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
    
    if (existingWord.length >= 2 && existingWord.all { it.isLetter() }) {
        // Desync detected: there's a multi-char word without composing
        // SOLUTION: Start composing with this char instead of just committing
        Timber.d("Desync: starting composing with '$char' after word '$existingWord'")
        
        // Adopt the existing word + new char into composing
        // This transitions us OUT of desync state
        val newComposing = char  // Just the new char, not existingWord
        // (existingWord stays as committed text)
        
        // Fall through to onCharacter below - don't return!
    }
}

// ── Phase 3: Normal character processing ────────────────────────────
when (val res = autocorrect.onCharacter(charToProcess[0])) {
    is UpdateComposing -> {
        if (composingBroken) {
            ic.commitText(charToProcess, 1)
            expectedSelStart += 1
        } else {
            val ok = ic.setComposingText(res.composing, 1)
            if (!ok) {
                composingFailCount++
                if (composingFailCount >= maxComposingFails) {
                    composingBroken = true
                    ic.commitText(charToProcess, 1)
                }
            } else {
                composingFailCount = 0
            }
        }
        expectedSelStart = ic.getTextBeforeCursor(100, 0)?.length ?: expectedSelStart
    }
}
```

### Key Changes

1. **Delete duplicate runs FIRST** (Phase 1) - always executes, no matter what
2. **Desync detection** (Phase 2) - logs but doesn't commit directly
3. **Fall through to onCharacter** - no early `return`, let composing start normally
4. **Result**: After first char in desync, we're back in normal composing state

### State Machine - Proposed Behavior

```
State 1: NORMAL COMPOSING
- composingWord accumulates
- setComposingText() active
- On space → State 2

State 2: AFTER SPACE
- composingWord = ""
- On next letter → check for duplicate (Phase 1)
- If duplicate → delete it
- Then → State 1

State 3: DESYNC (temporary)
- composingWord = "" but word exists
- On next letter:
  1. Check duplicate (Phase 1) - delete if exists
  2. Detect desync (Phase 2) - log it
  3. Start composing (Phase 3) - transition to State 1
- REMOVED: No more "commit and return" trap!

State 4: COMPOSING BROKEN
- Composing disabled
- Direct commit only
- Never exits (per-field)
```

---

## Data Flow Examples

### Example 1: Normal typing after space

```
User: "hola" → space → "m"
1. composingWord="" (after space)
2. textBefore="hola " (ends with space)
3. Phase 1: textBefore.endsWith("m") = false → skip
4. Phase 2: textBefore.endsWith(" ") = true → NOT desync
5. Phase 3: onCharacter('m') → composingWord="m"
Result: "hola m" (composing active) ✓
```

### Example 2: Backspace to empty, retype same char (Bug 1 fix)

```
User: "palabra" → backspace x7 → "p"
1. After backspace x7: composingWord="" (finishComposingText committed "p")
2. textBefore="...p" (committed)
3. User types "p":
   Phase 1: textBefore.endsWith("p") = true
     → deleteSurroundingText(1, 0) deletes committed "p"
   Phase 2: textBefore (now "...") doesn't end with word → NOT desync
   Phase 3: onCharacter('p') → composingWord="p"
Result: "...p" (composing, no duplicate) ✓
```

### Example 3: Desync after backspace deletes space (Bug 1 fix)

```
User: "hola " → backspace → "m"
1. After backspace: composingWord="" (space deleted)
2. textBefore="...hola" (no space at end)
3. User types "m":
   Phase 1: textBefore.endsWith("m") = false → skip
   Phase 2: existingWord="hola" (length ≥ 2) → DESYNC detected
     Log: "Desync: starting composing with 'm' after word 'hola'"
     → DON'T return! Fall through to Phase 3
   Phase 3: onCharacter('m') → composingWord="m"
Result: "...holam" (composing active on "m") ✓
```

### Example 4: Multiple chars in desync (Bug 1 main issue)

```
User: "borra" → backspace deletes space → "e" → "e"
Char 1 'e':
1. textBefore="...borra"
2. Phase 1: skip (no duplicate)
3. Phase 2: desync detected, log, DON'T return
4. Phase 3: composingWord="e"
5. textBefore="...borrae" (composing "e")

Char 2 'e':
1. textBefore="...borra" (before composing)
2. Phase 1: textBefore.endsWith("e") = false (ends with "borra")
3. Phase 2: NOT desync anymore! (composingWord="e" not empty)
4. Phase 3: composingWord="ee"
Result: "...borraee" (composing, NOT "borraeee") ✓
```

---

## Testing Strategy

### Unit Tests (New)

```kotlin
@Test
fun `desync - multiple chars after backspace dont duplicate`() {
    // Bug 1 regression test
    ime.type("borra ")
    ime.backspace()  // Delete space → desync
    ime.type("e")
    ime.type("e")
    ime.type("s")
    
    assertEquals("borraees", ime.getComposingText())
    assertNotEquals("borraeees", ime.getText())  // No cascading duplicates
}

@Test
fun `backspace to empty then retype same char no duplicate`() {
    // Bug 2 regression test
    ime.type("palabra")
    repeat(7) { ime.backspace() }  // → "p" committed
    ime.type("p")
    
    assertEquals("p", ime.getComposingText())
    assertEquals("...p", ime.getText())  // Only one "p"
}

@Test
fun `autocorrect without composing verifies delete`() {
    // Bug 3 regression test
    val ic = MockInputConnection("palab")
    ic.setDeleteBehavior(actualDeleted = 3)  // Only deletes 3 of 5
    
    ime.space()  // Triggers autocorrect "palab" → "palabra"
    
    verify(ic).commitText(" palabra ", 1)  // Fallback with leading space
    assertTrue(ime.composingBroken)
}

@Test
fun `undo correction deletes word and space`() {
    // Bug 4 regression test
    ime.type("palabr")
    ime.space()  // Autocorrects to "palabras "
    ime.backspace()  // Undo
    
    assertEquals("palabr", ime.getComposingText())
    assertEquals("...palabr", ime.getText())  // No extra space
}
```

### Integration Tests (Device)

1. **Cascading duplication** (Bug 1):
   - Type "hola ", backspace, type "mundo"
   - Verify: "holamundo" not "holam mmundo"

2. **Retype after backspace** (Bug 2):
   - Type "test", backspace x4, type "test" again
   - Verify: "test" not "ttest"

3. **Autocorrect in Chrome** (Bug 3):
   - Type "palab", space in Chrome search
   - Verify: "palabra" or "palab palabra" (fallback), NOT "ppalabra"

4. **Undo in WhatsApp** (Bug 4):
   - Type "palabr", space (autocorrects), backspace
   - Verify: "palabr" not " palabr"

---

## Performance Impact

**Added operations per character**:
- Phase 1: +1 `getTextBeforeCursor()`, +1 `deleteSurroundingText()` (only when duplicate detected, rare)
- Phase 2: +1 string split, +1 regex match (only when composing empty, ~10% of chars)
- Phase 3: No change

**Estimated cost**: < 0.5ms per character (negligible)

**Memory**: No additional allocations in hot path

---

## Risks

**Risk 1**: Delete duplicate might fail in some fields
- **Mitigation**: Log failures, continue with overlap (better than crash)

**Risk 2**: Desync detection regex might be expensive
- **Mitigation**: Only runs when composingWord empty (~10% of time)

**Risk 3**: State machine might still have edge cases
- **Mitigation**: Extensive device testing, comprehensive logs

**Risk 4**: HeliBoard might have different approach
- **Mitigation**: Review HeliBoard code before implementing

---

## Implementation Plan

### Phase 1: Code Changes (2 hours)
1. Implement new desync fix (lines 613-664)
2. Fix undo space handling (line 378)
3. Add comprehensive logging

### Phase 2: Unit Tests (2 hours)
1. Write 4 new regression tests
2. Verify all 161 existing tests still pass
3. Add edge case tests

### Phase 3: Device Testing (2 hours)
1. Test in 5+ apps (Chrome, WhatsApp, Notes, Gmail, Telegram)
2. Collect logs from each session
3. Verify no new bugs introduced

### Phase 4: Iteration (2 hours)
1. Analyze device test results
2. Fix any new issues found
3. Re-test

**Total estimate**: ~8 hours

---

## Alternatives Considered

### Alt 1: Disable desync fix entirely
**Rejected**: Would break typing after backspace deletes space. Need desync detection.

### Alt 2: Use setComposingRegion instead of commitText in desync
**Rejected**: setComposingRegion causes cursor jumps (documented constraint).

### Alt 3: Always start composing on any letter, never commit directly
**Rejected**: Breaks terminal mode and special cases where composing not supported.

### Alt 4: Multiple deleteSurroundingText attempts (retry loop)
**Rejected**: Would be slow, visible flickering, still might fail.

---

## HeliBoard Reference

Need to review HeliBoard's handling of:
1. Desync detection (what triggers it?)
2. Transition back to composing (how does it exit desync?)
3. deleteSurroundingText failures (does it have fallback?)
4. Undo space handling (does it delete space too?)

**Action**: Study HeliBoard `/home/dark/Project/heliboard` before implementing.
