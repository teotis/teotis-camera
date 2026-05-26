# Saved Photo Mask Rendering

## Goal

Use a saved-photo subject mask to improve Color Lab and portrait rendering: protect people/skin, allow stronger background color changes, and make foreground/background treatment more natural than global RGB shifts.

## Context

- User request: body/subject recognition should help both preview and postprocess.
- Verified facts:
  - `AppContainer` runs `CompositeMediaPostProcessor` in this order: multi-frame placeholder, document crop, frame ratio, portrait render, photo algorithm, watermark, selfie mirror, metadata.
  - `PhotoAlgorithmPostProcessor` currently applies `FilterRenderSpec` globally with RGB/contrast/saturation/highlight/shadow logic.
  - `PortraitRenderPostProcessor` already handles portrait metadata and lightweight subject/background-style rendering, but it does not use a real segmentation mask.
  - `MediaOutputHandle` now carries editable file/content URI when available; all saved-photo postprocessors depend on it.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
- Non-goals:
  - Do not change watermark rendering order.
  - Do not save mask image files unless a debug-only diagnostic mode already exists.
  - Do not claim depth/bokeh if only 2D person mask exists.
  - Do not require segmentation for all photos.

## Implementation Scope

- Add saved-photo mask backend:

```kotlin
internal interface SavedPhotoSceneMaskProvider {
    suspend fun createSubjectMask(
        bitmap: Bitmap,
        request: SavedPhotoSceneMaskRequest
    ): SceneMaskResult
}

internal sealed interface SceneMaskResult {
    data class Available(val mask: SceneMaskPayload) : SceneMaskResult
    data class Unavailable(val reason: String) : SceneMaskResult
    data class Failed(val reason: String) : SceneMaskResult
}
```

- Add `NoOpSavedPhotoSceneMaskProvider` and ML Kit implementation.
- Add mask-aware color renderer:
  - Keep old global path as fallback.
  - When subject mask exists:
    - Person/skin region: reduce warmth/tint/chroma shift, preserve luma.
    - Background region: allow stronger Color Lab chroma/tone shift.
    - Edge region: feather blend to avoid halos.
  - Add pipeline notes:
    - `scene-mask:saved=applied`
    - `scene-mask:saved=degraded:<reason>`
    - `color-render:subject-protected`
    - `color-render:background-adjusted`
- Decide integration point:
  - Preferred: extend `PhotoAlgorithmPostProcessor` to optionally consume `SavedPhotoSceneMaskProvider`.
  - If portrait renderer also needs it, share the provider but do not run segmentation twice for the same JPEG in one pipeline. Use a small per-shot in-memory cache keyed by `shotId + outputHandle`.

## Steps

1. Add provider interface and no-op implementation.
2. Add synthetic mask payload utilities in tests.
3. Extend `PhotoAlgorithmEditor` or introduce `MaskAwarePhotoAlgorithmEditor`.
4. Add per-pixel mask-aware rendering:
   - Sample mask alpha at corresponding bitmap pixel through `SceneMaskTransform`.
   - Compute subject weight and background weight.
   - Apply Color Lab deltas with subject protection.
   - Feather edge values using a small local blur or smoothstep on mask alpha.
5. Add ML Kit saved-photo implementation:
   - Use `SINGLE_IMAGE_MODE` or equivalent still-image mode.
   - Decode bitmap once if possible.
   - Treat low-confidence/no-person result as degraded fallback, not failure.
6. Update tests:
   - Masked subject patch changes less than background patch.
   - Neutral gray stays close to neutral.
   - Edge alpha blends smoothly.
   - No-op provider preserves old output path and adds degraded note.
   - Segmentation failure does not prevent downstream watermark/selfie mirror processors.

## Acceptance Criteria

- Saved JPEG Color Lab can be visibly stronger in background while keeping person/skin more natural.
- No-mask path remains behaviorally compatible with current `PhotoAlgorithmPostProcessor`.
- Low confidence or no person does not make capture fail.
- Pipeline notes explain whether mask was applied, degraded, unsupported, or failed.
- Downstream watermark and selfie mirror still run after mask-aware rendering.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- If `PhotoAlgorithmPostProcessor` and `PortraitRenderPostProcessor` both need masks, avoid independent duplicate inference. A simple shot-local provider cache is enough for first pass.
- The first mask is person-subject only. Do not use it as true depth.
- Mask edge quality will decide whether output feels premium. Synthetic unit tests are necessary but not sufficient; visual QA remains required.
