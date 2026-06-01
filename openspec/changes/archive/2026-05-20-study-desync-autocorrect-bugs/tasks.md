## Phase 1: Study HeliBoard Reference

### Task 1.1: Review HeliBoard desync handling
- **Location**: `/home/dark/Project/heliboard/app/src/main/java/helioboard/InputLogic.kt`
- **Action**:
  1. Search for "desync" or similar patterns
  2. Find how HeliBoard detects composing is out of sync
  3. Document how it transitions back to composing
  4. Check if it has the same cascading char bug
- **Verification**: Notes in design.md with HeliBoard patterns
- **Estimation**: 30 minutes
- **Status**: ⏳ TODO

### Task 1.2: Review HeliBoard deleteSurroundingText handling
- **Location**: Same file
- **Action**:
  1. Find autocorrect code path
  2. Check if it verifies deleteSurroundingText success
  3. Document fallback strategy (if any)
- **Verification**: Notes in design.md
- **Estimation**: 20 minutes
- **Status**: ⏳ TODO

### Task 1.3: Review HeliBoard undo handling
- **Location**: Same file
- **Action**:
  1. Find undo/backspace handler
  2. Check if it deletes word only or word+space
  3. Document the pattern
- **Verification**: Notes in design.md
- **Estimation**: 15 minutes
- **Status**: ⏳ TODO

---

## Phase 2: Implementation

### Task 2.1: Implement unified desync fix
- **File**: `app/src/main/java/org/dark/keyboard/DarkIME2.kt`
- **Lines**: 613-664 (replace entire block)
- **Action**:
  1. Implement Phase 1: Delete duplicate (always runs first)
  2. Implement Phase 2: Desync detection (log only, no return)
  3. Implement Phase 3: Normal onCharacter (fall through)
  4. Remove old desync fix with `return`
  5. Keep comprehensive logging
- **Code**:
  ```kotlin
  // ── Phase 1: Delete duplicate committed char ────
  val currentComposing = autocorrect.getComposing()
  
  if (currentComposing.isEmpty() && textBefore.endsWith(char)) {
      ic.beginBatchEdit()
      val deleted = ic.deleteSurroundingText(1, 0)
      ic.endBatchEdit()
      
      if (deleted) {
          Timber.d("Deleted duplicate committed char '$char'")
      } else {
          Timber.w("Failed to delete duplicate char '$char'")
      }
  }
  
  // ── Phase 2: Desync detection ────
  val isDesync = currentComposing.isEmpty() && 
                 textBefore.isNotEmpty() && 
                 !textBefore.endsWith(" ") &&
                 !textBefore.endsWith("\n")
  
  if (isDesync) {
      val existingWord = textBefore.trimEnd().split(Regex("\\s+")).lastOrNull() ?: ""
      
      if (existingWord.length >= 2 && existingWord.all { it.isLetter() }) {
          Timber.d("Desync: starting composing with '$char' after word '$existingWord'")
          // Don't return! Fall through to Phase 3
      }
  }
  
  // ── Phase 3: Normal character processing ────
  when (val res = autocorrect.onCharacter(char[0])) {
      // ... existing code ...
  }
  ```
- **Verification**: Code compiles
- **Estimation**: 30 minutes
- **Status**: ⏳ TODO

### Task 2.2: Fix undo space handling
- **File**: `app/src/main/java/org/dark/keyboard/DarkIME2.kt`
- **Lines**: ~378
- **Action**:
  1. Change `ic.deleteSurroundingText(r.corrected.length, 0)`
  2. To `ic.deleteSurroundingText(r.corrected.length + 1, 0)`
  3. Add verification: check actuallyDeleted == expectedToDelete
  4. Add fallback if verification fails
  5. Add detailed logging
- **Verification**: Code compiles, tests pass
- **Estimation**: 20 minutes (actual)
- **Status**: ✅ DONE

### Task 2.3: Remove old delete-duplicate code
- **File**: `app/src/main/java/org/dark/keyboard/DarkIME2.kt`
- **Lines**: ~644-655
- **Action**:
  1. Remove the old delete-duplicate fix (now in Phase 1)
  2. Keep only the setComposingText verification logic
  3. Update comments
- **Verification**: Code compiles, no duplicate logic
- **Estimation**: 10 minutes
- **Status**: ⏳ TODO

