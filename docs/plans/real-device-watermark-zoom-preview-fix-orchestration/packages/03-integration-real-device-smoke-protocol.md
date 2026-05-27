# Package 03 - Integration Real-Device Smoke Protocol

## Package ID

`03-integration-real-device-smoke-protocol`

## Goal

Verify the two implementation packages together and produce an exact real-device smoke checklist. This package should not hide product gaps behind unit-test success: final visual acceptance remains device-dependent.

## Branch And Worktree

- Branch: `agent/watermark-zoom-preview-fix/03-integration-real-device-smoke-protocol`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/watermark-zoom-preview-fix/03-integration-real-device-smoke-protocol`
- Base: the integration branch after packages 01 and 02 are available, or latest `main` plus explicit package branch references if `99-finalize` has not merged them yet.

## Allowed Paths

- `docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/03-integration-real-device-smoke-protocol.md`
- `docs/plans/real-device-watermark-zoom-preview-fix-orchestration/scratch/03-integration-real-device-smoke-protocol/`
- `docs/plans/real-device-watermark-zoom-preview-fix-orchestration/FINAL_REPORT.md` only if instructed by `99-finalize`
- Test-only additions under matching `app/src/test`, `core/effect/src/test`, `core/session/src/test`, or `core/device/src/test` if a small missing integration assertion is found and both implementation branches are present.

## Forbidden Paths

- Do not change product behavior unless you first mark this package blocked and explain the missing behavior.
- Do not edit packages 01/02 status files.
- Do not edit `INDEX.md`.
- Do not claim real-device pass without actual device evidence from the user or an attached device run.

## Required Checks

1. Read package 01 and 02 status files and verify they contain worktree, branch, base commit, commit hash, changed files, and verification results.
2. Inspect diffs for packages 01 and 02:
   - watermark package must visibly affect preview model/overlay, not only settings text;
   - zoom package must address still-photo live preview switching, not only label/snap/session tests.
3. Run focused integration verification from the assigned worktree.
4. Write a smoke checklist that the user can run on vivo X300 or another multi-camera Android device.

## Required Smoke Checklist Content

The coordinator status must include:

- APK path produced by `:app:assembleDebug`.
- Watermark preview cases:
  - select `professional-bottom-bar`; preview shows bottom parameter bar or equivalent bottom strip before capture;
  - select `pure-text`; preview shows text-only overlay;
  - select `blur-four-border`; preview shows four-border/border affordance;
  - placement/opacity/scale changes are visible or explicitly recorded as approximate.
- Zoom preview cases:
  - drag from below `2x` to above `2x`; preview image switches to the `2x` node/view when available;
  - drag from above `2x` back below threshold; preview returns without jitter;
  - drag from below `5x` to above `5x`; preview image switches to the `5x` node/view when available;
  - drag from above `5x` back below threshold; preview returns to the lower node without jitter;
  - if the device lacks physical nodes, the app surfaces degraded/digital behavior honestly.
- Residual risks that unit tests cannot prove.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.PreviewOverlayGeometryTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

Optional stage gate if the machine is idle:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Expected Evidence

- Worktree path, branch, base commit, final commit hash if any.
- Package 01 and 02 commit hashes verified to exist.
- Focused verification output summaries.
- APK path.
- Real-device smoke checklist.
- Explicit residual risks.

## Unlock Condition

Mark completed only after package evidence is complete and local focused integration verification passes, or blocked with exact missing evidence/failing command.
