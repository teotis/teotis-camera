# Package 01 — Postprocess Outer Guard

## Package ID

`01-postprocess-outer-guard`

## Goal

Make capture save reliability honest at the adapter boundary: if a valid `rawResult` exists and optional postprocess throws outside the composite wrapper, `CameraXCaptureAdapter` must still emit `ShotCompleted` with the original saved media and an explicit degraded note.

## Context

- User request: verify external Rendering 2.0 landing and package remaining issues.
- Verified facts:
  - `CompositeMediaPostProcessor` catches per-processor failures.
  - `CameraXCaptureAdapter.emitShotCompleted(...)` still calls `mediaPostProcessor.process(rawResult)` without an outer guard.
  - The original handoff explicitly required a composite-level fallback note such as `postprocess:failed:composite`.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/CompositeMediaPostProcessorTest.kt`
- Non-goals:
  - Do not mask capture failures before a `rawResult` exists.
  - Do not change processor order unless a test proves it is required.

## File Ownership

- Allowed paths:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/CompositeMediaPostProcessorTest.kt`
  - focused tests that directly exercise this behavior
- Forbidden paths:
  - `core/effect/**`
  - `core/settings/**`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - orchestration `INDEX.md`

## Implementation Scope

- Wrap `mediaPostProcessor.process(rawResult)` in `emitShotCompleted(...)`.
- On failure, continue with `rawResult.addPipelineNotes("postprocess:failed:composite")`.
- Preserve output path, output handle, thumbnail source, live bundle semantics, and timing.
- Add focused test coverage if a practical adapter-level test seam exists; otherwise add a small extracted pure helper around the fallback decision and test that.

## Acceptance Criteria

- A composite-level postprocess exception after valid capture does not prevent `ShotCompleted`.
- The original output handle/path remain unchanged.
- Pipeline notes include `postprocess:failed:composite`.
- Per-processor fail-soft tests still pass.
- No device capture failure path is converted into success.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.CompositeMediaPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

- Diff showing the adapter-level outer guard.
- Test result summary for the commands above.
- Explicit note whether adapter-level behavior is directly tested or covered through an extracted helper.

## Risks And Notes

- Catching broad exceptions is acceptable only after valid saved media exists.
- If a direct test requires too much CameraX setup, keep the production change tiny and test the fallback helper.