### Task 2.4: Compile and verify no syntax errors
- **Command**: `./gradlew compileDebugKotlin`
- **Action**: Compile project
- **Verification**: BUILD SUCCESSFUL, no errors
- **Estimation**: 2 minutes
- **Status**: ✅ DONE

---

## Phase 3: Unit Testing

### Task 3.1: Test cascading char duplication (Bug 1)
- **File**: Create `app/src/test/java/org/dark/keyboard/DesyncBugTest.kt`
- **Action**:
  ```kotlin
  @Test
  fun `desync - multiple chars after backspace dont duplicate`() {
      val ime = createIME()
      ime.type("borra ")
      ime.backspace()  // Delete space → desync
      ime.type("e")
      ime.type("e")
      ime.type("s")
      
      val text = ime.getText()
      assertEquals("borraees", text)
      assertFalse(text.contains("borraeees"), "Should not have cascading duplicates")
  }
  ```
- **Verification**: Test passes
- **Estimation**: 20 minutes
- **Status**: ⏳ TODO

### Task 3.2: Test retype same char after backspace (Bug 2)
- **File**: Same file
- **Action**:
  ```kotlin
  @Test
  fun `backspace to empty then retype same char no duplicate`() {
      val ime = createIME()
      ime.type("palabra")
      repeat(7) { ime.backspace() }  // → "p" committed
      ime.type("p")
      
      assertEquals("p", ime.getComposingText())
      assertEquals("p", ime.getText())  // Only one "p"
  }
  ```
- **Verification**: Test passes
- **Estimation**: 15 minutes
- **Status**: ⏳ TODO

### Task 3.3: Test autocorrect delete verification (Bug 3)
- **File**: Same file
- **Action**:
  ```kotlin
  @Test
  fun `autocorrect without composing verifies delete`() {
      val ic = MockInputConnection()
      ic.setText("palab")
      ic.setDeleteBehavior(actualDeleted = 3)  // Fails to delete all 5
      
      val ime = createIME(ic)
      ime.space()  // Autocorrect "palab" → "palabra"
      
      val text = ic.getText()
      assertTrue(text.contains(" palabra "), "Should use fallback with leading space")
      assertFalse(text.contains("ppalabra"), "Should not have partial duplicate")
  }
  ```
- **Verification**: Test passes
- **Estimation**: 25 minutes
- **Status**: ⏳ TODO

### Task 3.4: Test undo deletes word and space (Bug 4)
- **File**: Same file
- **Action**:
  ```kotlin
  @Test
  fun `undo correction deletes word and space`() {
      val ime = createIME()
      ime.type("palabr")
      ime.space()  // Autocorrects to "palabras "
      
      val textBefore = ime.getText()
      assertTrue(textBefore.endsWith("palabras "))
      
      ime.backspace()  // Undo
      
      val textAfter = ime.getText()
      assertEquals("palabr", ime.getComposingText())
      assertFalse(textAfter.endsWith(" palabr"), "Should not have leading space")
  }
  ```
- **Verification**: Test passes
- **Estimation**: 20 minutes
- **Status**: ⏳ TODO

### Task 3.5: Run full test suite
- **Command**: `./gradlew testDebugUnitTest`
- **Action**:
  1. Run all unit tests
  2. Verify all 161 existing tests still pass
  3. Verify all 4 new tests pass
- **Verification**: BUILD SUCCESSFUL, 165 tests passed
- **Estimation**: 5 minutes
- **Status**: ⏳ TODO

---

## Phase 4: Device Testing

### Task 4.1: Build APK
- **Command**: `./gradlew assembleDebug`
- **Action**: Build APK with all fixes
- **Verification**: APK generated, ~44MB
- **Estimation**: 2 minutes
- **Status**: ✅ DONE

### Task 4.2: Test cascading duplication in Notes app
- **Device**: Xiaomi 15T
- **App**: Notes (native EditText)
- **Scenario**:
  1. Type "hola "
  2. Backspace (delete space)
  3. Type "mundo" letter by letter
- **Expected**:
  - ✅ "holamundo" (composing on "mundo")
  - ❌ NOT "holam mmundo" or "holammundo"
- **Verification**: No cascading duplicates
- **Estimation**: 5 minutes
- **Status**: ⏳ TODO (requires device)

### Task 4.3: Test retype same char in WhatsApp
- **Device**: Xiaomi 15T
- **App**: WhatsApp
- **Scenario**:
  1. Type "test"
  2. Backspace x4 (to empty)
  3. Type "test" again
- **Expected**:
  - ✅ "test" (no duplicate)
  - ❌ NOT "ttest"
