# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Finalize this orchestration by validating package evidence, merging package branches into the integration branch, running integration verification, merging back to mainline only after verification passes, writing `FINAL_REPORT.md`, and cleaning up only recorded resources after success.

## Branch And Worktree

- Branch: `agent/shutter-feedback-watermark/99-finalize`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-99-finalize`

## Allowed Paths

- This plan directory:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/99-finalize.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/FINAL_REPORT.md`
- Git branches/worktrees recorded by this orchestration.
- Integration branch: `agent/shutter-feedback-watermark/integration`.

## Required Work

1. Read `INDEX.md`, `launchers/package-graph.tsv`, every package doc, every status file, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed,
   - changed files within allowed paths,
   - evidence pack complete,
   - branch, worktree, base commit, commit hash recorded,
   - verification commands passed or failure justified.
3. Stop if any package is not `completed`.
4. Create or update the integration branch `agent/shutter-feedback-watermark/integration`.
5. Merge package branches in this order:
   - `04-watermark-template-preview`
   - `02-shutter-fast-feedback-runtime`
   - `03-shutter-visual-state-and-qa`
   - `05-real-device-acceptance`
6. Stop and record conflicts without cleaning anything.
7. Run integration verification:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

8. Merge the verified integration branch back to `main` only after verification passes.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
10. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
