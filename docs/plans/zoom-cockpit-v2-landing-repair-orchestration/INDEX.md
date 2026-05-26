# Zoom Cockpit V2 Landing Repair - Orchestration Index

## Goal

Repair the current Zoom Cockpit V2 landing according to the latest code/evidence check. App-side zoom slider/render tests pass, but the landing is not acceptance-ready because Session recording zoom policy is still incomplete, package evidence and `state.tsv` disagree, and prior package 04 touched files owned by earlier packages while landing directly on `main`. This plan creates a tail-driven repair flow that preserves the existing useful implementation while closing the missing Session policy, UI/product-state consistency, and orchestration ledger gaps.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/zoom-cockpit-v2-landing-repair/integration`
- Functional package branches: `agent/zoom-cockpit-v2-landing-repair/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
|---|---|---|---|---|
| 01-session-recording-zoom-policy | none | status | initial ready wave | 1 |
| 02-slider-render-contract-reconciliation | none | status | initial ready wave | 1 |
| 03-orchestration-ledger-repair | 01-session-recording-zoom-policy, 02-slider-render-contract-reconciliation | status | both package status files completed | 2 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-session-recording-zoom-policy` -> `02-slider-render-contract-reconciliation` -> `03-orchestration-ledger-repair`
- Code dependency policy: status dependencies unlock implementation; `99-finalize` merges all completed package branches into the integration branch before integration verification.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## Latest Verified Inputs

- App focused command passed: `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest`.
- Session command failed broadly: `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest` reported 19 failures, mostly outside zoom. Do not treat the whole class as Zoom V2 evidence.
- Current `DefaultCameraSessionTest` still has `zoom toggle remains available while recording and blocks only unsupported devices`, which conflicts with the newer requirement for recording-state restrictions.
- Current `FocalLengthSliderView.kt` contains inline math helpers and tests pass, but previous package evidence claimed a separate `FocalLengthSliderMath.kt` exists on main; it does not.
- Previous `state.tsv` has `02` and `03` as `launched` while Markdown says `02` completed and `03` pending; this blocks reliable finalize.

## Package Documents

| Work Package | Purpose |
|---|---|
| [01-session-recording-zoom-policy.md](./packages/01-session-recording-zoom-policy.md) | Make Session Kernel enforce active-recording zoom restrictions and add zoom-only tests. |
| [02-slider-render-contract-reconciliation.md](./packages/02-slider-render-contract-reconciliation.md) | Align UI/render/slider behavior and tests with the final Session policy and actual landed files. |
| [03-orchestration-ledger-repair.md](./packages/03-orchestration-ledger-repair.md) | Repair coordinator status/state evidence and remove stale claims from the previous orchestration. |
| [99-finalize.md](./packages/99-finalize.md) | Merge package branches, run integration verification, merge back to mainline, report, and clean up only after success. |

