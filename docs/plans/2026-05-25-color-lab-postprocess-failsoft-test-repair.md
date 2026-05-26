# Color Lab Postprocess Fail-Soft And Test Repair

## Goal

Fix the current app focused test failures and harden Color Lab saved-photo postprocess so optional mask/render failures preserve the captured image with explicit degraded notes instead of causing animation-only no-image behavior.

## Context

- User request: after selecting Color Lab, tapping shutter has animation but no captured image.
- Validation evidence:
  - `PhotoAlgorithmPostProcessorTest` currently has multiple failures around exception preservation, saved mask available/unavailable/failed notes, and mask-aware fallback.
  - `CameraSessionCoordinatorTest` currently fails still quality/resolution bind rebind expectations.
  - `PhotoAlgorithmPostProcessor` still calls `maskBitmapSource.invoke(target)` outside the protected fail-soft block, so source/decode failure can escape before fallback.
- Related broader plans:
  - [`Rendering 2.0 Capture Save Reliability`](2026-05-25-rendering-2-0-capture-save-reliability.md)
  - [`Scene Mask Diagnostic Honesty Repair`](2026-05-25-scene-mask-diagnostic-honesty-repair.md)
  - [`Saved Mask Production Wiring`](2026-05-25-saved-mask-production-wiring.md)
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- Non-goals:
  - Do not hide real CameraX capture failures before a JPEG/result exists.
  - Do not remove raw feedback suppression for Color Lab/filter shots.
  - Do not tune Color Lab visual strength in this package.

## Implementation Scope

- Wrap all optional mask source/decode/render paths in fail-soft boundaries.
- Restore deterministic diagnostic notes expected by tests.
- Preserve original `ShotResult` fields when optional render fails.
- Investigate and fix or explicitly update still quality/resolution bind rebind behavior.

## Steps

1. Reproduce the focused failures:
   - `PhotoAlgorithmPostProcessorTest`;
   - `CameraSessionCoordinatorTest`.
2. In `PhotoAlgorithmPostProcessor`, identify every step that can throw after a valid `ShotResult` exists:
   - mask source lookup;
   - mask descriptor decode;
   - editor render;
   - mask-aware editor branch.
3. Move `maskBitmapSource.invoke(target)` and bitmap/decode handling inside a protected resolver that returns a structured available/unavailable/failed result.
4. Preserve or restore notes:
   - `algorithm-render:failed:render-exception` for editor exceptions;
   - `scene-mask:saved=unsupported` when no saved mask is available;
   - `scene-mask:saved=degraded` with reason when mask exists but cannot be used;
   - `scene-mask:preview=unsupported` until preview genuinely applies mask-aware rendering.
5. Ensure the fallback order is explicit:
   - mask-aware render when mask is valid and editor supports it;
   - global recipe render when mask is unavailable or unsupported;
   - original captured result when render fails.
6. For `CameraSessionCoordinatorTest` quality/resolution failures:
   - inspect whether `BindPreview` is no longer emitted after still quality/resolution changes;
   - if behavior regressed, restore the existing session/device bind contract;
   - if tests are stale, update them only with proof that the new behavior is intentional and covered elsewhere.
7. Add or adjust tests before implementation where practical, then make the smallest code change that turns the focused tests green.

## Acceptance Criteria

- Optional Color Lab/postprocess failures after capture never prevent `ShotCompleted` from reaching session.
- Original output handle/path/thumbnail source are preserved when postprocess degrades.
- Pipeline notes distinguish applied, unsupported, degraded, and failed states.
- `PhotoAlgorithmPostProcessorTest` and `CameraSessionCoordinatorTest` pass.
- No broad catch-all is added before camera capture success; fail-soft applies only after a valid raw result exists.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.color lab filtered photo suppresses raw capture feedback"
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- Catching `Throwable` is acceptable only at optional postprocess boundaries after a saved/captured result exists. Keep capture-critical failures visible.
- The coordinator rebind failures may be unrelated to Color Lab postprocess but still block validation; handle them in this package because they are part of the same failed app focused command.
