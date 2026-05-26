# 2026-05-23 Media Pipeline Contract Split Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Use `rtk` for every shell command. This plan is text-only and does not require screenshots, videos, or visual judgment.

## Goal

Split `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt` into focused files while preserving source-level API and media pipeline behavior.

## Current Evidence

`MediaPipelineContracts.kt` is 1133 lines and currently contains:

- shot/media primitive types;
- save request and metadata contracts;
- live photo bundle contracts;
- thumbnail/output/result contracts;
- concrete postprocessors;
- `ShotExecutor`;
- ShotGraph 2.0 node models;
- algorithm processor contracts;
- frame stream contracts;
- a placeholder postprocessor-to-algorithm bridge;
- `ShotPlan.toShotGraph`;
- media save transaction result derivation.

Existing nearby files already show the desired direction:

- `AlgorithmJobScheduler.kt`: scheduling behavior.
- `LiveTemporalAssemblyPlanner.kt`: live temporal planning.
- `ResourceAdmissionPolicy.kt`: resource admission behavior.
- `ResourceBudgetContracts.kt`: resource budget contracts.
- `MediaProcessorAvailability.kt`: availability contracts.

The giant file should be reduced to smaller owners using the same pattern.

## Architecture Target

All new files stay in:

```kotlin
package com.opencamera.core.media
```

Target source files:

- `MediaTypes.kt`: basic media enums, `MediaMetadata`, `SaveRequest`, and `PostProcessSpec`.
- `LivePhotoContracts.kt`: live photo capture/bundle/window contracts and live helper functions.
- `ShotLifecycleContracts.kt`: capture strategy, shot request/save task/plan/result, thumbnail source, output handle, timing, and result helper functions.
- `MediaPostProcessorContracts.kt`: `MediaPostProcessor` only.
- `MediaPostProcessors.kt`: `CompositeMediaPostProcessor`, `PipelineMetadataPostProcessor`, and `MultiFrameMergePlaceholderPostProcessor`.
- `ShotExecutor.kt`: `ShotExecutor` and its private metadata merge helper.
- `ShotGraphContracts.kt`: ShotGraph 2.0 node, algorithm node, and artifact-role contracts.
- `AlgorithmProcessorContracts.kt`: `MediaInputRef`, `AlgorithmBudget`, `AlgorithmRequest`, `AlgorithmResult`, and `AlgorithmProcessor`.
- `FrameStreamContracts.kt`: frame source, descriptor, buffer policy, payload, and lease contracts.
- `AlgorithmProcessorAdapters.kt`: `MultiFrameMergePlaceholderPostProcessor.toAlgorithmProcessor`.
- `ShotGraphPlanner.kt`: `ShotPlan.toShotGraph`.
- `MediaSaveContracts.kt`: transaction status/result and `ShotResult.toTransactionResult`.

`MediaPipelineContracts.kt` should be deleted after all declarations move and compilation is green. If deletion creates tooling churn, leave only the package line and a short comment for one pass, then delete in a cleanup commit.

## File Movement Map

Move these declarations from `MediaPipelineContracts.kt`:

- To `MediaTypes.kt`:
  - `MediaType`, `ShotKind`, `ThumbnailPolicy`, `FlashMode`.
  - `StillCaptureQualityPreference`, `StillCaptureResolutionPreset`, `FrameRatio`.
  - `MediaMetadata`, `SaveRequest`, `PostProcessSpec`.

- To `LivePhotoContracts.kt`:
  - `LivePhotoCaptureSpec`, `LivePhotoBundle`.
  - `LiveMotionSource`, `LiveBundleStatus`, `LiveTemporalWindow`.
  - `LivePhotoBundle.isTemporalMedia`.
  - `LivePhotoBundle.temporalNotes`.

- To `ShotLifecycleContracts.kt`:
  - `CaptureProfile`, `CaptureStrategy`.
  - `ShotRequest`, `MediaSaveTask`, `ShotPlan`.
  - `ThumbnailSource`, `CaptureFeedbackPreview`, `MediaOutputHandle`.
  - `ShotTiming`, `ShotResult`.
  - `ThumbnailSource.outputPathOrNull`.
  - `ThumbnailSource.renderUriOrNull`.
  - `ShotResult.hasPostProcessFailures`.
  - `ShotResult.postProcessFailureSummary`.

- To `MediaPostProcessorContracts.kt`:
  - `MediaPostProcessor`.

- To `MediaPostProcessors.kt`:
  - `CompositeMediaPostProcessor`.
  - `PipelineMetadataPostProcessor`.
  - `MultiFrameMergePlaceholderPostProcessor`.

- To `ShotExecutor.kt`:
  - `ShotExecutor`.
  - private `MediaMetadata.mergeWith`.

