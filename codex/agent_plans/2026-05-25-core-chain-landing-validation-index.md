# Core Chain Landing Validation Index

日期：2026-05-25

## Goal

把近期方案文档中共同触及的核心链路收敛成可执行的修复顺序，避免“功能入口已经出现，但底层处理互斥或诊断误导”。本索引基于当前代码核验结果，而不是只复述旧方案。

## Context

- User request: 核验形成超过 2 小时且不超过 36 小时的近期方案，重点关注共同触及的核心链路是否混乱、互斥或入口到底层断裂。
- Verified facts:
  - `CameraXCaptureAdapter.kt` 的 still preview bind 中，一个 `ImageAnalysis` analyzer 同时把同一个 `ImageProxy` 交给 scene mask source 和 Live preview frame source。
  - `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame(...)` 与 `CameraXLivePreviewFrameSource.onAnalyzeFrame(...)` 都会关闭 `ImageProxy`，存在 double-close、提前 close、后续消费者读不到帧的风险。
  - `AppContainer.kt` 现在已经把 `MlKitSavedPhotoSceneMaskProvider` 注入 `PhotoAlgorithmPostProcessor`，所以旧文档中“production composition 仍是 NoOp provider”的判断已经过期。
  - `PhotoAlgorithmPostProcessor` 的 saved mask 成功路径会写 `previewMask = SUPPORTED`，但 preview mask 目前没有被 Color Lab preview render path 消费。
  - `PreviewSceneMaskSource.latestMask()` 当前主要停留在 source/test 层；`PreviewEffectAdapter` 没有读取 preview mask。
  - Quick panel 亮度底层 `SessionIntent.ApplyPreviewBrightness -> DeviceCommand.ApplyPreviewBrightness -> CameraX setExposureCompensationIndex` 已存在，但 XML/action binding 仍是 `- / value / +`；画幅 UI 仍是三个 chip。
- Relevant existing plans:
  - `2026-05-25-portrait-preview-analysis-fanout-stability.md`
  - `2026-05-25-preview-saved-mask-consistency.md`
  - `2026-05-25-quick-panel-frame-cycle-brightness-slider.md`
- Non-goals:
  - Do not start a new product stage.
  - Do not assign real-device visual acceptance to non-multimodal agents.
  - Do not move image/mask/frame payload into `SessionState`.

## Recommended Execution Order

1. **P0: Preview analysis fanout ownership**
   - Execute `2026-05-25-portrait-preview-analysis-fanout-stability.md`.
   - This is the blocking correctness fix for Live Photo preview frames, preview scene mask, and future scene/low-light signals.

2. **P1: Scene mask diagnostic honesty**
   - Execute `2026-05-25-scene-mask-diagnostic-honesty-repair.md`.
   - This prevents false pass/fail signals while the preview path is still approximate or not wired.

3. **P2: Preview/saved mask consistency**
   - Execute `2026-05-25-preview-saved-mask-consistency.md`, but update its implementation assumptions with the current fact that saved-photo ML Kit provider is already wired in `AppContainer`.
   - This should happen only after P0, because preview mask consumption should not build on unsafe `ImageProxy` ownership.

4. **P3: Quick panel UI completion**
   - Execute `2026-05-25-quick-panel-frame-cycle-brightness-slider.md`.
   - This is a product/UI mismatch, not the core-chain blocker.

## Delegation Model

- Non-multimodal agents can implement P0, P1, and most of P2 because they are deterministic code/test tasks.
- Codex/user should retain:
  - final real-device preview vs saved JPEG visual acceptance;
  - taste judgment for “明显但自然” Color Lab;
  - verifying that Live Motion Photo still produces actual motion segments after the fanout fix.

## Acceptance Criteria

- `ImageProxy` ownership has one explicit owner and tests prove exactly-one close for analyzer callbacks.
- Scene mask pipeline notes never claim preview support when preview masks are not actually consumed.
- Saved-photo mask unavailable/failed paths emit honest degraded/unsupported/failed notes when a mask-aware render was attempted.
- `latestMask()` has a production consumer before any feature is declared preview mask-aware.
- Quick panel work, if executed, continues to dispatch through existing session intents and does not directly call CameraX.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- The current blocker is not an abstract architecture problem; it is concrete ownership ambiguity around `ImageProxy`.
- Existing plans created later on 2026-05-25 may contain more up-to-date implementation detail. Agents should still follow this index's verified ordering and avoid using any plan line that contradicts current code.
- If P0 requires changing ML Kit preview processing to close after async completion, tests must encode that decision rather than relying on comments.

## Validation Result - 2026-05-25

Status: partially implemented, not validated.

- P0 fanout ownership is substantially landed: `PreviewAnalysisFanout` exists, `CameraXCaptureAdapter` routes still-preview analysis through it, and the focused fanout/live/scene-mask tests pass.
- P1 diagnostic honesty is not landed: `PhotoAlgorithmPostProcessorTest` now contains expected coverage, but the production processor still does not satisfy saved-mask unavailable/failed/editor-not-mask-aware diagnostics and still overstates preview support on the saved-mask success path.
- P2 preview/saved consistency is only superficially wired: `latestMask()` reaches `previewOverlayRenderModel(...)` through `AppContainer.previewMaskSnapshot`, but preview rendering currently consumes only availability/descriptor state, not mask pixels; no deterministic subject/background differential preview test exists.
- P3 quick-panel UI is partially landed at the Android view/binding layer: the XML/action path now uses `brightnessSlider` and `buttonQuickFrameRatio`, dispatching through `ApplyPreviewBrightness` and `FrameRatioSelected`. Its render-model verification is not clean because `SessionCockpitRenderModelTest` still has failing expectations.

Validation evidence:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewAnalysisFanoutTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
# PASS

rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionPreviewRenderModelTest
# FAIL: PhotoAlgorithmPostProcessorTest saved-mask diagnostics; SessionUiRenderModelTest existing mode/filter expectations.

rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
# FAIL: quick-panel/model expectations plus existing humanistic mode expectations.

rtk ./gradlew --no-daemon :app:assembleDebug
# PASS

rtk ./scripts/verify_stage_7_observability.sh
# FAIL after escalation: :core:device:compileTestKotlin references removed stillCaptureQuality test API.
```
