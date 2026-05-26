# 02 Ledger And Rules Restoration

## Package ID

02-ledger-and-rules-restoration

## Goal

Restore the missing handoff package record and align project instructions with the accepted build-isolation model. The outcome is that future agents can discover the root cause, current fix, remaining script-follow-up, and correct verification policy without reading this conversation.

## Context

- `AGENTS.md` currently documents the new Gradle build isolation policy.
- The prior handoff document `docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md` is missing in the current workspace.
- `docs/plans/INDEX.md` is the canonical planning index.
- `codex/documentation.md` should record the live status and remaining risk.

## File Ownership

Allowed paths:
- `AGENTS.md`
- `codex/documentation.md`
- `docs/plans/INDEX.md`
- `docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md`
- your assigned status file under `docs/plans/gradle-build-isolation-followup-orchestration/status/`

Forbidden paths:
- `scripts/**`
- Runtime app/core Kotlin files
- Other orchestration package status files
- `docs/plans/gradle-build-isolation-followup-orchestration/INDEX.md`

## Dependencies

None, but read package 01 to avoid contradicting its intended script behavior.

## Parallel Safety

Safe to run in parallel with package 01 if you do not edit scripts.

## Implementation Scope

- Recreate `docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md` as the durable handoff package for this build-isolation work.
- Mark the current state honestly, for example `implemented-with-follow-up`: Gradle override and wrapper are implemented; old verification scripts still require isolation hardening until package 01 lands.
- Update `docs/plans/INDEX.md` with a link to both:
  - the restored handoff package;
  - this orchestration package.
- Update `codex/documentation.md` only where it records current residual risks or next-step suggestions.
- Keep `AGENTS.md` concise and operational. It should say that external agents/worktrees use `run_isolated_gradle.sh` for Gradle tasks and must use an isolated root for stage scripts that invoke Gradle internally.

## Acceptance Criteria

- The restored handoff package has concrete sections: verified facts, current implementation status, remaining gap, acceptance policy, verification commands.
- `docs/plans/INDEX.md` links to the handoff package and orchestration kit.
- `codex/documentation.md` reflects that the core fix is valid but old verification script isolation is pending until package 01 lands.
- `AGENTS.md` does not claim all stage scripts are isolated until that is true.
- No script or runtime code changes.

## Verification Commands

```bash
rtk rg -n "Gradle Isolation|build isolation|run_isolated_gradle|OPENCAMERA_BUILD_ROOT|implemented-with-follow-up" AGENTS.md codex/documentation.md docs/plans/INDEX.md docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md
rtk test -f docs/plans/2026-05-26-agent-handoff-gradle-isolation-index.md
```

## Expected Evidence Pack

Write to `status/02-ledger-and-rules-restoration.md` only. Include:
- docs changed;
- exact status language chosen;
- verification command output summary;
- unresolved documentation risks, if any.

## Risks And Notes

- Do not rewrite old historical plans.
- Do not overstate that the entire build system is fixed until package 01 and final audit pass.
- Avoid duplicating long implementation instructions in `AGENTS.md`; link to the plan where useful.