- To `ShotGraphContracts.kt`:
  - `CaptureNodeRole`, `CaptureTimingPolicy`, `CaptureFrameFormat`, `CaptureNode`.
  - `AlgorithmType`, `AlgorithmRequirement`, `AlgorithmFallback`, `AlgorithmNode`.
  - `MediaArtifactRole`, `OutputNode`, `ShotGraph`.

- To `AlgorithmProcessorContracts.kt`:
  - `MediaInputRef`, `AlgorithmBudget`, `AlgorithmRequest`, `AlgorithmResult`, `AlgorithmProcessor`.

- To `FrameStreamContracts.kt`:
  - `FrameSourceKind`, `FramePayloadAccess`, `FrameDescriptor`.
  - `FrameDropPolicy`, `FrameBufferPolicy`.
  - `FramePayload`, `FrameLease`.

- To `AlgorithmProcessorAdapters.kt`:
  - `MultiFrameMergePlaceholderPostProcessor.toAlgorithmProcessor`.

- To `ShotGraphPlanner.kt`:
  - `ShotPlan.toShotGraph`.

- To `MediaSaveContracts.kt`:
  - `MediaTransactionStatus`, `MediaSaveTransactionResult`.
  - `ShotResult.toTransactionResult`.

## Compatibility Rules

- Keep every public symbol name unchanged.
- Keep every enum entry, MIME type string, metadata note string, graph node ID convention, and transaction status unchanged.
- Keep all files in `com.opencamera.core.media`.
- Do not move app-specific postprocessors into `core:media`.
- Do not make `ShotExecutor` call CameraX or any app-layer class.
- Do not change `ThumbnailSource.SavedMedia.renderUri` behavior.
- Do not change `MediaOutputHandle.renderUriOrNull` behavior.
- Do not change `ShotPlan.toShotGraph` output node IDs, algorithm node IDs, requirements, or fallbacks.

## Implementation Steps

1. Create the new files with only `package com.opencamera.core.media`.
2. Move declarations in dependency order:
   - `MediaTypes.kt`;
   - `LivePhotoContracts.kt`;
   - `ShotLifecycleContracts.kt`;
   - `MediaPostProcessorContracts.kt`;
   - `MediaPostProcessors.kt`;
   - `ShotExecutor.kt`;
   - `ShotGraphContracts.kt`;
   - `AlgorithmProcessorContracts.kt`;
   - `FrameStreamContracts.kt`;
   - `AlgorithmProcessorAdapters.kt`;
   - `ShotGraphPlanner.kt`;
   - `MediaSaveContracts.kt`.
3. After each group, remove the original declaration from `MediaPipelineContracts.kt` to avoid duplicate symbol errors.
4. Run the core media tests after moving `ShotExecutor`, postprocessors, graph planner, and save transaction logic:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
```

5. Run device translator tests because `core:device` consumes `ShotPlan`, `ShotExecutor`, and media request contracts:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
```

6. Run session tests because `DefaultCameraSession` consumes `ShotExecutor`, `ShotRequest`, `ThumbnailSource`, and `ShotResult`:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

7. Run the Stage 7 gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

8. Delete or empty `MediaPipelineContracts.kt` only after the focused tests pass.

## Tests To Watch

- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/PipelineNotesOrderTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/MultiFrameMergeAlgorithmProcessorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/AlgorithmProcessorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/FrameStreamContractsTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/MediaSaveTransactionTest.kt`
- `core/mode/src/test/kotlin/com/opencamera/core/mode/ModeCaptureStrategyGraphTest.kt`
- `core/device/src/test/kotlin/com/opencamera/core/device/DefaultDeviceShotRequestTranslatorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/test/java/com/opencamera/app/camera/AlgorithmProcessorBridgesTest.kt`

## Risk Notes

- Kotlin top-level function file-class names will change for `toShotGraph`, `toTransactionResult`, and `toAlgorithmProcessor`. This is acceptable for source recompilation inside this app, but not binary compatible for external consumers.
- `MediaPostProcessor` and concrete postprocessors should not be split across packages. The app currently imports `CompositeMediaPostProcessor`, `MultiFrameMergePlaceholderPostProcessor`, and `PipelineMetadataPostProcessor` from `com.opencamera.core.media`.
- `FramePayload.YuvPlanesRef` and `FramePayload.RgbaBufferRef` override `equals` and `hashCode` for byte arrays. Preserve those bodies exactly.
- `ShotPlan.toShotGraph` is a pure derivation but has important product semantics. Do not "improve" graph IDs, roles, or fallback rules during this file split.
- `MultiFrameMergePlaceholderPostProcessor.toAlgorithmProcessor` bridges old placeholder behavior into the algorithm processor contract. Keep it as a compatibility bridge until a separate media pipeline implementation plan replaces it.

## Completion Criteria

- `MediaPipelineContracts.kt` no longer owns mixed responsibilities.
- The largest new media file is below roughly 450 lines.
- All public media symbols still live in `com.opencamera.core.media`.
- Focused media tests pass.
- Focused device and session tests pass.
- Stage 7 observability gate passes before declaring completion.

