# Package 01 - Postprocess Outer Guard

## Package ID

`01-postprocess-outer-guard`

## Problem

The failed audit found that `CameraXCaptureAdapter.emitShotCompleted(...)` still calls `mediaPostProcessor.process(rawResult)` without an outer fallback guard. If optional render/postprocess work throws above the composite processor boundary, the capture completion path can fail instead of preserving the raw saved media result.

## Goal

Make capture save reliability explicit: optional rendering/postprocess failures must preserve the saved raw media result, append a degraded pipeline note, and continue completion/feedback with no silent no-image behavior.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/test/java/com/opencamera/app/camera/**`
- `core/media/src/main/**`
- `core/media/src/test/**`

Forbidden paths:
- `core/effect/**`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `docs/plans/**` except `docs/plans/rendering-2-0-validation-fix-orchestration-v2/status/01-postprocess-outer-guard.md`

## Dependencies

None. Coordinate with package 02 only if both need the same app camera test file.

## Required Behavior

- Wrap the app-level call to `mediaPostProcessor.process(rawResult)` in a guard that catches processor failures.
- On failure, return the original `rawResult` with a pipeline note such as `postprocess:failed:composite` or a more specific equivalent.
- Preserve the original media handle/path, capture timing, live-photo bundle fields, and existing diagnostics.
- Do not move camera runtime ownership out of the Session Kernel or Device Adapter contracts.
- Do not hide the failure; it must be visible in pipeline notes/diagnostics.

## Acceptance Criteria

- A throwing postprocessor cannot prevent `ShotCompleted` emission when the raw media result exists.
- The fallback result is the raw saved media result plus a degraded/failure note.
- Existing success postprocess behavior remains unchanged.
- Focused tests cover the new fallback path.
- All commands are run through `rtk`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.CompositeMediaPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

If a worktree is used, run Gradle through:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
```

## Expected Evidence Pack

Write to `status/01-postprocess-outer-guard.md`:
- worktree path and branch
- changed files and `git diff --stat`
- exact verification commands and pass/fail summaries
- fallback note name used
- commit hash or PR link
- self-certification that only allowed paths were touched
