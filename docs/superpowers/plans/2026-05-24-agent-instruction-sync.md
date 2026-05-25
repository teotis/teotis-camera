# Agent Instruction Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep multi-agent instruction entrypoints synchronized by making `AGENTS.md` the only full project contract and verifying thin wrappers.

**Architecture:** `AGENTS.md` owns shared rules. `CLAUDE.md` and `GEMINI.md` are fixed wrappers that defer to `AGENTS.md`. `scripts/verify_agent_instructions.sh` is the local gate that prevents drift.

**Tech Stack:** Markdown, POSIX shell, `rtk` command wrapper.

---

### Task 1: Add Thin Agent Entrypoints

**Files:**
- Create: `CLAUDE.md`
- Create: `GEMINI.md`
- Modify: `AGENTS.md`

- [ ] **Step 1: Verify current gap**

Run:

```bash
rtk /bin/test -f CLAUDE.md
```

Expected: command exits non-zero because the wrapper does not exist yet.

- [ ] **Step 2: Create fixed wrappers**

Create `CLAUDE.md` and `GEMINI.md` with short text that names `AGENTS.md` as the canonical source and repeats only the local command rule.

- [ ] **Step 3: Correct stale workspace path**

In `AGENTS.md`, ensure the authoritative workspace path is `/Volumes/Extreme_SSD/project/open_camera`.

### Task 2: Add Sync Verification

**Files:**
- Create: `scripts/verify_agent_instructions.sh`

- [ ] **Step 1: Add template checks**

The script writes expected wrapper templates to temporary files, compares them with `CLAUDE.md` and `GEMINI.md`, and fails with a clear message if either has drifted.

- [ ] **Step 2: Add shared-section checks**

The script verifies `AGENTS.md` still has the shared sections agents depend on: local command rule, architecture contract, current stage, required working loop, verification, documentation rules, and edit constraints.

- [ ] **Step 3: Run the gate**

Run:

```bash
rtk ./scripts/verify_agent_instructions.sh
```

Expected:

```text
Agent instruction files are synchronized with AGENTS.md.
```

### Task 3: Record Status

**Files:**
- Modify: `codex/documentation.md`

- [ ] **Step 1: Add a recent loop entry**

Record that the repository now has a multi-agent instruction sync gate and thin wrappers.

- [ ] **Step 2: Re-run verification**

Run:

```bash
rtk ./scripts/verify_agent_instructions.sh
```

Expected:

```text
Agent instruction files are synchronized with AGENTS.md.
```
