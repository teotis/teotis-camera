# Scene Mask Verification And Visual QA

## Goal

Define local and real-device verification for scene-mask based Color Lab and portrait processing, with a clear split between deterministic tests for implementation agents and final visual acceptance retained by Codex/user.

## Context

- User request: fully understand subject/body recognition direction and split plans if sound.
- This plan depends on:
  - [Scene Mask Contracts And Capability](./2026-05-25-scene-mask-contracts-capability.md)
  - [Preview Subject Mask Pipeline](./2026-05-25-preview-subject-mask-pipeline.md)
  - [Saved Photo Mask Rendering](./2026-05-25-saved-photo-mask-rendering.md)
- Relevant files:
  - `scripts/verify_stage_7_observability.sh`
  - `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/*`
- Non-goals:
  - Do not require real-device evidence from non-multimodal implementation agents.
  - Do not judge “natural skin” from unit tests alone.

## Local Verification Scope

Add or update focused tests:

1. Contract tests:
   - `SceneMaskDescriptor` metadata round trip.
   - Transform rotation/crop/mirror fields preserve values.
   - Capability supports `SUPPORTED / DEGRADED / UNSUPPORTED`.

2. Preview source tests:
   - Analyzer fans out to Live and preview mask consumers.
   - `ImageProxy.close()` happens exactly once.
   - Inference backlog drops frames.
   - No-op backend reports unsupported and does not crash.

3. Saved-photo renderer tests:
   - Synthetic person mask protects center subject.
   - Background receives stronger Color Lab change.
   - Feathered edge avoids hard alpha step.
   - Low confidence or failed mask falls back with pipeline note.

4. Integration tests:
   - Capture metadata includes mask availability/degradation notes when a mask-aware render path is requested.
   - Existing Color Lab no-mask tests still pass.
   - `PhotoWatermarkPostProcessor` still runs after mask-aware color render.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.live.LivePreviewFrameSourceTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Real Device QA Protocol

Codex/user should run this after implementation.

Scene set:

- One person in foreground.
- Visible background with color variation.
- At least one neutral object such as white wall, gray table, or black clothing.
- If possible, include sky/green plant/skin tones.

Steps:

1. Install debug APK.
2. Open Photo mode.
3. Record screen while moving Color Lab:
   - center;
   - warm airy corner;
   - warm deep corner;
   - cool airy corner;
   - cool deep corner.
4. Capture one saved JPEG per corner.
5. Repeat once in Portrait mode if mask-aware portrait path was changed.
6. Collect:
   - screen recording;
   - saved JPEGs;
   - diagnostics/pipeline notes if available.

Acceptance:

- Preview and saved JPEG move in the same direction.
- Background color/tone changes are stronger than before.
- Person/skin remains natural enough; no obvious orange/blue/green cast.
- Mask edge does not show harsh halos around hair, shoulders, glasses, or hands.
- If segmentation is unavailable, UI/output diagnostics honestly report degraded/unsupported and capture still succeeds.

Failure examples:

- Preview shows mask-aware effect but saved JPEG is global-only.
- Saved JPEG changes person strongly but background weakly.
- Subject edge has visible cutout halos.
- Capture or preview stalls because segmentation blocks analyzer.
- Pipeline notes say mask applied when no mask was actually used.

## Codex-Retained Acceptance

Only Codex/user should make the final call on:

- “自然不生硬”.
- Whether foreground/background strength feels camera-product level.
- Whether ML Kit quality is acceptable on the target real device.
- Whether to enable mask-aware Color Lab by default.

## Risks And Notes

- Unit tests can prove direction and guardrails, not taste.
- One target device pass is not a broad device matrix. Record device model, API level, and backend id in QA notes.
- If ML Kit beta behavior changes, rerun local and device QA before declaring product pass.
