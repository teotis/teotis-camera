# Preview And Saved Mask Consistency

## Goal
Close the product loop for "preview and final image look similar" by making preview masks visible to the preview color pipeline and saved-photo masks visible to the final render pipeline through shared descriptor semantics.

## Context
- User request: Color Lab should be stronger but natural, preview and final output should remain close, and subject/foreground/background handling may need mask awareness.
- Verified facts:
  - `CameraXCaptureAdapter` feeds `ImageAnalysis` frames to `PreviewSceneMaskSource`.
  - `MlKitSelfiePreviewSceneMaskSource` stores a `latestMask()`.
  - Current app search shows `latestMask()` is not consumed by the preview rendering path outside tests/source code.
  - Saved-photo mask rendering can become real after the production wiring package.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- Non-goals:
  - Do not require pixel-perfect preview/final equality.
  - Do not block camera preview if segmentation is slow.
  - Do not make mask payload part of `SessionState`.
  - Do not ask non-multimodal agents to judge naturalness from screenshots.

## Implementation Scope
- Add a preview-facing mask snapshot adapter that exposes:
  - descriptor/quality/backend from canonical `core/media`.
  - bounded mask dimensions and timestamp.
  - stale-mask handling.
- Feed mask availability into the preview render/effect layer without moving raw mask pixels into session state.
- Make preview Color Lab use mask information for subject protection / background color emphasis when available.
- Make saved-photo Color Lab use saved-photo mask descriptor/quality notes when available.
- Add deterministic tests with fake masks:
  - subject pixels are less affected than background pixels for protected recipes.
  - stale or unavailable preview mask falls back to non-mask preview.
  - saved and preview metadata use comparable role/backend/quality fields.

## Steps
1. After the canonicalization package lands, inspect the exact names of preview/saved payload types.
2. Decide the smallest preview render injection point. Prefer a local app adapter or effect input object over storing mask payloads in session state.
3. Add tests around fake masks and Color Lab preview/saved behavior.
4. Implement preview mask consumption with bounded CPU work.
5. Add pipeline/diagnostic notes showing preview approximate vs saved-photo quality.
6. Leave final visual judgment to Codex/user using real-device preview and saved JPEG comparisons.

## Acceptance Criteria
- `latestMask()` has at least one production consumer in the preview/effect rendering path.
- Preview mask usage is optional and degrades without blocking camera preview.
- Saved-photo and preview mask metadata share canonical `SceneMaskDescriptor` semantics.
- Tests prove subject/background differential behavior with a fake mask.
- No raw mask bytes are stored in session state or persisted settings.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes
- The preview pipeline may only support coarse overlays today. If so, first land a conservative approximation and keep stronger perceptual Color Lab work in the existing Color Lab recipe plan.
- Mask staleness matters. A good default is to ignore masks older than a small preview window rather than applying a stale subject boundary.
- Codex/user should run multimodal acceptance on the provided day/night images or real-device captures after code lands.
