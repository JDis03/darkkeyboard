# composing-lifecycle Specification (DELTA)

This shows the requirements that **changed** due to study-desync-autocorrect-bugs.

## ADDED Requirements

### Requirement: Undo correction verification

The system SHALL verify deleteSurroundingText success when undoing corrections and delete word + trailing space.

#### Scenario: Undo deletes corrected word and space


- GIVEN user typed "congele" and autocorrect changed it to "congreso "
- AND cursor is positioned after the space
- WHEN user presses backspace
- THEN system SHALL delete the corrected word AND the trailing space (word.length + 1 chars)
- AND system SHALL restore original word "congele" in composing
- AND NO duplicate letters shall appear (e.g., NOT "ccongele")

#### Scenario: Undo with deleteSurroundingText failure

- GIVEN user triggered undo of correction
- WHEN `deleteSurroundingText` fails to delete all characters
- THEN system SHALL log warning with expected vs actual deleted count
- AND system SHALL attempt fallback: compose original word over remaining text
- AND system SHALL NOT crash or enter invalid state
