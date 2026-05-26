# Package 99 — Finalize

## Package ID

`99-finalize`

## Purpose

Read all package evidence and decide whether the research/design package is ready to become an implementation handoff, needs more research, or should be stopped because the required behavior cannot be supported honestly on vivo X300 with the current platform stack. This is the active finalize agent — not a passive audit prompt.

## Inputs

- `INDEX.md`
- `launchers/package-graph.tsv`
- `status/state.tsv`
- `packages/01-pixel-capability-enumeration.md`
- `packages/02-quick-pixel-surface-design.md`
- `packages/03-shutter-lifecycle-contract.md`
- `packages/04-real-device-verification-protocol.md`
- `status/01-pixel-capability-enumeration.md`
- `status/02-quick-pixel-surface-design.md`
- `status/03-shutter-lifecycle-contract.md`
- `status/04-real-device-verification-protocol.md`

## Finalize Steps

1. Read INDEX, graph, all package docs, all status files, and `state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete
   - branch, worktree, base commit, commit hash recorded
   - verification commands passed or failure is explicitly justified
3. Decide whether merging is allowed.
4. Create or update the integration branch `integration/vivo-x300-pixel-shutter-lifecycle`.
5. Merge functional package branches in Merge Strategy order (01, 03, 02, 04).
6. Stop and record conflicts without cleaning anything.
7. Run integration verification.
8. Merge integration branch back to mainline only after verification passes.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
10. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## PASS Criteria

- All four research/design packages completed their status evidence.
- No package implemented runtime changes.
- The combined design has clear supported/degraded/unsupported semantics for pixel capability.
- The shutter re-arm policy is conservative by capture kind and does not add a hidden second session kernel.
- The real-device protocol can catch the original two symptoms.

## PARTIAL Criteria

- One package is incomplete but enough evidence exists to write a narrower follow-up.
- High-pixel support remains uncertain but the degraded/unsupported path is honest.
- Shutter lifecycle design is plausible but needs one more code audit before implementation.

## FAIL Criteria

- Evidence conflicts across packages.
- A package edited forbidden files or claimed real-device success without device evidence.
- The proposed design would make unsafe repeated capture possible in special modes.
- The pixel capability design would show 48MP/50MP without bindable/saved-output proof.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
