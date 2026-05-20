# composing-lifecycle Specification

## Purpose

Manage the lifecycle of composing text in the IME, from user typing to space/enter/backspace events, ensuring the InputConnection state stays synchronized with the engine's internal state.

## Requirements

### Requirement: Composing word accumulation

The system SHALL accumulate typed letters into `composingWord` until a delimiter (space, enter, punctuation) is pressed.

#### Scenario: User types letters

- GIVEN the user is in a text field
- WHEN the user types "h", "o", "l", "a"
- THEN `composingWord` accumulates to "hola"
- AND InputConnection shows "hola" with composing span

#### Scenario: User types space

- GIVEN `composingWord = "hola"`
- WHEN the user presses space
- THEN `composingWord` is cleared to ""
- AND `lastWordBeforeSpace` is set to "hola"
- AND composing is committed as plain text
- AND a space is appended

### Requirement: Backspace handling

The system SHALL support three types of backspace: undo autocorrect, reduce composing, and normal delete.

#### Scenario: Undo autocorrect

- GIVEN the engine just applied a correction "palabr" → "palabras"
- WHEN the user presses backspace
- THEN the correction is undone
- AND `composingWord` is restored to "palabr"
- AND `skipCurrentWord` is set to true (prevent re-correction)

#### Scenario: Reduce composing

- GIVEN `composingWord = "hola"`
- WHEN the user presses backspace
- THEN `composingWord` becomes "hol"
- AND InputConnection updates composing span to "hol"

#### Scenario: Normal backspace

- GIVEN `composingWord` is empty
- WHEN the user presses backspace
- THEN normal backspace behavior (delete preceding char)

### Requirement: Composing finalization

The system SHALL finalize composing on space, enter, punctuation, or field loss.

#### Scenario: Space finalizes composing

- GIVEN `composingWord = "test"`
- WHEN the user presses space
- THEN composing is committed as plain text
- AND `composingWord` is cleared
- AND cursor moves after the space

#### Scenario: Enter finalizes composing

- GIVEN `composingWord = "test"`
- WHEN the user presses enter
- THEN composing is committed as plain text
- AND `composingWord` is cleared
- AND newline is inserted

### Requirement: Desync recovery

The system SHALL recover when `composingWord` is empty but text exists at cursor.

#### Scenario: Backspace deleted space (desync)

- GIVEN user typed "hello ", then backspace (space deleted)
- AND `composingWord` is now empty
- WHEN user types "w"
- THEN engine does NOT create duplicate "w"
- AND composing adopts existing word if ≥2 chars

#### Scenario: External edit cleared composing

- GIVEN `composingWord = "test"`
- WHEN external app clears the field
- THEN `composingWord` is cleared
- AND `expectedSelStart` is reset to -1

## Invariants

- **I1**: `composingWord` NEVER contains spaces or newlines
- **I2**: When `composingWord` is non-empty, InputConnection MUST have an active composing span
- **I3**: `lastWordBeforeSpace` is only set when space is pressed with active composing
- **I4**: `skipCurrentWord` is cleared on space (prevents permanent autocorrect block)

## Edge Cases

### Empty composing + char typed

- If `composingWord` is empty and user types a char:
  - If last committed char is the same → delete it first (fix "ppalabra" bug)
  - Otherwise, start new composing

### Backspace to empty + retype same char

- User types "palabra", backspace x7 → "p" committed
- User types "p" → desync fix (existingWord.length < 2) skips
- Normal flow: delete committed "p", start composing "p"

## Dependencies

- `AutocorrectEngine` (manages `composingWord`, `lastWordBeforeSpace`, `skipCurrentWord`)
- `InputConnection` (Android API for text manipulation)
- `Cursor Tracking` (spec: cursor-tracking) — `expectedSelStart` verification
