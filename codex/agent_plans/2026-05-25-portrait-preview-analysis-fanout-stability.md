# Preview Analysis Fanout Stability

## Goal

修复预览 `ImageAnalysis` 多消费者分发，让 Live preview frame source、preview scene mask source 和未来低光/场景信号可以安全共用分析帧。人像模式后续预览 mask 只能建立在这个稳定入口上。

## Context

- User request: 重新开放人像模式，基础是人物和背景识别拆分，并用于后续光斑、滤镜、美颜。
- Verified facts:
  - `CameraXCaptureAdapter.kt` 已在 still template 中创建 `ImageAnalysis`。
  - 当前 analyzer 把同一个 `ImageProxy` 先传给 `sceneMaskSource?.onAnalyzeFrame(...)`，再传给 `CameraXLivePreviewFrameSource?.onAnalyzeFrame(...)`。
  - `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame(...)` 会关闭 `ImageProxy`；Live preview source 也会在自己的 finally 中关闭 `ImageProxy`。
  - `PreviewSceneMaskSourceTest` 只验证 no-op/ML Kit null image 的基础行为，尚未验证多消费者 fanout 时只关闭一次、且不会提前关闭。
- Relevant files:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/camera/MlKitSelfiePreviewSceneMaskSource.kt`
  - `app/src/main/java/com/opencamera/app/camera/live/CameraXLivePreviewFrameSource.kt`
  - `app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/live/LivePreviewFrameSourceTest.kt`
- Non-goals:
  - Do not add portrait UI.
  - Do not implement real-time portrait blur in this package.
  - Do not move frame/mask payload into session state.

## Implementation Scope

- Introduce a small app-layer fanout owner, for example `PreviewAnalysisFanout` or `SceneMaskAnalysisBridge`.
- Make one component own `ImageProxy.close()`. Consumers should either:
  - copy the data they need synchronously and return without closing; or
  - receive a copied/immutable frame payload instead of the original `ImageProxy`.
- Recommended first pass:
  - `CameraXCaptureAdapter` analyzer owns `ImageProxy.close()`.
  - `CameraXLivePreviewFrameSource` receives a copied YUV frame helper or is wrapped so it no longer closes the shared proxy.
  - `MlKitSelfiePreviewSceneMaskSource` should not close a proxy owned by the fanout. If ML Kit needs the underlying `mediaImage` until async completion, either keep segmentation as the last owner and close in completion, or copy/convert before returning. Do not leave closing ambiguous.
- Preserve `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.

## Steps

1. Add failing tests around a fake `ImageProxy` close counter:
   - both Live and scene mask consumers configured;
   - scene mask throws;
   - live source throws;
   - neither consumer configured.
2. Extract analyzer dispatch logic out of the long bind method in `CameraXCaptureAdapter.kt`.
3. Change `PreviewSceneMaskSource` contract if needed so ownership is explicit. Acceptable options:
   - rename method to `analyzeFrame(...)` and document that it must not close;
   - return a `Boolean`/result object and leave close to fanout;
   - introduce a copied frame value object for consumers that do not need the raw proxy.
4. Update `MlKitSelfiePreviewSceneMaskSource` to avoid prematurely closing a frame still needed by ML Kit async processing.
5. Update `CameraXLivePreviewFrameSource` usage so Live still records preview frames.
6. Add pipeline/diagnostic logs only if useful; do not surface high-frequency frame logs to `SessionState`.

## Acceptance Criteria

- A single `ImageProxy` is closed exactly once for every analyzer callback.
- Scene mask failure cannot break Live preview frame buffering.
- Live preview frame buffering cannot break scene mask inference.
- No analyzer path blocks the main thread waiting for ML inference.
- Tests prove fanout behavior without requiring a real camera.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- The current code comments say scene mask processes first because it needs raw `ImageProxy`, but that makes ownership especially fragile. Treat this as a correctness issue, not a style refactor.
- ML Kit may require the image data to remain valid until the async task completes. Confirm behavior while implementing and encode the ownership decision in tests.
- Keep this package app-local; do not create a second session/kernel owner for frame data.

