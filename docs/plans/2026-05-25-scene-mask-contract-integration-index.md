# Scene Mask Contract Integration Index

## Goal
Turn the current Scene Mask prototype landing into one coherent product pipeline: `core/media` owns the canonical contract, app-level ML Kit code becomes an adapter, saved-photo rendering is actually wired in production, and preview/saved behavior can be compared through shared metadata.

## Context
- User request: verify the external-agent claim that the project now has two incompatible scene mask type systems, then split a safe implementation plan.
- Verified facts:
  - `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt` defines canonical-looking `SceneMaskDescriptor`, `SceneMaskPayload`, `SceneMaskTransform`, `SceneMaskCapability`, and pipeline notes.
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt` and `SavedPhotoSceneMaskProvider.kt` define same-named app-local types with different semantics.
  - `AppContainer.kt` wires `NoOpSavedPhotoSceneMaskProvider()` into `PhotoAlgorithmPostProcessor`, so saved-photo ML Kit masking is not active in the production composition.
  - `PhotoAlgorithmPostProcessor.readSourceBytesForMask()` returns `null` for `ProcessorTarget.ContentUri`, which means common Android save targets cannot produce saved-photo masks.
  - `CameraXCaptureAdapter` sends analysis frames to `PreviewSceneMaskSource`, but `latestMask()` is not consumed by the Color Lab / preview render path.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/SceneMaskContractsTest.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
- Non-goals:
  - Do not introduce cloud segmentation.
  - Do not store mask pixels in `SessionState` or persisted settings.
  - Do not redesign Color Lab UI in this package.
  - Do not remove the `core/media` contract unless a compile blocker proves it unusable.

## Recommended Execution Order
1. `2026-05-25-scene-mask-contract-canonicalization.md`
2. `2026-05-25-saved-mask-production-wiring.md`
3. `2026-05-25-preview-saved-mask-consistency.md`

## Delegation Model
- Non-multimodal agents can implement the first two packages and most of the third package because they are code/test/refactor tasks.
- Codex/user should retain final visual acceptance: compare real preview behavior and saved JPEG output on the provided day/night images or real-device shots.

## Acceptance Criteria
- App code no longer defines confusing public/internal same-name `SceneMaskCapability`, `SceneMaskPayload`, or `SceneMaskTransform` types with semantics that conflict with `core.media`.
- Saved-photo mask provider is either truly wired or explicitly reported as unsupported/degraded through the canonical capability contract.
- `ContentUri` output targets can participate in saved-photo mask rendering.
- Preview masks and saved-photo masks both expose comparable descriptor/quality/backend metadata.
- Verification commands in each package pass.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.SceneMaskContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes
- ML Kit segmentation is local/on-device, but dependency resolution may still need local Gradle cache availability.
- `ImageProxy` ownership in the analysis fanout should be checked carefully. The scene mask source currently closes the image while the live preview source may also expect to read/close it.
- If a change touches `CameraXCaptureAdapter`, also run the relevant CameraX adapter tests and consider the Stage 7 observability script.
