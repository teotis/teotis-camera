# Package 03 - Orchestration Ledger Repair

## Package ID

`03-orchestration-ledger-repair`

## Goal

Repair the coordinator evidence for Zoom Cockpit V2 so `99-finalize` can trust the status ledger and does not merge based on stale or contradictory package claims.

## Current Evidence

- Previous `state.tsv` marks `02-slider-widget-productization` as `launched`, while its Markdown status says `completed`.
- Previous `state.tsv` marks `03-session-recording-zoom-policy` as `launched`, while its Markdown status says `pending`.
- Previous `04` status says changes landed directly on `main` and touched files owned by other packages.
- The previous orchestration was partially migrated from old `dispatch` style to new `orchestrate` style; the new repair package must be self-consistent.

## Implementation Scope

- Update only the new repair orchestration's coordinator files, not old runtime code.
- Record latest package results from packages 01 and 02.
- Mark stale previous claims explicitly as historical input, not acceptance evidence.
- Ensure `state.tsv`, Markdown status files, `package-graph.tsv`, and `agent-prompts.md` agree.
- If package 01 or 02 reports blocked, set this package blocked and do not unlock finalize.

## Acceptance Criteria

- `bash launchers/orchestrate.sh status` reports no state/status mismatch for this repair package.
- Every completed package has branch, worktree, base commit, commit hash, verification, and risks recorded.
- No old package claim is treated as current pass evidence unless it matches current code.
- `99-finalize` remains pending until all functional packages are completed.

## Verification Commands

```bash
rtk bash -n docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh
rtk bash docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh status
rtk rg -n "TODO|TBD|fill in|implement later|\\.\\.\\." docs/plans/zoom-cockpit-v2-landing-repair-orchestration
```

## Branch And Worktree Policy

- Branch: `agent/zoom-cockpit-v2-landing-repair/03-orchestration-ledger-repair`
- Worktree: `.agent-worktrees/zoom-cockpit-v2-landing-repair/03-orchestration-ledger-repair`

## Unlock Conditions

- Depends on package 01 and package 02 status `completed`.

## Allowed Paths

- `docs/plans/zoom-cockpit-v2-landing-repair-orchestration/**`
- `docs/plans/INDEX.md` only to add or update this plan's single index row

## Forbidden Paths

- Runtime source files
- Runtime tests
- Old orchestration package files except read-only inspection

## Expected Evidence Pack

- [ ] coordinator status updated
- [ ] `state.tsv` row updated
- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] base commit recorded
- [ ] commit hash recorded
- [ ] changed files listed
- [ ] verification commands/results recorded
- [ ] unresolved risks noted
- [ ] only allowed paths touched

