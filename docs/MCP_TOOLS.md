# MCP Tools Reference

All memory MCP tools accept a `cwd` parameter to set the project. Always pass it.

## Core Tools

### memory_start_session

**Phase:** START  
**Required:** Yes, call first in every session

```python
memory_start_session(cwd="/home/dark/Project/darkkeyboard")
```

**Returns:**
- `decisions`: List of technical decisions made in this project
- `learnings`: List of lessons learned
- `todos`: Pending tasks ordered by priority
- `last_session`: Summary of what happened last time
- `last_progress_entry`: Last entry from progress.md
- `warning`: Alert if previous session wasn't closed properly

### memory_add_decision

**Phase:** EXECUTE  
**When:** Choosing between technical approaches

```python
memory_add_decision(
    decision="Use SQLite WAL mode for concurrent access",
    rationale="Prevents 'database is locked' errors in multi-process scenarios",
    cwd="/home/dark/Project/darkkeyboard"
)
```

### memory_add_learning

**Phase:** EXECUTE  
**When:** Discovering something useful for future sessions

```python
memory_add_learning(
    lesson="FTS5 MATCH fails with apostrophes, use LIKE queries instead",
    context="Conflict detection in WriteQueue",
    cwd="/home/dark/Project/darkkeyboard"
)
```

### memory_add_todo

**Phase:** EXECUTE  
**When:** Finding out-of-scope work during implementation

```python
memory_add_todo(
    task="Implement semantic deduplication with embeddings threshold 0.85",
    priority="medium",
    cwd="/home/dark/Project/darkkeyboard"
)
```

### memory_get_todos

**Phase:** SELECT  
**When:** Choosing which feature to work on

```python
memory_get_todos(cwd="/home/dark/Project/darkkeyboard")
```

**Returns:** List of pending tasks ordered by priority (high → medium → low)

### memory_complete_todo

**Phase:** EXECUTE  
**When:** A task is finished

```python
memory_complete_todo(
    task="Implement semantic deduplication with embeddings threshold 0.85",
    cwd="/home/dark/Project/darkkeyboard"
)
```

### memory_search

**Phase:** EXECUTE  
**When:** Looking for existing code patterns before writing new code

```python
memory_search(
    query="how to handle SQLite concurrent writes",
    project="memory-system",  # or None for cross-project search
    limit=10,
    filters={"is_writeup": True, "os": "linux"},  # optional
    use_reranker=True  # optional, improves ranking
)
```

**Returns:** List of relevant code chunks with context

### memory_end_session

**Phase:** WRAP UP  
**Required:** Yes, call before finishing every session

```python
memory_end_session(
    summary="Implemented WriteQueue to prevent SQLite lock errors, all tests pass",
    completed_todos=["Implement WriteQueue pattern"],
    verified="ran init.sh, 57/57 tests pass",
    cwd="/home/dark/Project/darkkeyboard"
)
```

**Requirements:**
- `summary` must be >20 chars
- `verified` should describe what tests/checks were run
- Call ONLY after verification passes

## Project Detection Fallback Chain

If you don't pass `cwd`, the server detects project from:

1. `cwd` parameter (recommended, explicit)
2. `MEMORY_PROJECT` env var (optional override)
3. Git root detection (automatic)
4. `os.getcwd()` (last resort)

**Best practice:** Always pass `cwd` explicitly to avoid ambiguity.

## Common Mistakes

❌ **Don't do this:**
```python
memory_start_session()  # Missing cwd
memory_add_decision("Use WAL mode")  # Missing rationale and cwd
memory_end_session("done")  # Summary too short, missing verified
```

✅ **Do this:**
```python
memory_start_session(cwd="/home/dark/Project/darkkeyboard")
memory_add_decision(
    decision="Use WAL mode",
    rationale="Prevents database locks",
    cwd="/home/dark/Project/darkkeyboard"
)
memory_end_session(
    summary="Implemented WAL mode, all tests pass",
    verified="ran init.sh",
    cwd="/home/dark/Project/darkkeyboard"
)
```
