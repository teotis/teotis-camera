# 01-zoom-threshold-lens-switch

## Goal

When the user drags the focal-length slider past `2x` or `5x`, the preview/runtime must switch to the corresponding lens or device zoom node, not merely display an in-between digital zoom value. This must preserve Session Kernel / Device Adapter ownership: UI may dispatch intent, but CameraX/runtime decisions stay out of UI.

## User Symptoms Covered

- Issue 1: sliding across specific values does not switch lens.
- Product rule: crossing above `2x` and `5x` switches preview to the `2x` / `5x` camera path.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/gesture/ZoomScaleMapper.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- focused tests under matching `app/src/test`, `core/device/src/test`, and `core/session/src/test` packages

## Forbidden Paths

- Do not edit unrelated mode plugin features.
- Do not move camera runtime ownership into `MainActivity`, renderer, or view classes.
- Do not hard-code vivo-only camera IDs without a tested `supported/degraded/unsupported` fallback.
- Do not change capture frame ratio or saved-photo postprocess behavior.
- Do not edit coordinator files except `status/01-zoom-threshold-lens-switch.md` and the matching `state.tsv` row.

## Required Investigation

1. Read `docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md` and current package statuses before editing.
2. Trace the current slider release/drag path from `FocalLengthSliderView` to `SessionIntent.ApplyZoomRatio`, session state/effects, `DeviceCommand.UpdateZoomRatio`, and `CameraXCaptureAdapter`.
3. Determine the correct contract for threshold crossing:
   - exact threshold crossing above `2x` requests the `2x` node;
   - exact threshold crossing above `5x` requests the `5x` node;
   - dragging below a higher threshold should not jitter between nodes without hysteresis;
   - unsupported nodes must be explicit degraded/unsupported, not silent no-op.
4. Add a small contract helper if needed, but keep it in session/device-owned code, not the view.

## Acceptance Criteria

- Dragging from below to just above `2x` results in a session/device request that chooses the `2x` preset/node when available.
- Dragging from below to just above `5x` results in a session/device request that chooses the `5x` preset/node when available.
- Threshold behavior is tested with jitter/hysteresis around `2x` and `5x`.
- If the device exposes only digital zoom, UI/device diagnostics report degraded behavior without pretending a physical switch occurred.
- Existing slider label/snap and drag-latch tests still pass.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Test output summaries.
- A short note explaining whether real devices with `2x` / `5x` physical nodes require final user smoke.
- Any unsupported/degraded capability behavior.

## Unlock Condition

Mark completed only after focused zoom tests and assemble pass, or blocked with exact failing command and reason.
