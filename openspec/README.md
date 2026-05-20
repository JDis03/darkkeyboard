# OpenSpec for DarkKeyboard

OpenSpec is our **spec-driven development framework** that helps us plan, implement, and document changes with clear specifications.

## Structure

```
openspec/
├── specs/                    # Living specifications (organized by capability)
│   ├── composing-lifecycle/
│   │   └── spec.md          # Requirements, scenarios, invariants for composing
│   ├── cursor-tracking/
│   │   └── spec.md
│   ├── autocorrect-decision/
│   │   └── spec.md
│   ├── inputconnection-contracts/
│   │   └── spec.md
│   └── suggestion-pipeline/
│       └── spec.md
│
└── changes/                  # Change proposals (archived after implementation)
    ├── fix-ppalabra-duplicate/
    │   ├── proposal.md       # What & why
    │   ├── design.md         # Technical approach
    │   ├── tasks.md          # Implementation checklist
    │   └── specs/            # Spec deltas (what requirements changed)
    │       └── composing-lifecycle/
    │           └── spec.md
    └── archive/              # Completed changes (git history alternative)
        └── 2026-05-20-fix-ppalabra-duplicate/
```

## Workflow

### Starting a new change

```bash
# Option 1: Use the slash command (in OpenCode)
/opsx:propose "add emoji keyboard support"

# Option 2: Use CLI
npx openspec new change "add-emoji-keyboard"
```

This creates a scaffolded change folder with `.openspec.yaml`.

### Creating artifacts

OpenSpec guides you through creating:

1. **proposal.md** — What are we building and why?
2. **design.md** — How will we implement it?
3. **specs/** — What requirements are changing?
4. **tasks.md** — Step-by-step implementation checklist

You can create them manually or use:

```bash
# Get instructions for an artifact
npx openspec instructions proposal --change "add-emoji-keyboard"

# Check status
npx openspec status --change "add-emoji-keyboard"
```

### Implementing a change

```bash
# In OpenCode, use:
/opsx:apply

# This will:
# - Read the tasks.md
# - Implement each task step-by-step
# - Update specs/ as you go
```

### Archiving a completed change

```bash
# After implementation + verification
npx openspec archive add-emoji-keyboard --reason "Implemented and tested"

# This moves the change to openspec/changes/archive/YYYY-MM-DD-name/
# And updates the canonical specs in openspec/specs/
```

## Benefits

### 1. Review intent, not just code

When reviewing PRs, you see:
- **proposal.md** — Is this the right thing to build?
- **design.md** — Is this the right approach?
- **specs/** delta — What requirements changed?
- **tasks.md** — Is everything implemented?

This is MUCH easier than reviewing 500 lines of Kotlin diffs.

### 2. Context that persists

Our specs live in the repo. When a new developer (or AI agent) joins:
- Read `openspec/specs/*/spec.md` to understand how the system works
- Read past changes in `openspec/changes/archive/` to see how we evolved

### 3. Better AI collaboration

AI agents work better with clear specs. Instead of:

```
"Fix the cursor jump bug"  ← vague
```

You give:

```
Read openspec/specs/cursor-tracking/spec.md
The bug violates Invariant I3: expectedSelStart must match after setComposingText
Fix it according to the HeliBoard pattern documented in §2.3
```

The AI now has **context**, **constraints**, and **success criteria**.

## Integration with SDD.md

We migrated our original `SDD.md` into OpenSpec specs:

- `SDD.md §1 Composing Lifecycle` → `openspec/specs/composing-lifecycle/spec.md`
- `SDD.md §2 Cursor Tracking` → `openspec/specs/cursor-tracking/spec.md`
- `SDD.md §3 Autocorrect Decision` → `openspec/specs/autocorrect-decision/spec.md`
- `SDD.md §4 IC Contracts` → `openspec/specs/inputconnection-contracts/spec.md`
- `SDD.md §5 Suggestion Pipeline` → `openspec/specs/suggestion-pipeline/spec.md`

The `SDD.md` file now serves as a **high-level overview** that links to the detailed specs.

## Commands Reference

### In OpenCode (slash commands)

- `/opsx:propose "description"` — Create a new change with all artifacts
- `/opsx:apply` — Implement the tasks from tasks.md
- `/opsx:archive` — Archive a completed change
- `/opsx:explore` — Browse existing specs

### In CLI

```bash
# Create a new change
npx openspec new change "name"

# Get artifact instructions
npx openspec instructions <artifact-id> --change "name"

# Check status
npx openspec status --change "name"
npx openspec status --change "name" --json  # For parsing

# Archive a change
npx openspec archive "name" --reason "reason"

# List all changes
npx openspec list changes

# Validate specs
npx openspec validate
```

## Example: Fix "ppalabra" Bug

See `openspec/changes/fix-ppalabra-duplicate/` for a complete example of:
- A clear proposal (what & why)
- Technical design (how)
- Spec delta (what requirements changed)
- Implementation tasks (step-by-step checklist)

This change was implemented, tested, and is now ready to archive after device verification.

## Language / Idioma

OpenSpec is configured to generate all artifacts in **English**, even though you can communicate with the AI in Spanish.

The project context in `openspec/config.yaml` specifies:

```yaml
context: |
  Language: English
  All artifacts must be written in English.
  
  Note: The user will communicate with you in Spanish, but you should 
  generate all documentation and artifacts in English for consistency 
  with the codebase.
```

### Example

```bash
# You write in Spanish:
/opsx:propose "agregar historial de portapapeles"

# But it generates in English:
# - proposal.md (in English)
# - design.md (in English)  
# - tasks.md (in English)
# - specs/*.md (in English)
```

This approach gives you the best of both worlds:
- ✅ Communicate naturally in Spanish
- ✅ Documentation in English (consistent with code)
- ✅ Easy for international contributors to read

## Philosophy

OpenSpec follows these principles:

- **Fluid, not rigid** — Update artifacts anytime, no phase gates
- **Iterative, not waterfall** — Start coding when "good enough", refine as you go
- **Lightweight** — Spend 10 minutes planning, not 10 hours
- **Living docs** — Specs evolve with the code

We're not doing waterfall. We're doing **just enough** planning to:
1. Agree on what to build (proposal)
2. Think through the approach (design)
3. Break it into steps (tasks)
4. Document what changed (spec deltas)

Then we build. If things change, we update the docs and keep going.

---

For more info, see [OpenSpec docs](https://github.com/Fission-AI/OpenSpec) or ask in Discord.
