# Scene Mask Diagnostic Honesty Repair

## Goal

Make scene-mask diagnostics and metadata accurately describe what the current pipeline actually did. Saved-photo mask success, fallback, failure, and preview-mask availability must not be overstated, so downstream agents and real-device QA cannot mistake a partial landing for a complete product path.

## Context

- User request: 核验近期方案共同触及的核心链路，防止功能有入口但底层处理不当。
- Verified facts:
  - `AppContainer.kt` already wires `MlKitSavedPhotoSceneMaskProvider` into `PhotoAlgorithmPostProcessor`.
  - `PhotoAlgorithmPostProcessor` can apply mask-aware saved-photo Color Lab through `MaskAwarePhotoAlgorithmEditor`.
  - On saved mask success, `PhotoAlgorithmPostProcessor` currently builds `SceneMaskCapability(... previewMask = SUPPORTED ...)`, even though preview mask is not consumed by the preview color/render path.
  - When saved mask is unavailable or failed, `resolveMask(...)` returns `null` and the postprocessor falls back to global rendering without adding explicit `scene-mask:saved=degraded|unsupported|failed` notes.
  - `PreviewSceneMaskSource.latestMask()` exists, but current production preview render path does not consume it.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- Non-goals:
  - Do not implement preview mask rendering in this package.
  - Do not add portrait effect-layer contracts.
  - Do not move mask pixels into `ShotResult.metadata`, `SessionState`, or persisted settings.

## Implementation Scope

- Replace the unconditional success capability notes in `PhotoAlgorithmPostProcessor` with notes that distinguish:
  - saved-photo mask applied;
  - saved-photo mask unavailable;
  - saved-photo mask failed;
  - preview mask unsupported/not-consumed unless a real preview consumer exists.
- Preserve existing global render fallback when no saved mask is available.
- Add tests proving that unavailable/failed saved masks do not silently look like a no-mask ordinary path.
- Keep `SceneMaskDescriptor` metadata tags only for a real available mask.

## Steps

1. Inspect `PhotoAlgorithmPostProcessor.process(...)` and `resolveMask(...)`.
2. Introduce a small internal result type for mask resolution, for example:

```kotlin
private sealed interface ResolvedSavedMask {
    data class Available(val bitmap: Bitmap, val mask: SavedPhotoMaskPixels) : ResolvedSavedMask
    data class Unavailable(val reason: String) : ResolvedSavedMask
    data class Failed(val reason: String) : ResolvedSavedMask
    data object NotRequested : ResolvedSavedMask
}
```

3. Update processing behavior:
   - `Available`: use `applyWithMask(...)`, add `scene-mask:saved=applied`, do not claim `previewMask = SUPPORTED` unless a real preview path is injected.
   - `Unavailable`: run global render fallback and add `scene-mask:saved=unsupported` or `scene-mask:saved=degraded` plus `scene-mask:reason=<reason>`.
   - `Failed`: run global render fallback and add `scene-mask:saved=degraded` plus failure reason.
   - `NotRequested`: keep legacy behavior without extra scene-mask notes.
4. Add or update fake providers in `PhotoAlgorithmPostProcessorTest`.
5. Assert metadata tags from `SceneMaskDescriptor.toMetadataTags()` are present only when a mask was actually available.
6. Run verification.

## Acceptance Criteria

- Saved-photo mask success never writes `previewMask = SUPPORTED` unless preview mask rendering has a production consumer.
- Saved-photo mask unavailable/failed paths add explicit scene-mask notes while still preserving capture success.
- No-mask legacy path remains behaviorally compatible when no mask provider is configured.
- Tests cover available, unavailable, failed, and no-provider paths.
- Pipeline notes are sufficient for real-device QA to tell mask-applied from fallback.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Do not make segmentation required for capture. The correct behavior on provider failure is honest fallback, not shot failure.
- Keep note vocabulary aligned with `SceneMaskPipelineNotes` where practical, but do not force `SceneMaskCapability` to represent a mixed state that is not true.
- If this package lands before preview mask rendering, record preview as unsupported/not-consumed rather than degraded support.

