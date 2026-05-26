# Portrait 2.0 Landing Validation Blockers

## Goal

修复外部 agent 落地后当前阻塞人像 2.0 验收的问题。当前 settings/effect 层的 `portraitDepthStrength`、metadata 和 `PortraitLayeredEffectResolver` 基本落地，但 app 后处理层仍未通过原计划门禁：mask-aware 成片测试失败、scene mask 边缘柔化测试失败，并且静态代码显示 mask-aware 路径修改的 bitmap 没有写回最终 JPEG。

## Context

- User request: 文档已经交付外部 agent 完成，现在验证项目中当前落地效果，发现 blocking 则使用 `agent-handoff-planner`。
- Original package:
  - [Portrait 2.0 Approved Upgrade Index](./2026-05-25-portrait-2-0-approved-upgrade-index.md)
  - [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md)
  - [Portrait 2.0 Depth Slider And Bokeh Strength](./2026-05-25-portrait-2-0-depth-slider-and-bokeh-strength.md)
  - [Portrait 2.0 Background Light Spot And Highlight Shape](./2026-05-25-portrait-2-0-background-light-spot-highlight.md)
- Verified facts:
  - `rtk git status --short` is not usable in this workspace because Git reports `fatal: not a git repository: /Volumes/Extreme_SSD/project/codex_camera/.git/worktrees/orientation-adaptive-camera-ui`.
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest --tests com.opencamera.core.effect.PortraitLayeredEffectResolverTest` passed.
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest` failed.
  - Failing tests:
    - `PortraitRenderPostProcessorTest > available mask routes to mask-aware editor`
    - `PortraitRenderPostProcessorTest > mask-aware path preserves portrait mode metadata and downstream result`
    - `SceneMaskPayloadTest > mask with edge softness has smooth transition`
  - `PortraitRenderPostProcessor.process(...)` calls `editor.applyWithMask(maskResult.first, payload.spec, maskResult.second)` and then recycles `maskResult.first`, but the production `AndroidPortraitRenderEditor.applyWithMask(...)` only mutates that decoded bitmap and does not encode/write it back to `ProcessorTarget`.
  - `AndroidPortraitRenderEditor.applyWithMask(...)` also does not call `applyLightSpotEffect(...)`, so the mask-aware path can emit `portrait-layer:light-spot=...` without actually applying or recording `portrait-light-spot:applied/degraded` notes.
  - The failing mask-aware tests pass `maskBitmapSource = { null }`, so the provider is never asked for a mask; the tests currently encode a contradictory setup rather than proving production behavior.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/MaskAwarePortraitRenderEditor.kt`
  - `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt`
  - `app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/SceneMaskPayloadTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/SceneMaskTestUtils.kt`
  - `app/src/test/java/com/opencamera/app/camera/MaskAwarePortraitRenderMathTest.kt`
- Non-goals:
  - Do not change the already-passing settings/effect APIs unless required by compilation.
  - Do not add real-time preview portrait rendering.
  - Do not introduce vendor SDKs or true depth claims.
  - Do not hide failing tests by loosening assertions without making the product path real.

## Implementation Scope

- Fix mask-aware saved rendering so successful mask-aware edit is persisted:
  - either make `MaskAwarePortraitRenderEditor.applyWithMask(...)` accept the `ProcessorTarget` and own encode/write/EXIF restore, matching `PortraitRenderEditor.apply(...)`;
  - or make `PortraitRenderPostProcessor` encode/write the mutated bitmap after `applyWithMask(...)` returns.
- Prefer the first option if it keeps content URI/file writing and EXIF restoration in one editor owner.
- Ensure mask-aware path applies or honestly skips light spots:
  - call the same light-spot renderer after mask-aware subject/background blending; or
  - emit explicit degraded notes such as `portrait-light-spot:degraded:mask-path-not-supported` until it is implemented.
- Fix the test setup:
  - mask-aware routing tests must provide a real small bitmap from `maskBitmapSource`, or use a temp JPEG file, so `resolveMask(...)` can call the fake provider.
  - add a real persistence test: write a tiny JPEG fixture, run `PortraitRenderPostProcessor` with an available mask and a production or near-production editor, then verify output bytes/pixels changed and notes reflect the applied path.
- Fix `SceneMaskTestUtils.createCenterSubjectMask(...)` or the test coordinate assumptions so `edgeSoftness` produces a non-trivial transition at the asserted edge sample.
- Keep fallback behavior:
  - no provider, unavailable mask, failed mask, decode failure, and mask-render exception must not fail capture.
  - each fallback must add clear degraded/fallback notes.

## Steps

1. Reproduce the current failures with:
   ```bash
   rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
   ```
2. Add or repair tests before changing production code:
   - available mask routes to mask-aware editor with a non-null bitmap source;
   - mask-aware path writes changed JPEG bytes/pixels to the output target;
   - mask-aware path includes `portrait-mask:saved=applied`, `portrait-render:subject-mask`, `portrait-render:applied:depth|focus`, and truthful light-spot notes;
   - unavailable/failed/no-provider paths still fall back and preserve capture success;
   - edge softness creates a measurable center-to-edge-to-corner alpha transition.
3. Refactor `MaskAwarePortraitRenderEditor` or `PortraitRenderPostProcessor` so one component clearly owns output writing for mask-aware rendering.
4. Ensure `AndroidPortraitRenderEditor.applyWithMask(...)` preserves EXIF the same way `apply(...)` does, or document and test any intentional difference.
5. Rerun app focused tests, then the package-level commands below.
6. Update [Portrait 2.0 Approved Upgrade Index](./2026-05-25-portrait-2-0-approved-upgrade-index.md) from `blocked` to `implemented` only after the focused app tests pass.

## Acceptance Criteria

- Focused app tests for `PortraitRenderPostProcessorTest` and `SceneMaskPayloadTest` pass.
- Mask-aware saved portrait rendering changes the actual output JPEG, not only an in-memory decoded bitmap.
- Mask-aware path applies or truthfully degrades light-spot behavior.
- Pipeline notes distinguish mask-applied, fallback-focus, no-provider, unavailable, failed, and light-spot applied/degraded states.
- No fallback path fails capture solely because segmentation is unavailable.
- The existing passing settings/effect tests remain green.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest --tests com.opencamera.core.effect.PortraitLayeredEffectResolverTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- This is a product-blocking issue because the UI/metadata can claim `mask-aware` or `light-spot` behavior while saved pixels may not change.
- Avoid duplicating JPEG write logic in both normal and mask-aware paths without tests. A small internal shared helper in `AndroidPortraitRenderEditor` is acceptable.
- The current core effect resolver and app renderer still duplicate some product math. That is a follow-up architecture gap, but the immediate blocker is saved output correctness and failing app tests.
