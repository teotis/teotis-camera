# Package 04 - Ledger And Gate Honesty

## Package ID

`04-ledger-and-gate-honesty`

## Problem

The failed audit found contradictory documentation: a Rendering 2.0 approved package doc says `implemented`, while `docs/plans/INDEX.md` still lists the master and subplans as `planned`. The previous audit also avoided long Gradle verification because package evidence was absent and static blockers remained.

## Goal

Make the planning ledger and verification gate honest: documentation must reflect actual implementation and validation state, and any Stage 7 gate instability must be isolated with evidence rather than bypassed by deleting coverage.

## File Ownership

Allowed paths:
- `docs/plans/INDEX.md`
- `docs/plans/2026-05-25-rendering-2-0-approved-upgrade-index.md`
- `docs/plans/2026-05-25-rendering-2-0-capture-save-reliability.md`
- `docs/plans/2026-05-25-rendering-2-0-render-recipe-single-truth.md`
- `docs/plans/2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md`
- `docs/plans/rendering-2-0-validation-fix-orchestration*/**`
- `scripts/verify_stage_7_observability.sh` only if a minimal timeout/isolation improvement is required and tested
- `codex/documentation.md` only if the repo working loop requires recording a verified meaningful loop

Forbidden paths:
- Product implementation files under `app/src/main/**`, `core/**`, and feature modules
- Other product plan areas unrelated to Rendering 2.0
- Any package status file except `docs/plans/rendering-2-0-validation-fix-orchestration-v2/status/04-ledger-and-gate-honesty.md`

## Dependencies

This package can begin early, but final status wording should not claim implementation success until packages 01-03 provide evidence.

## Required Behavior

- Fix contradictory `planned`/`implemented`/`blocked` wording for Rendering 2.0 so it matches evidence.
- Preserve historical audit records; do not erase the failed audit.
- If touching Stage 7 verification scripts, only add scoped observability or isolation that helps diagnose hangs/failures. Do not remove meaningful tests.
- Record exactly what remains local-only versus real-device-only.

## Acceptance Criteria

- `docs/plans/INDEX.md` and Rendering 2.0 docs no longer contradict the current state.
- The new V2 orchestration package is linked from `docs/plans/INDEX.md`.
- Any verification-script change is minimal, documented, and covered by a dry run or focused command.
- No unrelated plan rows are rewritten.

## Verification Commands

```bash
rtk rg -n "Rendering 2.0|rendering-2-0|implemented|validated|planned|blocked" docs/plans/INDEX.md docs/plans/2026-05-25-rendering-2-0-approved-upgrade-index.md docs/plans/rendering-2-0-validation-fix-orchestration/status/99-integration-audit.md docs/plans/rendering-2-0-validation-fix-orchestration-v2/INDEX.md
rtk git diff --stat
```

If a verification script is edited, additionally run its shell syntax check and the smallest meaningful Gradle command it invokes.

## Expected Evidence Pack

Write to `status/04-ledger-and-gate-honesty.md`:
- worktree path and branch
- changed files and `git diff --stat`
- exact ledger rows changed
- verification commands and pass/fail summaries
- commit hash or PR link
- self-certification that only allowed paths were touched
