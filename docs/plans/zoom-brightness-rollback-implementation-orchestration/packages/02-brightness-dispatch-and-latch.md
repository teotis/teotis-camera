# Package 02 - Brightness Dispatch And Latch

## Package ID

`02-brightness-dispatch-and-latch`

## Goal

Fix the quick brightness slider rebound path by removing duplicate device dispatch and preventing render writes to `brightnessSlider.progress` while the user is actively dragging.

## Current Evidence

- `CameraSessionCoordinator.latestPreviewBrightnessCommand(...)` currently starts `previewBrightnessJob = scope.launch { cameraAdapter.dispatch(...) }` and also returns `DeviceCommand.ApplyPreviewBrightness(...)` to an outer `cameraAdapter.dispatch(...)`.
- `CockpitSurfaceRenderer.renderQuickBubble()` unconditionally writes `quickPanel.brightnessSlider.progress = brightness.steps - brightness.minSteps`.
- `MainActivityActionBinder` dispatches `SessionIntent.ApplyPreviewBrightness(...)` on user progress but `onStartTrackingTouch` / `onStopTrackingTouch` are no-ops.
- Session-side `PreviewBrightnessFeedback.requestId` stale filtering already exists and should be preserved.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` quick brightness render only
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` quick brightness binding only
- `app/src/main/java/com/opencamera/app/MainActivity.kt` callback/wiring only if needed
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` brightness sections only if needed
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt` brightness sections only if needed

## Forbidden Paths

- `core/session/**` unless a failing test proves session-side stale filtering is broken
- `core/device/**`
- zoom slider/gesture files
- unrelated quick panel rows
- `docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
- another package's status file

## Dependencies

- Depends on: none

## Implementation Requirements

1. Remove duplicate brightness dispatch.
   - Choose one dispatch path in `CameraSessionCoordinator`.
   - Prefer keeping `handleEffect` simple and ensuring exactly one `DeviceCommand.ApplyPreviewBrightness` reaches the adapter per session effect.
   - Keep latest-wins cancellation semantics if they are actually useful and testable.
2. Add active drag latch for quick brightness.
   - Set latch on `onStartTrackingTouch`.
   - Clear latch on `onStopTrackingTouch` / cancel.
   - Suppress renderer progress writes while latch is active.
   - On stop, dispatch final value if needed so the final finger position is committed.
3. Preserve request id stale filtering.
   - Do not remove `PreviewBrightnessFeedback` semantics.
4. Add tests.
   - Coordinator test: one effect -> exactly one device command.
   - UI/render tests or focused component tests that prove renderer does not write progress while drag latch is active, if testable without full Android instrumentation.

## Acceptance Criteria

- A single brightness request produces one device command, not two.
- Fast drag cannot be visually snapped back by renderer progress writes while the finger is down.
- Latest `REQUESTED` value remains the displayed model value.
- Stale `PreviewBrightnessApplied` results remain filtered by request id.
- Focused tests pass.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Expected Evidence

- worktree path
- branch
- base commit
- commit hash
- changed files
- diff stat
- focused test output summary
- whether a real-device CameraX latency smoke remains

