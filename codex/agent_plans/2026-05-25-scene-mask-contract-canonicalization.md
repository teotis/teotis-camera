# Scene Mask Contract Canonicalization

## Goal
Make `core/media` the single semantic contract for scene masks while keeping app-level ML Kit and Android buffer code as adapters. The result should remove same-name type ambiguity and make future preview/saved Color Lab work consume one shared language.

## Context
- User request: decide whether to rename/bridge the two existing type systems or roll back the core contract.
- Decision: keep `core/media` as canonical, then rename or adapt app-local implementation types.
- Verified facts:
  - `core.media.SceneMaskPayload` is an interface with `descriptor` and `alphaAt(maskX, maskY)`.
  - app `SceneMaskPayload` is a concrete `IntArray` saved-photo mask with `sampleAlpha(x, y)`.
  - core `SceneMaskTransform` is metadata; app `SceneMaskTransform` is a coordinate mapper.
  - app `SceneMaskCapability` is a preview runtime enum; core `SceneMaskCapability` is a multi-surface capability data class.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/SceneMaskContractsTest.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/camera/SavedPhotoSceneMaskProvider.kt`
  - `app/src/test/java/com/opencamera/app/camera/SceneMaskTestUtils.kt`
  - `app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- Non-goals:
  - Do not move Android `Bitmap`, `ImageProxy`, ML Kit, or byte-buffer logic into `core/media`.
  - Do not change visible Color Lab behavior in this package.
  - Do not introduce a second media/session owner for mask state.

## Implementation Scope
- Rename app-local types whose names conflict with canonical `core.media` concepts:
  - `app.camera.SceneMaskCapability` -> `PreviewSceneMaskRuntimeStatus` or `PreviewSceneMaskAvailability`.
  - `app.camera.SceneMaskPayload` -> `SavedPhotoSubjectMask` or `BitmapSubjectMaskPayload`.
  - `app.camera.SceneMaskTransform` -> `SceneMaskCoordinateMapper`.
- Make the saved-photo concrete payload implement `com.opencamera.core.media.SceneMaskPayload`.
- Add a `SceneMaskDescriptor` to saved-photo payloads with:
  - `role = PERSON_SUBJECT`
  - `quality = SAVED_PHOTO`
  - `backendId = "mlkit-selfie"` or provider-specific value
  - source/mask dimensions in `SceneMaskTransform`
  - confidence and diagnostics
- Add a preview adapter path so `PreviewSceneMaskPayload` can expose or build a `SceneMaskDescriptor` with `quality = PREVIEW_APPROXIMATE`.
- Update tests and imports to use explicit core imports where needed.

## Steps
1. Inspect all references:
   ```bash
   rtk rg -n "SceneMaskCapability|SceneMaskPayload|SceneMaskTransform|PreviewSceneMaskPayload" app core -g '*.kt'
   ```
2. Rename app-local conflicting types and update call sites.
3. Implement `core.media.SceneMaskPayload` on the saved-photo concrete payload.
4. Add descriptor construction to ML Kit saved-photo and preview payload creation.
5. Update tests to assert descriptor role, quality, backend, confidence, and `alphaAt()` behavior.
6. Add a simple guard test or static check to prevent reintroducing app-local same-name `SceneMaskPayload`, `SceneMaskCapability`, or `SceneMaskTransform`.

## Acceptance Criteria
- `rtk rg -n "^(internal )?(data )?(class|enum class|interface) SceneMask(Payload|Capability|Transform)" app/src/main/java app/src/test` returns no app-local conflicting type declarations.
- Saved-photo concrete masks implement `com.opencamera.core.media.SceneMaskPayload`.
- Existing preview and saved-photo tests pass after rename.
- Core `SceneMaskContractsTest` still passes.
- No Android framework type is introduced into `core/media`.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.SceneMaskContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes
- Use Kotlin import aliases only as a temporary migration aid; the final code should be readable without alias gymnastics.
- `READY` maps most closely to `SceneMaskSupport.SUPPORTED`, but preview runtime state and global capability are not identical. Preserve that distinction through naming.
- Keep payload sampling semantics stable: `alphaAt(maskX, maskY)` should remain mask-space sampling, while coordinate mapping belongs in `SceneMaskCoordinateMapper`.
