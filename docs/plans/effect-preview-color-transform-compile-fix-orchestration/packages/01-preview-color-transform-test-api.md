# Package 01 — Preview Color Transform Test API Compile Fix

## Problem

`rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-final ./scripts/verify_stage_6b3_watermark_v2.sh` now reaches the isolated build root correctly, but fails at:

```text
:core:effect:compileTestKotlin
Unresolved reference: PreviewColorMatrixBuilder
```

The references are in `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt`. Current production code exposes `PreviewColorTransform.fromSpec(...)`, `PreviewColorTransform.IDENTITY`, and `PreviewColorTransform.multiply(...)`; there is no `PreviewColorMatrixBuilder` symbol.

## Allowed Paths

- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
- `docs/plans/effect-preview-color-transform-compile-fix-orchestration/status/01-preview-color-transform-test-api.md`

## Forbidden Paths

- App UI/rendering files.
- Camera runtime files.
- Any unrelated package status file.
- Broad formatting or product behavior changes.

## Implementation Guidance

Choose the smallest coherent repair:

- Prefer updating tests to use the current API: `PreviewColorTransform.fromSpec(spec).matrix` and `PreviewColorTransform.IDENTITY.matrix`.
- If a builder abstraction is still the intended contract, reintroduce a small internal `PreviewColorMatrixBuilder` wrapper in `core:effect` that delegates to `PreviewColorTransform`; do not duplicate matrix math.
- Keep expectations aligned with existing `PreviewColorTransform` behavior. Default specs currently produce `PreviewColorTransform.IDENTITY`, not `null`, so update assertions deliberately rather than preserving stale API semantics.

## Verification

Run from the worktree with isolated Gradle:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-stage6b3-final ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Acceptance Criteria

- `:core:effect:compileTestKotlin` no longer fails on `PreviewColorMatrixBuilder`.
- Focused `PreviewColorTransformTest` and `PreviewEffectAdapterTest` pass.
- 6B3 watermark gate no longer stops at the effect test compile blocker.
- No runtime Color Lab behavior changes unless they are required to preserve current tested behavior.
