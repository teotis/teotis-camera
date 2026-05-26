# Package 03 — Preview Fidelity Honesty

## Package ID

`03-preview-fidelity-honesty`

## Goal

Make preview Color Lab claims match actual app rendering. The current code builds matrices in core, but `PreviewOverlayView.applyColorTransformToPreview(...)` has the real `PreviewView` color-filter application commented out; the visible path is a tint overlay fallback.

## Context

- Verified facts:
  - `PreviewEffectAdapter` can produce `PreviewColorTransform` from `FilterRenderSpec` and recipe.
  - `PreviewOverlayView` draws a tint overlay via `previewColorTransformOverlaySpec(...)`.
  - The actual `PreviewView` matrix application is disabled with `TODO: re-enable when PreviewView.paint API is available`.
  - Current docs can overclaim preview matrix fidelity if they say the matrix is applied to preview content.
- Relevant files:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
  - `app/src/main/java/com/opencamera/app/PreviewColorTransformOverlay.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/test/java/com/opencamera/app/PreviewColorTransformOverlayTest.kt`
- Non-goals:
  - Do not build a full GL/OES preview renderer.
  - Do not add advanced Color Lab controls.
  - Do not touch saved JPEG postprocess logic.

## File Ownership

- Allowed paths:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
  - `app/src/main/java/com/opencamera/app/PreviewColorTransformOverlay.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/test/java/com/opencamera/app/PreviewColorTransformOverlayTest.kt`
- Forbidden paths:
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - orchestration `INDEX.md`

## Implementation Scope

- Either implement a real supported preview transform application path, or explicitly downgrade matrix-only preview transforms to fallback/tint fidelity at the app boundary.
- Ensure tint overlay is applied only to the preview content area and not to cockpit controls.
- Adjust tests and docs so they do not claim matrix parity when only tint overlay is visible.
- Preserve visual fallback for devices/API paths where exact preview transform cannot be applied.

## Acceptance Criteria

- Tests prove `PreviewColorTransform` is visible through the actual app overlay path.
- If matrix application remains unsupported, the model/status reports degraded or fallback fidelity rather than `GOOD`/exact parity.
- No code comment claims an applied matrix when the application is still disabled.
- Preview controls and cockpit UI are not tinted.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.PreviewColorTransformTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformOverlayTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack

- Diff showing either real preview transform application or truthful fallback labeling.
- Test summary for core effect and app overlay tests.
- Note whether final real-device visual QA is still required.

## Risks And Notes

- Applying color filters to `PreviewView` may be unsupported or ineffective depending on implementation. Do not claim exact preview parity unless verified.

