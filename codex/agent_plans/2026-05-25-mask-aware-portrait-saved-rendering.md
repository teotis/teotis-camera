# Mask Aware Portrait Saved Rendering

Status: planned as part of [Portrait 2.0 Approved Upgrade Index](./2026-05-25-portrait-2-0-approved-upgrade-index.md).

## Goal

Upgrade saved-photo portrait rendering so it uses a person subject mask to split foreground and background. Person region should receive beauty/skin protection; background should receive bokeh, bloom, light-spot, and style treatment. When mask is unavailable, the existing focus/ellipse render remains as an honest degraded fallback.

## Context

- User request: 人像模式重开应基于人像和背景识别拆分，然后分别进行光斑、滤镜、美颜等后处理。
- Verified facts:
  - `PortraitRenderPostProcessor.kt` already resolves `PortraitRenderSpec` from portrait metadata.
  - `AndroidPortraitRenderEditor.applyPortraitRender(...)` currently computes subject/background from an ellipse around a fixed focus center.
  - `SavedPhotoSceneMaskProvider`, `SceneMaskResult`, and `SavedPhotoMaskPixels` already exist.
  - `PhotoAlgorithmPostProcessor` already shows one app-layer pattern for optional saved mask provider and mask-aware editor fallback.
  - `AppContainer.kt` wires `MlKitSavedPhotoSceneMaskProvider` only into `PhotoAlgorithmPostProcessor`, not into `PortraitRenderPostProcessor`.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/SceneMaskPayloadTest.kt`
- Non-goals:
  - Do not remove the existing ellipse/focus fallback.
  - Do not claim true depth from a 2D person mask.
  - Do not run segmentation twice in the same saved-photo pipeline when Color Lab and Portrait both need a mask.
  - Do not change watermark/selfie mirror ordering.

## Implementation Scope

- Extend portrait render with an optional provider:

```kotlin
internal interface MaskAwarePortraitRenderEditor : PortraitRenderEditor {
    suspend fun applyWithMask(
        bitmap: Bitmap,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<ProcessorEditorResult, List<String>>
}
```

- Update `PortraitRenderPostProcessor` to:
  - decode the editable JPEG target for mask creation;
  - request `SavedPhotoSceneMaskProvider.createSubjectMask(...)`;
  - use `MaskAwarePortraitRenderEditor` when mask is available;
  - fall back to `PortraitRenderEditor.apply(...)` when unavailable/failed/no provider;
  - add notes such as `portrait-mask:saved=applied`, `portrait-mask:saved=degraded:<reason>`, `portrait-render:subject-mask`, `portrait-render:fallback-focus`.
- Add a small shot-local cache only if both portrait and algorithm postprocessors would otherwise call the provider separately. The cache can live in app media pipeline wiring; do not put bitmap/mask payload in `ShotResult.metadata`.
- Implement mask-aware render:
  - `subjectWeight = smoothstep(...)` from mask alpha.
  - Person region: preserve detail, reduce color cast, apply beauty smoothing/lift conservatively.
  - Background region: blur/bloom/light spot styling, stronger bokeh effect, profile-specific glow.
  - Edge region: feather blend to avoid halos.

## Steps

1. Add red tests to `PortraitRenderPostProcessorTest`:
   - available mask routes to mask-aware editor;
   - unavailable/failed mask falls back and records degraded note;
   - mask-aware path preserves portrait mode metadata and downstream result;
   - non-portrait photos still ignored.
2. Add synthetic mask editor tests:
   - center subject remains sharper/less blurred than background;
   - background receives stronger bloom/blur;
   - edge alpha blends smoothly.
3. Update `PortraitRenderPostProcessor` constructor and `AppContainer.kt` wiring.
4. Implement `AndroidPortraitRenderEditor.applyWithMask(...)`.
5. Avoid duplicate inference if `PhotoAlgorithmPostProcessor` is also mask-aware in the same capture. If a shared cache is too broad for this package, document it as a follow-up and add pipeline notes proving duplicate calls are not hidden.
6. Ensure fallback behavior is identical enough to existing tests when provider is absent.

## Acceptance Criteria

- Portrait saved JPEG can use a real person mask for subject/background separation.
- Mask unavailable/no person/failed inference does not fail capture.
- Pipeline notes distinguish mask-applied from fallback-focus/degraded.
- Existing portrait metadata/profile/beauty/bokeh settings still resolve.
- Watermark and selfie mirror still run after portrait render.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- A 2D person mask is not a depth map. Use language like `subject-mask` or `background-separation`, not true depth.
- ML Kit selfie segmentation may produce weak hair/hand edges. Synthetic tests can guard math, but visual QA must judge product quality.
- If content URI decoding/writing is touched, rerun saved-photo postprocessor tests for watermark/frame/selfie mirror because they share the editable output handle.
