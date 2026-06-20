# AGENTS.md

Project harness for reliable agent-assisted development in a java-gradle codebase.

## Startup Workflow

Before writing code:

1. **Confirm working directory** with `pwd`
2. **Read this file** completely
3. **Read project docs if present** (`docs/ARCHITECTURE.md`, `docs/PRODUCT.md`, README, or equivalent)
4. **Run `./init.sh`** to verify environment is healthy
5. **Read `feature_list.json`** to see current feature state
6. **Review recent commits** with `git log --oneline -5`

If baseline verification is failing, repair that first before adding new scope.

## Working Rules

- **One feature at a time**: Pick exactly one unfinished feature from `feature_list.json`
- **Verification required**: Don't claim done without running verification commands
- **Update artifacts**: Before ending session, update `progress.md` and `feature_list.json`
- **Stay in scope**: Don't modify files unrelated to the current feature
- **Leave clean state**: Next session must be able to run `./init.sh` immediately

## Required Artifacts

- `feature_list.json` — Feature state tracker (source of truth)
- `progress.md` — Session continuity log
- `init.sh` — Standard startup and verification path
- `session-handoff.md` — Optional, for larger sessions

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
3. Record any unresolved risks or blockers
4. Commit with descriptive message once work is in safe state
5. Leave repo clean enough for next session to run `./init.sh` immediately

## MCP Tools — Memory & Context

When working on this project in OpenCode, use these tools to track decisions and find patterns:

| Tool | Phase | Purpose |
|------|-------|---------|
| `memory_start_session` | START | Get full project context (decisions, learnings, TODOs, last session) |
| `memory_get_todos` | SELECT | List pending tasks by priority |
| `memory_add_decision` | EXECUTE | Record technical decisions (alternatives considered, rationale) |
| `memory_add_learning` | EXECUTE | Record lessons discovered (debugging insights, patterns found) |
| `memory_add_todo` | EXECUTE | Add out-of-scope work to backlog (priority: high/medium/low) |
| `memory_search` | EXECUTE | Find existing code patterns & decisions across projects |
| `memory_complete_todo` | WRAP UP | Mark completed tasks before ending session |
| `memory_end_session` | WRAP UP | Close session with summary (min 20 chars describing what was done) |

**In OpenCode:** Press `ctrl+p` to view available tools, then select one above by name.

**Session workflow:**
```
1. START:  memory_start_session      ← load context
2. SELECT: memory_get_todos          ← pick ONE feature/task
3. EXECUTE: memory_add_decision/learning/search  ← during development
4. WRAP UP: memory_complete_todo → memory_end_session  ← before finishing
```

**Example:**
```
User: "What was our approach to autocorrect?"
Tool: memory_search "autocorrect implementation" ← find patterns & decisions

User: "Found a new bug pattern"
Tool: memory_add_learning "Bug X happens when Y" "context/details"

User: "Done with feature X, moving to Y"
Tool: memory_complete_todo "Implement feature X"
Tool: memory_end_session "Implemented feature X with Z tests passing"
```

## Verification Commands

```bash
# Full verification (recommended)
./init.sh
```

Required checks:
- `./gradlew test`

## Escalation

If you encounter:
- **Architecture decisions**: Consult project architecture docs if present, otherwise ask user
- **Unclear requirements**: Check product/requirements docs if present, otherwise ask user
- **Repeated test failures**: Update progress, flag for human review
- **Scope ambiguity**: Re-read `feature_list.json` for definition of done
