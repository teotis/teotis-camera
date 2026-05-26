# Package 01 — Effect Preview Contract/Test Repair

## Package ID
`01-effect-test-contract`

## Problem Statement

Main currently assembles, but `:core:effect:test --tests PreviewColorTransformTest --tests PreviewEffectAdapterTest` does not compile. The tests still reference old fields (`colorMatrix`, nullable `colorTransform`, `colorFidelity`) while production code now exposes `PreviewColorTransform(matrix, tintColor, tintAlpha, fidelity)` and `PreviewEffectRenderModel.colorTransform`.

## Acceptance Criteria

- [ ] `PreviewColorTransformTest` compiles and validates the current public contract without obsolete `colorMatrix` references.
- [ ] `PreviewEffectAdapterTest` compiles and validates the current public contract without obsolete `colorFidelity` references.
- [ ] Tests distinguish matrix-based transforms from tint-only/recipe transforms.
- [ ] No production code claims `PreviewColorFidelity.GOOD` unless a non-identity matrix exists and package 02 can actually apply or honestly downgrade it.
- [ ] No TODO or commented-out implementation is introduced.
- [ ] Required verification passes.

## File Ownership

Allowed paths:
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`

Forbidden paths:
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/PreviewColorTransformOverlay.kt`
- `app/src/main/java/com/opencamera/app/camera/`
- `core/session/`
- `feature/`

## Dependencies

None.

## Parallel Safety

Safe with package 05. Do not run concurrently with package 02.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Expected Evidence Pack

- Exact test compile errors fixed.
- Changed contract fields summarized.
- Verification output with pass/fail counts.
- Self-certification that only allowed paths were touched.
