# DarkKeyboard SDD — Specification-Driven Development

## 1. Composing Word Lifecycle Spec

### State Machine
```
                  ┌─────────┐
                  │  EMPTY  │ ◄──── startInput / reset / commit
                  └────┬────┘
                       │ onCharacter(c)
                  ┌────▼────┐
             ┌───►│COMPOSING│
             │    └────┬────┘
             │         │ onBackspace (len > 1)
             │         ▼
             │    ┌─────────┐
             │    │COMPOSING│ (len - 1)
             │    └────┬────┘
             │         │ onBackspace (len == 1)
             │         ▼
             │    ┌─────────┐
             │    │COMPOSING│ (empty)
             │    └────┬────┘
             │         │ onBackspace (empty)
             │         ▼
             └─────────┘  Normal backspace
```

### Invariants
- **I1:** composingWord.isEmpty() ⟺ no composing span in IC
- **I2:** If composingWord.isNotEmpty(), the IC has an active composing region
- **I3:** expectedSelStart ∈ [-1, textLength] and tracks cursor position
- **I4:** After any IC operation, expectedSelStart must be recalculated
- **I5:** `onSpace(text)` ALWAYS receives composingWord OR last word from textBeforeCursor

### Contract: onCharacter(c)
```kotlin
// PRE: c is a letter (isLetter() == true)
// POST: composingWord ends with c
// POST: IC has composing text = composingWord (via setComposingText)
// POST: expectedSelStart updated
// RETURN: UpdateComposing(word) | CommitDirect(char)
```

### Contract: onSpace(textBefore, hint)
```kotlin
// PRE: composingWord may be empty (must use textBefore to extract word)
// POST: composingWord = ""
// POST: lastWordBeforeSpace = word (for backspace-on-space undo)
// POST: skipCurrentWord reset on Normal return
// RETURN: Corrected(original, corrected) | Normal
```

---

## 2. Cursor Tracking Spec (HeliBoard Pattern)

### State
```
expectedSelStart: Int  // -1 = unknown, otherwise predicted cursor position
expectedSelEnd: Int    // same as start unless selection exists
```

### Contract: onUpdateSelection(old, new, candStart, candEnd)
```kotlin
// R1: If new == expectedSel → belated (our own operation) → IGNORE
// R2: If old == expectedSel && new != old → EXTERNAL MOVE → reset composing
// R3: If new is between old and expected → intermediate Android frame → IGNORE  
// R4: Otherwise → recalculate expected, update suggestions
```

### Invariants
- **I6:** After any IC operation that changes cursor, expectedSelStart is updated
- **I7:** expectedSelStart == -1 only at startInput or after a failure
- **I8:** No call to setComposingRegion allowed (causes cursor jumps)

---

## 3. Autocorrect Decision Spec

### Pre-flight checks (any fails → no correction)
```
P1: !typed.isAllCaps()
P2: !typed.containsDigit()  
P3: !personalDict.contains(typed)
P4: !isMidSentenceProperNoun(typed, context)
P5: typed.length >= MIN_WORD_LENGTH (4)
P6: autocorrect.isEnabled && shouldAutocorrect
P7: !skipCurrentWord
```

### Hint-based correction (priority 1)
```
H1: hint != null
H2: hint != typed (different words)
H3: hintDist = damerauLevenshtein(typed, hint) ≤ 3
H4: firstLetterCompatible(typed, hint)
H5: prefixCompatible(typed, hint)  // ≥2 shared initial chars (≥3 for 6+ chars)
H6: !typedIsInDict  // don't correct valid dictionary words
H7: hintFreq >= MIN_HINT_FREQ (2000)
H8: pairKey not in rejectedPairs || sessionRejected
→ RETURN preserveCase(typed, hint)
```

### No fallback to edit distance (disabled — too aggressive)

