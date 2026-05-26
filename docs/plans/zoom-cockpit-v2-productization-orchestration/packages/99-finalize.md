# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Zoom Cockpit V2 productization by validating all package evidence, merging completed package branches into the integration branch, running integration verification, merging back to mainline only after verification passes, writing `FINAL_REPORT.md`, and cleaning up only recorded local orchestration resources after success.

## Context

- This is not a passive audit. It is the final orchestration package.
- Relevant docs:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/INDEX.md`
  - all package docs under `packages/`
  - all package evidence under `status/`
  - machine state: `status/state.tsv`
  - graph: `launchers/package-graph.tsv`
- Non-goals:
  - Do not expand scope into preview-vs-capture saved crop semantics unless a package explicitly changed it.
  - Do not turn this into a broad UI Animation V2 audit.
  - Do not force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Finalize Steps

1. Read INDEX, graph, all package docs, all status files, and `state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, commit hash recorded;
   - verification commands passed or failure is explicitly justified.
3. Decide whether merging is allowed.
4. Create or update integration branch `agent/zoom-cockpit-v2-productization/integration`.
5. Merge functional package branches in Merge Strategy order:
   - `agent/zoom-cockpit-v2-productization/01-product-contract-capability-boundary`
   - `agent/zoom-cockpit-v2-productization/02-slider-widget-productization`
   - `agent/zoom-cockpit-v2-productization/03-session-recording-zoom-policy`
   - `agent/zoom-cockpit-v2-productization/04-cockpit-wiring-and-ux-integration`
6. Stop and record conflicts without cleaning anything.
7. Run integration verification.
8. Merge integration branch back to `main` only after verification passes.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
10. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Acceptance Criteria

- Every functional package is `completed` in `status/state.tsv` and its Markdown status file.
- Package evidence is complete and internally consistent.
- No package edited forbidden paths without explicit user approval.
- Integration branch contains all package commits in merge strategy order.
- Focused app/session tests pass.
- `:app:assembleDebug` passes.
- Stage 7 gate is run or any inability to run it is recorded with concrete reason.
- Final UX state matches the user goal: points + slider + one-decimal focal/zoom label + capability boundary + recording restriction.
- Residual real-device risks are listed rather than hidden.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

## Branch And Worktree Policy

- Branch: `agent/zoom-cockpit-v2-productization/99-finalize`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.agent-worktrees/zoom-cockpit-v2-productization/99-finalize`
- Integration branch: `agent/zoom-cockpit-v2-productization/integration`
- Mainline branch: `main`
- Cleanup may delete only recorded local package worktrees/branches after all finalize steps succeed.

## Unlock Conditions

- Unlocks when all functional package rows are `completed` in `status/state.tsv` and corresponding Markdown status files.

## File Ownership

- `99-finalize` owns:
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/status/99-finalize.md`
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/FINAL_REPORT.md`
- Package agents must not edit finalize files.

## Allowed Paths

- Read all project files.
- Write only:
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/status/99-finalize.md`
  - `docs/plans/zoom-cockpit-v2-productization-orchestration/FINAL_REPORT.md`
  - integration branch merge artifacts produced by non-force merges

## Forbidden Paths

- Runtime code edits unless they are obvious, low-risk, and strictly scope-contained omissions found during finalize.
- Destructive git operations.
- Unrecorded branch/worktree deletion.

## Dependencies

- Depends on: `01-product-contract-capability-boundary`, `02-slider-widget-productization`, `03-session-recording-zoom-policy`, `04-cockpit-wiring-and-ux-integration`

## Parallel Safety

- unsafe
- Reason: final package must run after all functional package evidence is available.

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status captured
- [ ] git diff --stat captured
- [ ] per-package acceptance status recorded
- [ ] verification commands run
- [ ] test results summarized
- [ ] integration branch recorded
- [ ] mainline merge commit recorded or blocked reason recorded
- [ ] cleanup results recorded
