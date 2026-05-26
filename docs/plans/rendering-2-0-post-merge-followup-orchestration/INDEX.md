# Rendering 2.0 Post-Merge Follow-Up - Orchestration Index

## Goal

Accept the already-landed positive Rendering 2.0 fixes where they are implementation-quality, then close the remaining verification and ledger gaps. The functional packages are intentionally narrow: unblock the app unit-test compile gate around preview geometry tests, reconcile Rendering 2.0 status ledgers without overclaiming product completion, then let `99-finalize` merge and clean up only after all evidence passes.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/rendering-2-0-post-merge-followup/integration`
- Functional package branches: `agent/rendering-2-0-post-merge-followup/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-post-merge-followup-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
| --- | --- | --- | --- | --- |
| 01-app-unit-test-gate-cleanup | none | status | ready at start | 1 |
| 02-ledger-status-reconciliation | none | status | ready at start | 1 |
| 99-finalize | 01-app-unit-test-gate-cleanup, 02-ledger-status-reconciliation | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-app-unit-test-gate-cleanup`, then `02-ledger-status-reconciliation`.
- Code dependency policy: status dependency only; both functional packages are file-disjoint and can complete independently.
- Conflict owner: `99-finalize`.
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
| --- | --- | --- |
| `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt` | 01-app-unit-test-gate-cleanup | 02 |
| `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` | 01-app-unit-test-gate-cleanup | 02 |
| `app/src/test/java/com/opencamera/app/PreviewColorTransformOverlayTest.kt` | 01-app-unit-test-gate-cleanup only if assertions need a narrow update | 02 |
| `app/src/main/java/com/opencamera/app/**Preview*Geometry*` | 01-app-unit-test-gate-cleanup | 02 |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` | 01-app-unit-test-gate-cleanup only if required for compile repair | 02 |
| `docs/plans/rendering-2-0-validation-fix-orchestration-v2/**` | 02-ledger-status-reconciliation | 01 |
| `docs/plans/2026-05-25-rendering-2-0-*.md` | 02-ledger-status-reconciliation | 01 |
| `docs/plans/INDEX.md` | 02-ledger-status-reconciliation | 01 |
| `docs/plans/rendering-2-0-post-merge-followup-orchestration/status/<package-id>.md` | assigned package only | all others |

## Evidence Pack Required From Each Agent

Each package agent MUST write to its assigned coordinator status file after completion.
Do NOT edit `INDEX.md` directly.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] base commit
- [ ] commit hash
- [ ] git status
- [ ] git diff --stat
- [ ] changed files
- [ ] commands run and output summary
- [ ] test result summary
- [ ] unresolved risks
- [ ] self-certification that only allowed paths were touched

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
| --- | --- | --- | --- | --- |
| [01-app-unit-test-gate-cleanup.md](./packages/01-app-unit-test-gate-cleanup.md) | implementation agent | none | safe with 02 | Repair the app test compile blockers so the PreviewColorTransformOverlay app test can actually run. |
| [02-ledger-status-reconciliation.md](./packages/02-ledger-status-reconciliation.md) | documentation/status agent | none | safe with 01 | Update Rendering 2.0 ledgers to reflect accepted 01/02/03 positives and remaining validation limits without overclaiming product completion. |
| [99-finalize.md](./packages/99-finalize.md) | finalize agent | after all functional packages | final only | Verify evidence, merge package branches through integration, run final verification, merge back to mainline, and clean up recorded resources only after success. |

## First Wave

The first wave is:
- `01-app-unit-test-gate-cleanup`
- `02-ledger-status-reconciliation`

Downstream dispatch is triggered only by package tail calls to `orchestrate.sh advance`. Package agents do not launch downstream work directly.
