# Preview Subject Mask Pipeline

## Goal

Add a low-resolution, replaceable preview subject mask pipeline so Color Lab and portrait preview can react to person/background separation without pretending the preview mask is final saved-output quality.

## Context

- User request: use open-source or self-built recognition to handle preview and postprocess person/foreground/background/color optimization.
- Recommended backend:
  - First pass: ML Kit Selfie Segmentation behind an interface.
  - Second pass option: MediaPipe Image Segmenter if the project later needs broader semantic classes.
- Verified facts:
  - `CameraXCaptureAdapter` already binds `ImageAnalysis` for Live preview frames when `livePreviewFrameSource` exists.
  - `CameraXLivePreviewFrameSource` copies YUV preview frames into a ring buffer and closes `ImageProxy` safely.
  - Current preview effect path is `EffectSpec -> PreviewEffectAdapter -> SessionPreviewRenderModel -> PreviewOverlayView`.
- Relevant files:
  - `app/build.gradle.kts`
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/live/CameraXLivePreviewFrameSource.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
- Non-goals:
  - Do not run segmentation on the main thread.
  - Do not block preview binding on ML model availability.
  - Do not add visible UI controls in this package.
  - Do not use preview mask as final saved-photo mask.

## Implementation Scope

- Add app-layer interfaces:

```kotlin
internal interface PreviewSceneMaskSource {
    val capability: SceneMaskCapability
    fun start(config: PreviewSceneMaskConfig)
    fun stop(reason: String)
    fun latestMask(): SceneMaskPayload?
    fun onAnalyzeFrame(image: ImageProxy, rotationDegrees: Int)
}

internal data class PreviewSceneMaskConfig(
    val targetWidth: Int = 256,
    val targetHeight: Int = 256,
    val maxFps: Int = 8,
    val backendId: String = "mlkit-selfie"
)
```

- Add two implementations:
  - `NoOpPreviewSceneMaskSource` for unsupported/degraded build paths and tests.
  - `MlKitSelfiePreviewSceneMaskSource` behind an interface.
- Add `SceneMaskAnalysisBridge` or similar owner that allows multiple consumers of `ImageAnalysis`:
  - Live preview ring buffer.
  - Preview scene mask source.
  - Future low-light/scene signal source.
- Update `CameraXCaptureAdapter` so the `ImageAnalysis` analyzer fans out safely and always closes `ImageProxy` exactly once.
- Add preview render model hook:
  - `PreviewEffectRenderModel` may include a `subjectMaskPreview` descriptor/availability, but not pixel payload.
  - App layer can keep the latest mask payload in `PreviewSceneMaskSource` and use it only inside preview renderer if needed.

## ML Kit Notes

- Use the official ML Kit Selfie Segmentation Android guide for the current Gradle dependency and API names at implementation time:
  - https://developers.google.cn/ml-kit/vision/selfie-segmentation/android?hl=en
- Prefer stream mode for preview and configure smoothing if the API exposes it.
- If dependency resolution fails or package size is unacceptable, keep the `NoOpPreviewSceneMaskSource` path and report `DEGRADED`.

## Steps

1. Add `PreviewSceneMaskSource` and fake/no-op tests.
2. Refactor `CameraXCaptureAdapter` `ImageAnalysis` setup so one analyzer can dispatch to Live and scene mask sources.
3. Add ML Kit adapter:
   - Convert `ImageProxy` to ML Kit input image using rotation degrees.
   - Request a confidence mask.
   - Downsample/normalize alpha to compact mask payload.
   - Drop frames when inference is still running.
   - Add diagnostics for model unavailable, inference failure, frame skipped, low confidence.
4. Add preview smoothing:
   - Keep only the latest mask.
   - Apply simple temporal blend or let ML Kit stream smoothing do it if available.
   - Record `preview=approximate`.
5. Connect `AppContainer` with no-op fallback when dependency/backend unavailable.
6. Add tests:
   - analyzer closes image once when both consumers exist;
   - no-op reports unsupported;
   - frame drop behavior prevents inference backlog;
   - transform rotation/crop metadata is preserved.

## Acceptance Criteria

- Preview can run without segmentation support.
- Segmentation failure cannot crash preview or capture.
- `ImageAnalysis` is still `STRATEGY_KEEP_ONLY_LATEST`.
- Mask source produces low-frequency latest mask and diagnostics, not session state.
- Live preview frame source continues to work.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Preview mask should be treated as approximate. Saved-photo postprocess must rerun segmentation or use a higher-quality still mask.
- If the analyzer currently uses `ContextCompat.getMainExecutor(context)`, consider moving ML inference dispatch to a background executor while still closing `ImageProxy` deterministically.
- Avoid introducing another hidden owner that interprets session state. The source owns only frame/mask data.
