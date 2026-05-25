# Agent Instruction Sync Design

## Goal

Make this repository easier for multiple coding agents to share without letting their instruction files drift apart.

## Design

`AGENTS.md` remains the single source of truth for project rules, stage status, command policy, verification, and documentation policy. Agent-specific root files are thin entrypoints only: they tell that agent to read and follow `AGENTS.md`, plus the local `rtk` command rule. They must not copy the full project contract.

The repository adds `scripts/verify_agent_instructions.sh` as the sync gate. It checks that `CLAUDE.md` and `GEMINI.md` exactly match their expected wrapper templates, and that `AGENTS.md` still contains the critical shared sections. This catches accidental divergence cheaply and can be run by any agent before handoff.

## Scope

- Add thin wrappers for Claude Code and Gemini CLI.
- Add one shell verification script.
- Update the living status document with the new multi-agent instruction baseline.
- Keep `codex/plan.md`, `codex/prompt.md`, and `codex/implement.md` unchanged.

## Verification

Run:

```bash
rtk ./scripts/verify_agent_instructions.sh
```

Expected result:

```text
Agent instruction files are synchronized with AGENTS.md.
```
