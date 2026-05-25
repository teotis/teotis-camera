# Saved Photo Mask Production Wiring

## Goal
Make saved-photo subject masks actually participate in production post-processing when supported, while keeping honest degraded/unsupported behavior when ML Kit or input access is unavailable.

## Context
- User request: evaluate whether current Scene Mask landing is appropriate.
- Verified facts:
  - `MlKitSavedPhotoSceneMaskProvider` exists but `AppContainer` passes `NoOpSavedPhotoSceneMaskProvider()` to `PhotoAlgorithmPostProcessor`.
  - `PhotoAlgorithmPostProcessor.resolveMask()` can call a provider and `MaskAwarePhotoAlgorithmEditor.applyWithMask()`, so the shape exists.
  - `PhotoAlgorithmPostProcessor.readSourceBytesForMask()` returns `null` for `ProcessorTarget.ContentUri`, preventing common Android saved outputs from being masked.
  - `AndroidPhotoAlgorithmEditor` already knows how to read/write both file path and content URI targets for normal algorithm rendering.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/MaskAwarePhotoAlgorithmEditor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- Non-goals:
  - Do not require internet or cloud segmentation at runtime.
  - Do not make saved-photo mask mandatory for capture success.
  - Do not change filter/color recipe math except where mask notes are passed through.

## Implementation Scope
- Wire `MlKitSavedPhotoSceneMaskProvider` in `AppContainer` with a guarded fallback to `NoOpSavedPhotoSceneMaskProvider`.
- Extend `PhotoAlgorithmPostProcessor` so mask source bytes can be read from `ProcessorTarget.ContentUri`, likely by passing a `ContentResolver` or a small `ProcessorTargetBytes` helper into the processor.
- Ensure unavailable/failed mask creation adds clear pipeline notes instead of silently behaving like the feature does not exist.
- Include canonical `SceneMaskPipelineNotes` or descriptor metadata from `core/media` after the contract canonicalization package lands.
- Add tests for:
  - provider available -> `applyWithMask()` path is used.
  - provider unavailable -> normal `apply()` path still works and notes record degradation.
  - content URI target -> mask decode path can read bytes.
  - provider exception -> output is not corrupted and failure note is recorded.

## Steps
1. Inspect current post-processor tests and fake editors/providers.
2. Introduce a minimal target-byte reader abstraction if direct `ContentResolver` injection would make tests cleaner.
3. Update `PhotoAlgorithmPostProcessor` constructor and `AppContainer` composition.
4. Replace production `NoOpSavedPhotoSceneMaskProvider()` with guarded ML Kit provider creation.
5. Add or update tests for FilePath and ContentUri mask source access.
6. Add pipeline notes for `scene-mask:saved=applied/degraded/unsupported`.

## Acceptance Criteria
- Production app composition no longer hardcodes `NoOpSavedPhotoSceneMaskProvider()` when ML Kit saved segmentation is available.
- Content URI saved-photo outputs can be decoded for mask generation in tests.
- Saved-photo mask success uses `MaskAwarePhotoAlgorithmEditor.applyWithMask()`.
- Saved-photo mask failure degrades to normal rendering with explicit notes.
- `:app:assembleDebug` passes.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes
- If `MlKitSavedPhotoSceneMaskProvider` is long-lived, define ownership for `close()` or make lifecycle disposal explicit.
- Avoid decoding the same large JPEG more times than necessary. A later optimization can reuse the decoded bitmap across mask creation and algorithm rendering, but this package should first make correctness observable.
- If ML Kit is missing at runtime, fallback must be honest and non-crashing.
