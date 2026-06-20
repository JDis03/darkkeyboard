# Session Lifecycle

Every session follows four phases in order. Do not skip phases.

## Phase 1: START (Load Context)

**First action:**
- Call `memory_start_session(cwd="/home/dark/Project/darkkeyboard")` to load project context
  - Returns: decisions, learnings, pending TODOs, last session summary
- Read the `last_progress_entry` to see what the previous session left
- Check for `warning` about unclosed sessions before proceeding
- Run `./init.sh` to verify baseline health

**If baseline verification fails:** Repair that first before adding new scope.

## Phase 2: SELECT (Pick One Feature)

**Choose exactly ONE feature:**
- Call `memory_get_todos(cwd="/home/dark/Project/darkkeyboard")` to list pending tasks ordered by priority
- Or read `feature_list.json` — pick exactly one "not-started" or "in-progress" feature
- **NEVER work on multiple features simultaneously**

**Stop before coding:** Confirm which feature you're working on.

## Phase 3: EXECUTE (Implement & Track)

**During implementation:**
- Call `memory_add_decision(decision, rationale, cwd="/home/dark/Project/darkkeyboard")` when choosing between approaches
- Call `memory_add_learning(lesson, context, cwd="/home/dark/Project/darkkeyboard")` when discovering something useful
- Call `memory_add_todo(task, priority, cwd="/home/dark/Project/darkkeyboard")` for out-of-scope work found during implementation
- Call `memory_search(query)` to find existing code patterns before writing new code
- Update feature status to "in-progress" in `feature_list.json` at start

## Phase 4: WRAP UP (Close Session Safely)

**Before finishing:**
1. Run `./init.sh` again to verify tests pass
2. Update `feature_list.json` with new status and evidence
3. Call `memory_end_session(summary, completed_todos=[], verified="ran init.sh", cwd="/home/dark/Project/darkkeyboard")`
   - Summary MUST be >20 chars and describe what was accomplished
   - Include `verified=` to record what tests/checks were run
4. Update `progress.md` with final status
5. Commit ONLY when verification passes

## Critical Rules

1. **One feature at a time** - Never work on multiple features
2. **Verify before declaring done** - Tests must pass
3. **Don't skip phases** - Each phase unlocks the next
4. **Leave clean state** - Next session runs `./init.sh` immediately
5. **Track everything** - Use memory tools to record work, not just code

## When This Workflow Matters Most

- Building new features
- Fixing bugs
- Refactoring
- Multi-session work
- Projects with teammates

If you complete a feature and want to start another, run `memory_end_session` first, then start a new session with `memory_start_session`.
