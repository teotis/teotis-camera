# Package 03 - Preview Fidelity Honesty

## Package ID

`03-preview-fidelity-honesty`

## Problem

The failed audit found that `PreviewOverlayView.applyColorTransformToPreview(...)` still leaves actual `PreviewView` color-filter application commented out behind a TODO. The UI can therefore claim matrix-level Color Lab preview fidelity while the real preview path only uses an approximation or no real transform.

## Goal

Make preview behavior match the claim. Either apply the supported preview color transform to the real preview path, or explicitly downgrade/report the path as an approximation/fallback so the product does not overclaim fidelity.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/**PreviewColorTransform*`
- `app/src/main/java/com/opencamera/app/camera/PreviewEffectAdapter.kt`
- `app/src/test/java/com/opencamera/app/**Preview*`
- `app/src/test/java/com/opencamera/app/**ColorTransform*`

Forbidden paths:
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `core/effect/**` unless a test-only fixture requires a shared value and is coordinated
- `docs/plans/**` except `docs/plans/rendering-2-0-validation-fix-orchestration-v2/status/03-preview-fidelity-honesty.md`

## Dependencies

None.

## Required Behavior

- If Android view APIs support the intended transform safely, apply it to the actual camera preview rendering path.
- If the platform path cannot apply the matrix faithfully, expose/report a degraded approximation state and ensure the UI/product text does not imply exact preview fidelity.
- Keep overlay/tint effects from tinting cockpit controls or unrelated UI.
- Preserve saved-output recipe flow; this package is about preview honesty, not saved postprocess.
- Add tests around supported, fallback, disabled, and UI-layer separation behavior where feasible.

## Acceptance Criteria

- The TODO/commented-out real preview transform no longer represents an unhandled product claim.
- Preview fidelity state is testable as supported or degraded.
- Cockpit controls and panel UI are not colored by preview-only effects.
- Existing preview startup and app assembly still pass.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

If exact test class names differ, run the nearest existing focused preview/color-transform tests and record the exact commands.

## Expected Evidence Pack

Write to `status/03-preview-fidelity-honesty.md`:
- worktree path and branch
- changed files and `git diff --stat`
- exact verification commands and pass/fail summaries
- whether the final behavior is exact preview transform or explicit degraded approximation
- commit hash or PR link
- self-certification that only allowed paths were touched
