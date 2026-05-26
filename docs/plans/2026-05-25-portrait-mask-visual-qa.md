# Portrait Mask Visual QA And Acceptance

## Goal

Define verification for the reopened portrait mode so implementation agents can prove deterministic behavior locally, while Codex/user retains final judgment on visual quality.

## Context

- User request: 人像模式重开，基础是人像/背景识别拆分，然后分别进行光斑、滤镜、美颜。
- This plan depends on:
  - [Preview Analysis Fanout Stability](./2026-05-25-portrait-preview-analysis-fanout-stability.md)
  - [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md)
  - [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md)
  - Existing Scene Mask plans from `2026-05-25-scene-mask-segmentation-index.md`
- Relevant files:
  - `scripts/verify_stage_7_observability.sh`
  - `app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PreviewSceneMaskSourceTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/live/LivePreviewFrameSourceTest.kt`
- Non-goals:
  - Do not ask non-multimodal agents to judge beauty taste or halo quality from screenshots.
  - Do not require a real-device matrix before local contracts are correct.

## Local Verification Scope

Implementation agents should add or maintain these tests:

1. Preview/fanout tests:
   - Analyzer closes each `ImageProxy` exactly once.
   - Scene mask and Live consumers cannot break each other.
   - Inference backlog drops frames rather than piling up.

2. Saved portrait tests:
   - Available synthetic person mask routes to mask-aware portrait editor.
   - No-person/failed provider falls back and records degraded notes.
   - Subject patch is less blurred and less color-shifted than background patch.
   - Background patch receives stronger bokeh/bloom/light-spot behavior.

3. Contract tests:
   - Beauty maps to subject spec only.
   - Bokeh/light-spot maps to background spec only.
   - Mask support can be `SUPPORTED / DEGRADED / UNSUPPORTED`.

4. Regression tests:
   - Non-portrait photos are ignored by portrait renderer.
   - Existing Color Lab mask-aware tests still pass.
   - Watermark/selfie mirror postprocessors still run after portrait rendering.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PreviewSceneMaskSourceTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.SceneMaskContractsTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Real Device QA Protocol

Codex/user should run this after local tests pass.

Scene set:

- One person in foreground, with visible hair/shoulders/hands if possible.
- Background containing small lights or high-contrast highlights for light-spot judgment.
- Neutral object such as white wall, gray table, or black clothing.
- One low-light or indoor scene and one daylight scene.

Steps:

1. Install debug APK.
2. Open Portrait mode.
3. Capture with:
   - native + natural bokeh;
   - luminous + creamy bokeh;
   - luminous + dreamy bokeh;
   - one strong portrait filter.
4. If preview mask rendering is implemented, record screen while switching these options.
5. Collect saved JPEGs and diagnostics/pipeline notes.

Acceptance:

- Person remains visibly separated from background without obvious cutout edges.
- Skin does not become orange/green/blue under strong background treatment.
- Background receives stronger bokeh/bloom/light-spot treatment than subject.
- Hair/shoulder/hand edges do not show harsh halos.
- Saved JPEG and preview move in the same visual direction when preview rendering exists.
- If segmentation is unavailable, capture succeeds and diagnostics honestly report fallback/degraded.

Failure examples:

- Pipeline notes say mask applied when output clearly used only center ellipse.
- Subject is blurred or light-spotted like the background.
- Background is barely changed while subject is heavily beautified/tinted.
- Capture stalls or preview freezes because segmentation blocks the analyzer.
- Watermark/selfie mirror disappears after portrait render.

## Codex-Retained Acceptance

Only Codex/user should decide:

- whether the effect feels product-grade;
- whether mask edge quality is acceptable on target devices;
- whether the default portrait profile should use mask-aware rendering;
- whether preview approximation is close enough to saved JPEG.

## Risks And Notes

- Unit tests prove routing and relative pixel behavior, not taste.
- One device pass is not a device matrix. Record device model, Android version, backend id, and whether ML Kit was supported.
- If mask quality is weak, prefer honest fallback over aggressive artificial bokeh.

