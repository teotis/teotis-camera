# Package 02 - Zoom Threshold Live Preview Switch

## Package ID

`02-zoom-threshold-live-preview-switch`

## Goal

When the user drags the focal-length slider past `2x` or `5x`, the live preview must switch to the corresponding lens/runtime node. The reverse direction must switch back when the slider drops below the threshold range. This package is about the preview image changing, not only the slider value, node label, or saved still crop.

## User Symptoms Covered

- Latest real-device issue 5: sliding the zoom bar across specific nodes does not automatically switch the preview image.
- Product rule: crossing above `2x` previews the `2x` node; crossing above `5x` previews the `5x` node; crossing back down reverses the preview node.

## Branch And Worktree

- Branch: `agent/watermark-zoom-preview-fix/02-zoom-threshold-live-preview-switch`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/watermark-zoom-preview-fix/02-zoom-threshold-live-preview-switch`
- Base: latest `main` unless `99-finalize` or the user says otherwise.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/gesture/ZoomScaleMapper.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- focused tests under matching `app/src/test`, `core/device/src/test`, and `core/session/src/test` packages
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/02-zoom-threshold-live-preview-switch.md`

## Forbidden Paths

- Watermark preview/rendering files.
- Frame-ratio saved crop behavior, unless needed only for a test assertion that it remains unchanged.
- Mode plugin product features unrelated to zoom.
- Hard-coded vivo camera IDs without tested `supported`, `unsupported`, or `degraded` behavior.
- UI-owned direct CameraX/Camera2 calls.
- Other package status files.
- `INDEX.md`.

## Required Investigation

1. Read this plan, `docs/plans/real-device-ux-regression-20260527-orchestration/packages/01-zoom-threshold-lens-switch.md`, and current code before editing.
2. Trace drag events from `FocalLengthSliderView.onRatioChanged/onRatioSnapped` to `SessionIntent.ApplyZoomRatio`.
3. Verify the session path through `DefaultCameraSession.handleApplyZoomRatio(...)`, `evaluateLensNode(...)`, `SessionEffect.SwitchLensNode`, and `CameraSessionCoordinator`.
4. Verify the device path through `CameraXCaptureAdapter.switchLensNode(...)`, `cameraSelectorForLensNode(...)`, and `updateZoomRatio(...)`.
5. Pay special attention to current still-photo logic:
   - `updateZoomRatio(...)` returns early for `CaptureTemplate.STILL_CAPTURE`.
   - bind/rebind also avoids `cameraControl.setZoomRatio(...)` for still preview.
   - This may keep the still preview visually pinned to the full-lens path even when the session requested a threshold node.
6. Decide the smallest ownership-correct fix:
   - either rebind to the physical node and apply an appropriate preview zoom ratio;
   - or explicitly represent degraded digital preview when no physical node is available;
   - never move runtime switching into UI.

## Implementation Guidance

- Keep threshold and hysteresis decisions session/device-owned.
- Preserve existing slider label/snap and active-drag echo suppression behavior.
- Avoid preview jitter around 2x and 5x; if hysteresis is changed, update tests with exact boundary examples.
- Ensure still-photo preview behavior aligns with the new product rule. The previous full-lens still preview policy is no longer sufficient if it prevents the user from seeing `2x`/`5x` framing.
- For devices without physical `2x`/`5x` nodes, make the fallback explicit in state/diagnostics or status evidence; do not claim physical preview switching.

## Acceptance Criteria

- [ ] Dragging from below to above `2x` emits or reaches a session/device request for the `2x`/telephoto node when available.
- [ ] Dragging from below to above `5x` emits or reaches a session/device request for the `5x`/periscope node when available.
- [ ] Dragging back below the thresholds returns to the lower node without jitter.
- [ ] Still-photo preview no longer remains visually locked to the old full-lens preview when a physical node switch is available.
- [ ] Unsupported/degraded device behavior is explicit and testable.
- [ ] Existing slider, session, coordinator, and CameraX adapter tests pass.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, final commit hash.
- Changed files and `git diff --stat`.
- A before/after explanation of still-photo preview behavior around `2x` and `5x`.
- Test output summaries.
- A note saying which parts still require vivo X300 or equivalent real-device smoke.

## Unlock Condition

Mark completed only after focused zoom tests and assemble pass, or blocked with exact failing command and reason.
