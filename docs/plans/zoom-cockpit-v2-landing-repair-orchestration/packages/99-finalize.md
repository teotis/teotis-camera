# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize the Zoom Cockpit V2 landing repair by validating all package evidence, merging completed package branches into the integration branch, running integration verification, merging back to `main` only after verification passes, writing `FINAL_REPORT.md`, and cleaning up only recorded local orchestration resources after success.

## Finalize Steps

1. Read INDEX, graph, all package docs, all status files, and `state.tsv`.
2. Verify every functional package has complete evidence and touched only allowed paths.
3. Create or update integration branch `agent/zoom-cockpit-v2-landing-repair/integration`.
4. Merge package branches in this order:
   - `01-session-recording-zoom-policy`
   - `02-slider-render-contract-reconciliation`
   - `03-orchestration-ledger-repair`
5. Stop and record conflicts without cleaning anything.
6. Run integration verification.
7. Merge integration branch back to `main` only after verification passes.
8. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
9. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Acceptance Criteria

- All functional packages are `completed`.
- App focused zoom/slider/render tests pass.
- Zoom-only Session tests pass or any broader `DefaultCameraSessionTest` failures are clearly separated from Zoom V2.
- `:app:assembleDebug` passes.
- Stage 7 gate is run if feasible, or failure is recorded with exact blocker.
- Final report states whether Zoom Cockpit V2 is `PASS`, `PARTIAL`, or `FAIL`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*zoom*"
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

- Branch: `agent/zoom-cockpit-v2-landing-repair/99-finalize`
- Worktree: `.agent-worktrees/zoom-cockpit-v2-landing-repair/99-finalize`

## Unlock Conditions

- Unlocks when all functional package rows are `completed` in `status/state.tsv` and corresponding Markdown status files.

## Allowed Paths

- Read all project files.
- Write:
  - `docs/plans/zoom-cockpit-v2-landing-repair-orchestration/status/99-finalize.md`
  - `docs/plans/zoom-cockpit-v2-landing-repair-orchestration/FINAL_REPORT.md`
  - integration branch merge artifacts produced by non-force merges

## Forbidden Paths

- Destructive git operations.
- Unrecorded branch/worktree deletion.
- Runtime edits unless they are obvious, low-risk, and strictly scope-contained omissions found during finalize.

