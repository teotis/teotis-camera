# Package 02 — Color Lab Preview Execution and Fidelity Honesty

## Package ID
`02-color-lab-preview-execution`

## Problem Statement

The current code constructs a `ColorMatrixColorFilter` but does not apply it to preview pixels. In `PreviewOverlayView`, `pv.paint.colorFilter = filter` is commented out, while comments still say the matrix is applied to TextureView mode. This leaves problem 15 unresolved: Color Lab preview remains a tint overlay approximation and may still look weaker than saved output.

## Acceptance Criteria

- [ ] Remove the false application path and any TODO/commented-out preview color implementation.
- [ ] Either actually apply a matrix/tint transform to preview pixels through a verified Android API path, or mark the preview as `APPROXIMATE`/`DEGRADED` and strengthen only the honest overlay path.
- [ ] If `PreviewColorFidelity.GOOD` is used, there must be code and tests showing the transform reaches preview pixels, not merely an overlay.
- [ ] Overlay fallback must be bounded to the active preview/frame area and must not tint UI chrome.
- [ ] Color Lab preview tests cover matrix transform, recipe tint transform, identity/no-op behavior, and fallback fidelity.
- [ ] App compile, focused app overlay tests, and core effect tests pass.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/PreviewColorTransformOverlay.kt`
- `app/src/main/res/layout/activity_main.xml` only if implementation mode changes are required and tested
- `app/src/test/java/com/opencamera/app/PreviewColorTransformOverlayTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` only for color-overlay interaction tests
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`

Forbidden paths:
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `core/settings/`
- `core/session/`
- `feature/`
- focal slider files

## Dependencies

Must wait for package 01.

## Parallel Safety

Caution with package 03 because both may touch `PreviewOverlayView.kt`. Prefer package 02 after 01 and before 03, or rebase carefully.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewColorTransformOverlayTest --tests com.opencamera.app.PreviewOverlayGeometryTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Expected Evidence Pack

- Whether preview color is real pixel transform or honest overlay fallback.
- The fidelity states used and why.
- Before/after references to removed false path.
- Test output.