### Contract: skipCurrentWord
```kotlin
// SET: onBackspace returns UndoCorrection → skipCurrentWord = true
// SET: onBackspace clears lastWordBeforeSpace → skipCurrentWord = true
// CHECK: onSpace reads skipCurrentWord → if true, return Normal and reset
// RESET: onSpace with skipCurrentWord=true → skipCurrentWord = false after return
// RESET: reset() → skipCurrentWord = false
// RESET: onCharacter with empty composingWord → skipCurrentWord = false
```

---

## 4. IC Operation Contracts

### Safe operations (always allowed)
```
commitText(text, cursor)     → +text.length to cursor, no composing change
sendKeyEvent(KEYCODE_DEL)    → -1 to cursor, deletes one char
finishComposingText()         → removes composing span, commits composing text
```

### Batch-required operations
```
deleteSurroundingText(N, 0) + setComposingText(text, 1)
  → MUST be wrapped in beginBatchEdit/endBatchEdit
  → prevents cursor jump during delete
```

### FORBIDDEN operations
```
setComposingRegion(start, end)  → CAUSES cursor jumps, NEVER use
```

### Contract: Every handleKey path
```kotlin
// PRE: ic != null
// POST: expectedSelStart reflects cursor after operation
// POST: If operation involves IC manipulation, it's wrapped in batchEdit
```

---

## 5. Suggestion Engine Spec

### Input
```kotlin
textBeforeCursor: String   // text before cursor (not including composing)
maxResults: Int            // max suggestions to return
```

### Pipeline phases
```
F1: Trie prefix lookup (completions)
    → words starting with partial, sorted by freq

F1b: Edit distance search (typo corrections) — only if F1 returns empty
    → damerauLevenshtein ≤ 2, first letter compat, length ±2

F2a: User frequency boost
    → user words starting with partial, ln(freq) × W_USER

F2b: Bigram boost
    → next-word predictions given previous word, × W_BIGRAM

F3: T5 re-ranking (if model available)
    → cosine_similarity(embed(context), embed(word))
    → top-3 by score
```

### Output
```kotlin
List<String>   // top-N suggestions (completions + corrections)
```

---

## 6. Test Specifications

### Unit tests required for each invariant

| Spec | Test Name | What It Verifies |
|------|-----------|-----------------|
| I1 | `composing_word_empty_means_no_ic_span` | composingWord.isEmpty() after finishComposingText |
| I2 | `composing_word_implies_ic_composing` | onCharacter sets composing text in IC |
| I3 | `expected_sel_tracks_cursor` | after char, expected = textBeforeCursor.length |
| R1 | `on_update_selection_belated_ignored` | newSel == expected → no reset |
| R2 | `on_update_selection_external_reset` | oldSel == expected && newSel != oldSel → reset |
| P1-P7 | `preflight_*_prevents_correction` | each guard blocks autocorrect |
| H1-H8 | `hint_*_filters_correction` | each check filters appropriately |
| H6 | `typed_in_dict_not_corrected` | dict word with different hint → Normal |
| F1b | `edit_distance_generates_typo_corrections` | "coasas" finds "cosas" as candidate |

---

## 7. Implementation Checklist

### Already implemented
- [x] Composing word lifecycle (I1, I2)
- [x] Cursor tracking (R1-R4, I6-I7)
- [x] Autocorrect pre-flight checks (P1-P7)
- [x] Hint-based correction (H1-H8)
- [x] skipCurrentWord contract
- [x] No setComposingRegion (I8)
- [x] Batch edits for delete+compose
- [x] Suggestion pipeline F1-F3
- [x] T5 re-ranker with SentencePiece

### Missing / needs improvement
- [ ] Cursor-position-aware composing (HeliBoard: isCursorFrontOrMiddleOfComposingWord)
- [ ] SetComposingText failure → reset (HeliBoard: mWordComposer.reset())
- [ ] Block on pending suggestions before commit
- [ ] SuggestionSpan on committed words
- [ ] Formal test for each invariant
