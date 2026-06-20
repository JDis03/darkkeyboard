# AGENTS.md

DarkKeyboard — Modern Android IME with Gboard proportions and HeliBoard autocorrect

## Quick Start

```bash
./init.sh                    # Verify environment + run tests
memory start-session         # Load project context (decisions, learnings, TODOs)
```

## Hard Constraints

- **One feature at a time** — Pick exactly ONE from `feature_list.json`
- **Verification required** — Tests must pass before claiming done
- **Always call `memory_start_session` first** — Loads project context
- **Always call `memory_end_session` before finishing** — Saves session state
- **Pass `cwd=` to all memory MCP tools** — Ensures correct project detection

## Session Lifecycle

Every session follows four phases with exact MCP tools:

### START Phase (first action)
- Call `memory_start_session()` — returns decisions, learnings, pending TODOs, last session
- Read the `last_progress_entry` to see what the previous session left
- Check for `warning` about unclosed sessions before proceeding
- Run `./init.sh` to verify baseline health

### SELECT Phase (choose ONE feature)
- Call `memory_get_todos()` — list pending tasks ordered by priority
- Or read `feature_list.json` — pick exactly one "not-started" or "in-progress" feature
- NEVER work on multiple features simultaneously

### EXECUTE Phase (implement)
- Call `memory_add_decision(decision, rationale)` when choosing between approaches
- Call `memory_add_learning(lesson, context)` when discovering something useful
- Call `memory_add_todo(task, priority)` for out-of-scope work found during implementation
- Call `memory_search(query)` to find existing code patterns before writing new code
- Update feature status to "in-progress" in feature_list.json at start

### WRAP UP Phase (mandatory before session close)
1. Run `./init.sh` to verify clean state
2. Update `feature_list.json` with new status and evidence
3. Call `memory_end_session(summary, completed_todos=[], verified="ran init.sh")`
4. Summary MUST be >20 chars and describe what was accomplished
5. Include `verified=` to record what tests/checks were run

## Topic Docs

- **Session Lifecycle** (`docs/SESSION_LIFECYCLE.md`) — Required reading before starting work
- **MCP Tools** (`docs/MCP_TOOLS.md`) — Reference when using memory tools
- **Architecture** (`ARCHITECTURE.md`) — Read when making design decisions
- **Project Status** (`PROJECT_STATUS.md`) — Current state and roadmap

## Working Rules

- **One feature at a time**: Pick exactly one unfinished feature from `feature_list.json`
- **Verification required**: Don't claim done without running verification commands
- **Update artifacts**: Before ending session, update `progress.md` and `feature_list.json`
- **Stay in scope**: Don't modify files unrelated to the current feature
- **Leave clean state**: Next session must be able to run `./init.sh` immediately

## Definition of Done

A feature is done only when ALL of the following are true:

- [ ] Target behavior is implemented
- [ ] Required verification actually ran (tests / lint / type-check)
- [ ] Evidence recorded in `feature_list.json` or `progress.md`
- [ ] Repository remains restartable from standard startup path

## End of Session

Before ending a session:

1. Update `progress.md` with current state
2. Update `feature_list.json` with new feature status
3. Call `memory_end_session()` with summary and verification evidence
4. Record any unresolved risks or blockers
5. Commit with descriptive message once work is in safe state
6. Leave repo clean enough for next session to run `./init.sh` immediately

## Verification Commands

```bash
# Full verification (recommended)
./init.sh
```

## Escalation

If you encounter:
- **Architecture decisions**: Consult project architecture docs if present, otherwise ask user
- **Unclear requirements**: Check product/requirements docs if present, otherwise ask user
- **Repeated test failures**: Update progress, flag for human review
- **Scope ambiguity**: Re-read `feature_list.json` for definition of done
