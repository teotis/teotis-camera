# Package 01 - Effect Preview API Drift

## Package ID

`01-effect-preview-api-drift`

## Goal

Restore `core:effect` preview tests and the official Watermark V2 gate after API drift around `PreviewColorTransform` and `PreviewEffectRenderModel`.

## Context

- User request: check whether the external Watermark 2.0 landing is appropriate.
- Verified blocker: `rtk ./scripts/verify_stage_6b3_watermark_v2.sh` fails in `:core:effect:compileTestKotlin`.
- Current production API observed during validation:
  - `PreviewColorTransform` exposes `matrix` and `fidelity`.
  - `PreviewEffectRenderModel` no longer exposes `colorFidelity`.
- Current failing tests still reference removed or renamed API:
  - `PreviewColorTransform.colorMatrix`
  - `PreviewEffectRenderModel.colorFidelity`
- Relevant files:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`

## File Ownership

- Allowed paths:
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewColorTransform.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewColorTransformTest.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
  - narrowly related `core/effect` tests if needed to keep the contract coherent
- Forbidden paths:
  - `core/settings/**`
  - `app/src/main/java/**`
  - `docs/plans/**` except your assigned status file
  - `codex/documentation.md`

## Implementation Scope

- Inspect the current `core:effect` preview model contract.
- Choose the smallest honest repair:
  - Prefer updating tests to the current production API if `matrix` and `fidelity` are the intended names.
  - Only restore compatibility aliases if production callers actually require them and the aliases do not create duplicate truth.
- Keep preview fidelity semantics honest: do not reintroduce a `colorFidelity` field on `PreviewEffectRenderModel` unless the app truly consumes that top-level field.
- Add or adjust tests so the current model contract is explicit.
- Do not touch Watermark rendering or settings behavior in this package.

## Acceptance Criteria

- `PreviewColorTransformTest` compiles and passes.
- `PreviewEffectAdapterTest` compiles and passes.
- `:core:effect:test` passes for the touched tests.
- The official Watermark V2 gate no longer stops at `:core:effect:compileTestKotlin`.
- No compatibility alias hides contradictory names unless the test documents it as a deliberate API bridge.

## Verification Commands

External agents working in a worktree MUST use isolated Gradle:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test
```

If your worktree needs to run the official script, use an explicit build root:

```bash
rtk env OPENCAMERA_BUILD_ROOT=/private/tmp/opencamera-watermark2-effect ./scripts/verify_stage_6b3_watermark_v2.sh
```

## Expected Evidence Pack

- Diff summary of any production API or test changes.
- The exact failing compile symbols before the fix.
- Test result summary for the focused `core:effect` commands.
- Note whether the official 6B3 script progressed beyond `:core:effect:compileTestKotlin`.

## Risks And Notes

- This is a contract repair, not a Watermark renderer feature.
- Avoid broad Color Lab or rendering changes here; this package exists only because the official Watermark gate depends on `core:effect` tests.