- **Verification**: No duplicate first char
- **Estimation**: 5 minutes
- **Status**: ⏳ TODO (requires device)

### Task 4.4: Test autocorrect in Chrome search
- **Device**: Xiaomi 15T
- **App**: Chrome browser
- **Scenario**:
  1. Open Google search
  2. Type "palab"
  3. Press space
- **Expected (two outcomes possible)**:
  - ✅ BEST: "palabra" (deleteSurroundingText worked)
  - ✅ OK: "palab palabra" (fallback with leading space, full duplicate)
  - ❌ NOT: "ppalabra" (partial duplicate)
- **Verification**: No partial duplicates
- **Estimation**: 5 minutes
- **Status**: ⏳ TODO (requires device)

### Task 4.5: Test undo in Telegram
- **Device**: Xiaomi 15T
- **App**: Telegram
- **Scenario**:
  1. Type "palabr"
  2. Press space (autocorrects to "palabras")
  3. Press backspace immediately (undo)
- **Expected**:
  - ✅ "palabr" in composing
  - ❌ NOT " palabr" with leading space
- **Verification**: No extra space
- **Estimation**: 5 minutes
- **Status**: ⏳ TODO (requires device)

### Task 4.6: Extended typing test in Gmail
- **Device**: Xiaomi 15T
- **App**: Gmail compose
- **Scenario**:
  1. Type a full paragraph (50+ words)
  2. Include backspaces, corrections, undo operations
  3. Type naturally, fast
- **Expected**:
  - ✅ No duplicate letters appearing
  - ✅ Autocorrect works smoothly
  - ✅ No crashes
- **Verification**: Natural typing experience
- **Estimation**: 10 minutes
- **Status**: ⏳ TODO (requires device)

### Task 4.7: Extract and analyze device logs
- **Command**: `adb pull /data/data/org.dark.keyboard/cache/logs/darkime_*.log ./logs/`
- **Action**:
  1. Extract logs from all test sessions
  2. Search for "Deleted duplicate committed char" (should appear when needed)
  3. Search for "Desync: starting composing" (should appear in desync cases)
  4. Search for "deleteSurroundingText failed" (should appear in Chrome/WebView)
  5. Count occurrences of each
- **Verification**:
  - Phase 1 logs appear when retyping same char
  - Phase 2 logs appear when desync detected
  - No error logs about crashes
- **Estimation**: 15 minutes
- **Status**: ⏳ TODO (requires device)

---

## Phase 5: Documentation

### Task 5.1: Update autocorrect-decision spec
- **File**: `openspec/specs/autocorrect-decision/spec.md`
- **Action**:
  1. Add new requirement: "System must verify deleteSurroundingText success"
  2. Add scenario: "Autocorrect without composing when deleteSurroundingText fails"
  3. Update invariants: "No partial duplicates (e.g., 'ppalabra')"
- **Verification**: Spec reflects changes
- **Estimation**: 15 minutes
- **Status**: ⏳ TODO

### Task 5.2: Update composing-lifecycle spec
- **File**: `openspec/specs/composing-lifecycle/spec.md`
- **Action**:
  1. Add new requirement: "System must transition out of desync after first char"
  2. Add scenario: "Desync after backspace deletes space"
  3. Update state machine diagram
- **Verification**: Spec reflects new desync behavior
- **Estimation**: 15 minutes
- **Status**: ⏳ TODO

### Task 5.3: Create spec deltas
- **Files**:
  - `openspec/changes/study-desync-autocorrect-bugs/specs/autocorrect-decision/spec.md`
  - `openspec/changes/study-desync-autocorrect-bugs/specs/composing-lifecycle/spec.md`
- **Action**:
  1. Show what changed with `++` and `--` markers
  2. Document new requirements
  3. Document removed/changed requirements
- **Verification**: Deltas clearly show changes
- **Estimation**: 20 minutes
- **Status**: ⏳ TODO

### Task 5.4: Update skill file
- **File**: `~/.agents/skills/darkkeyboard/SKILL.md` (if exists)
- **Action**:
  1. Move bugs to "Done" section
  2. Update test count (161 → 165)
  3. Add note about unified desync fix
- **Verification**: Skill file current
- **Estimation**: 10 minutes
- **Status**: ⏳ TODO

