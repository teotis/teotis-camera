# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Integrate the functional package branches, verify the combined result, merge back to mainline after verification, write the final report, and clean up only recorded resources after success.

## Branch And Worktree

- Branch: `agent/watermark-zoom-preview-fix/99-finalize`
- Integration branch: `agent/watermark-zoom-preview-fix/integration`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/watermark-zoom-preview-fix/99-finalize`

## Authorization

`99-finalize` may:

- Inspect `INDEX.md`, package docs, status files, `state.tsv`, events, branches, commits, and diffs.
- Run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh verify-finalize`.
- Create/update the integration branch.
- Merge functional branches in the merge order from `INDEX.md`.
- Run integration verification.
- Merge the verified integration branch back to `main`.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only package worktrees/branches recorded in this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:

- force-push
- hard reset
- delete remote branches
- delete local branches/worktrees not recorded by this plan
- clean unrelated files
- commit coordinator ledger/final report together with unrelated source changes

## Required Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh verify-finalize
```

3. For every functional package, verify:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack is complete;
   - branch exists;
   - commit hash exists;
   - package worktree is clean or dirty state is recorded as a blocker;
   - verification commands passed or failure is explicitly justified.
4. Stop if any package is incomplete, stale, invalid, blocked, or outside allowed paths.
5. Create or update `agent/watermark-zoom-preview-fix/integration`.
6. Merge functional package branches in this order:
   - `01-watermark-template-preview-expectation`
   - `02-zoom-threshold-live-preview-switch`
   - `03-integration-real-device-smoke-protocol`
7. On conflict, stop, preserve worktrees/branches, mark `99-finalize` blocked, and record conflict files.
8. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.PreviewOverlayGeometryTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

9. If focused verification passes and the machine is available, run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

10. Merge the verified integration branch back to `main` only after verification passes.
11. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
12. Mark `99-finalize` as `finalized` through the orchestrator.
13. Clean up only recorded package worktrees/branches after all previous steps succeed.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion in `status/99-finalize.md`.
- Also update the coordinator ledger with:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<specific failure>" \
  --failed-command "<failed command>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<decisive log lines>" \
  --recovery-hint "<next action>"
```

- Preserve branches/worktrees on failure.
- Check `status/events.jsonl` before retrying repeated failure fingerprints.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, APK path, smoke checklist summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