### Task 5.5: Archive change
- **Command**: `npx openspec archive study-desync-autocorrect-bugs --reason "Implemented unified desync fix, all bugs resolved"`
- **Action**:
  1. Run archive command
  2. Verify moved to archive folder
  3. Verify specs updated
- **Verification**: Change archived
- **Estimation**: 2 minutes
- **Status**: ⏳ TODO (after all verification complete)

---

## Summary

| Phase | Tasks | Time Estimate | Can Do Without Device? |
|-------|-------|---------------|------------------------|
| Phase 1: Study HeliBoard | 3 | ~1 hour | ✅ Yes |
| Phase 2: Implementation | 4 | ~45 min | ✅ Yes |
| Phase 3: Unit Testing | 5 | ~1.5 hours | ✅ Yes |
| Phase 4: Device Testing | 7 | ~45 min | ❌ No (requires device) |
| Phase 5: Documentation | 5 | ~1 hour | ✅ Yes |
| **TOTAL** | **24 tasks** | **~5 hours** | **17 tasks (70%) without device** |

## Execution Order

1. **Session 1** (without device): Phase 1, 2, 3 (~3.5 hours)
   - Study HeliBoard
   - Implement fixes
   - Write and run unit tests
   - Build APK

2. **Session 2** (with device): Phase 4 (~45 min)
   - Install APK
   - Test in 5+ apps
   - Extract logs
   - Verify all bugs fixed

3. **Session 3** (without device): Phase 5 (~1 hour)
   - Update documentation
   - Create spec deltas
   - Archive change

## Success Criteria

- ✅ All 165 unit tests pass
- ✅ No "eescribi", "oosea", "ccorrige" style duplicates on device
- ✅ Autocorrect works in Chrome (or fallback gracefully)
- ✅ Undo doesn't leave extra space
- ✅ No crashes in any tested app
- ✅ Logs show correct phase transitions
- ✅ Documentation complete

## Rollback Plan

If device testing reveals critical issues:
1. Revert commits from Phase 2
2. Keep Phase 1 study notes
3. Redesign based on findings
4. Create new change proposal

---

## Results Summary

### What Was Fixed

**Bug 3: Undo after autocorrect leaving first letter duplicated** ✅ RESOLVED

**Root cause identified**:
- Cursor position after autocorrection is AFTER the space: "congreso |"
- `deleteSurroundingText(8, 0)` deletes 8 chars backwards from cursor
- Deletes: " congres" (space + 7 letters), leaves "c"
- Then `setComposingText("congele")` creates: "ccongele"

**Solution implemented**:
- Changed to `deleteSurroundingText(r.corrected.length + 1, 0)` to delete word + space
- Added verification: compare textBefore.length - textAfter.length vs expected
- Added fallback logging if verification fails
- Added detailed Timber logs for diagnostics

**Testing results**:
- ✅ Tested on device: Xiaomi 15T
- ✅ Bug reproduced: "congele" → "congreso" → backspace = "ccongele"
- ✅ After fix: "congele" → "congreso" → backspace = "congele" (correct!)
- ✅ Logs show: "Undo: 'congreso' → 'congele' (deleted 9 chars)"

### Bugs Not Fixed (Future Work)

**Bug 1: Desync fix causes cascading letter duplication**
- Status: NOT FIXED (requires unified desync redesign)
- See Phase 1-3 tasks for implementation plan
- Estimated effort: ~3 hours

**Bug 2: Autocorrect with empty composing leaves partial duplicates**
- Status: PARTIALLY FIXED
- Fix implemented: verification + fallback
- Needs device testing to confirm effectiveness

**Bug 4: Delete duplicate fix bypassed by desync fix**
- Status: NOT FIXED (requires Phase 2 unified desync implementation)
- Current workaround: delete-duplicate runs but desync still returns early

### Files Modified

1. `app/src/main/java/org/dark/keyboard/DarkIME2.kt`:
   - Lines 368-410: Undo handler with verification
   - Lines 439-473: Autocorrect without composing with verification

### Test Status

- Unit tests: All 161 existing tests pass
- New tests: Not written yet (Phase 3 blocked)
- Device testing: Manual testing confirms Bug 3 fixed

### Time Spent

- Analysis & documentation: ~2 hours
- Implementation (Bug 3): ~30 minutes
- Device testing: ~15 minutes
- Total: ~2.75 hours

### Next Steps

1. **Immediate**: Commit the undo fix
2. **Short term**: Continue monitoring for other undo edge cases
3. **Long term**: Implement unified desync fix (Phase 1-3 from original plan)
