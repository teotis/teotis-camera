# Capture Graph And Algorithm Pipeline 2.0

> **For agentic workers:** This is a non-multimodal implementation plan. Implement contracts, deterministic processors, metadata, and tests. High-fidelity visual tuning is deferred.

## Goal

Upgrade capture from a single `CaptureStrategy -> ShotPlan -> DeviceShotRequest -> ShotResult` chain into an explicit ShotGraph that can represent app-level algorithms: multi-frame merge, filter render, portrait fallback, document cleanup, watermark render, Live assembly, and thumbnail generation.

## Current Baseline

1.0 already has:

- `CaptureStrategy.SingleFrame`
- `CaptureStrategy.MultiFrame`
- `CaptureStrategy.LivePhoto`
- `CaptureStrategy.VideoRecording`
- `ShotExecutor`
- `MediaPostProcessor`
- app post-processors for document, frame ratio, portrait, filter, watermark, selfie mirror, video subtitle sidecar
- `MultiFrameMergePlaceholderPostProcessor`

2.0 should preserve this direction and make the graph more explicit so multiple agents can add processors without touching the Session Kernel.

## ShotGraph Model

Suggested contracts:

```kotlin
data class ShotGraph(
    val shotId: String,
    val captureNodes: List<CaptureNode>,
    val algorithmNodes: List<AlgorithmNode>,
    val outputNodes: List<OutputNode>,
    val diagnostics: List<String> = emptyList()
)

data class CaptureNode(
    val id: String,
    val role: CaptureNodeRole,
    val frameCount: Int,
    val timingPolicy: CaptureTimingPolicy,
    val requiredFormat: CaptureFrameFormat
)

enum class CaptureNodeRole {
    PRIMARY_STILL,
    TEMPORARY_FRAME,
    PRE_SHUTTER_FRAME,
    MOTION_SEGMENT,
    METADATA_SAMPLE
}

data class AlgorithmNode(
    val id: String,
    val type: AlgorithmType,
    val inputs: List<String>,
    val output: String,
    val requirement: AlgorithmRequirement,
    val fallback: AlgorithmFallback
)

enum class AlgorithmType {
    FILTER_RENDER,
    WATERMARK_RENDER,
    MULTI_FRAME_MERGE,
    NIGHT_ENHANCE,
    PORTRAIT_RENDER,
    DOCUMENT_ENHANCE,
    LIVE_ASSEMBLE,
    THUMBNAIL_SELECT
}
```

This does not need to replace `ShotPlan` immediately. The first step can be an adapter:

```text
CaptureStrategy -> ShotPlan 1.0 -> ShotGraph summary -> DeviceShotRequest
```

Then the graph can gradually become the source of truth.

## Algorithm Processor Contract

Each app-level algorithm should be a replaceable processor:

```kotlin
interface AlgorithmProcessor {
    val type: AlgorithmType
    fun canProcess(request: AlgorithmRequest): AlgorithmCapability
    suspend fun process(request: AlgorithmRequest): AlgorithmResult
}

data class AlgorithmRequest(
    val node: AlgorithmNode,
    val inputs: List<MediaInputRef>,
    val metadata: MediaMetadata,
    val budget: AlgorithmBudget
)

sealed interface AlgorithmResult {
    data class Applied(
        val output: MediaOutputHandle,
        val notes: List<String>
    ) : AlgorithmResult

    data class Skipped(
        val reason: String,
        val notes: List<String>
    ) : AlgorithmResult

    data class Failed(
        val reason: String,
        val recoverable: Boolean
    ) : AlgorithmResult
}
```

Important semantics:

- `Skipped` with a reason is acceptable for optional/degraded processors.
- `Failed(recoverable=true)` can fall back to original output.
- `Failed(recoverable=false)` fails the shot transaction.
- Processor notes must survive into `ShotResult.pipelineNotes`.

## Processor Families

### Filter Render

Input:

- saved photo output or editable file/content URI
- `FilterRenderSpec`
- profile id

Output:

- edited saved media or new output handle
- note `algorithm-render:applied:<profile-id>` or explicit skip reason

Non-multimodal pass:

- verify metadata flow and editor invocation
- use deterministic fake editor
- no visual color judgment

### Multi-Frame / Night

Input:

- temporary frame outputs
- final frame
- exposure/timing metadata

Output:

- final saved image
- notes for frame count, temp frames, merge strategy

2.0 improvement:

- replace placeholder with `MultiFrameMergeProcessor` contract first
- keep placeholder as deterministic fallback
- allow feature-gated real merge later

### Portrait

Input:

- still output
- optional segmentation/depth/face metadata
- profile, beauty, bokeh settings

Output:

- rendered output or focus-only fallback
- support state must say whether segmentation/depth was real, approximate, or unavailable

Non-multimodal pass:

- implement contracts and fallback notes only

### Document

Input:

- saved photo
- optional edge/geometry data

Output:

- cropped/enhanced photo or no-op fallback
- notes for auto-crop applied/skipped

### Watermark

Input:

- saved photo/video sidecar
- template and tokens
- placement and frame background

Output:

- rendered still or sidecar-only metadata for video

Required:

- still output failure should be explicit
- video sidecar-only must not pretend to be burnt into frames

## Media Save Transaction

2.0 should treat media output as a transaction with explicit artifact roles:

```kotlin
enum class MediaArtifactRole {
    PRIMARY_STILL,
    PRIMARY_VIDEO,
    TEMP_FRAME,
    MOTION_SEGMENT,
    LIVE_SIDECAR,
    THUMBNAIL,
    DEBUG_TRACE
}

data class MediaSaveTransactionResult(
    val primaryOutput: MediaOutputHandle,
    val artifacts: Map<MediaArtifactRole, List<MediaOutputHandle>>,
    val status: MediaTransactionStatus,
    val cleanupNotes: List<String>
)
```

Required behavior:

- Temporary frames are deleted after successful or failed merge.
- Optional sidecar failure may degrade Live to still-only if the primary still succeeded.
- Primary still/video failure fails the shot.
- Thumbnail source is selected after transaction status is known.

## Likely Files

Core:

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`

App processors:

- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/DocumentAutoCropPostProcessor.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

Tests:

- processor tests under `app/src/test/java/com/opencamera/app/camera/`
- `core/media` graph/planning tests

## Implementation Tasks

- [ ] Add ShotGraph summary contracts without deleting `ShotPlan`.
- [ ] Add an adapter that turns existing `CaptureStrategy` into a ShotGraph summary.
- [ ] Add `AlgorithmProcessor` contracts and deterministic fake processor tests.
- [ ] Update `MultiFrameMergePlaceholderPostProcessor` or wrap it as a processor with explicit `Skipped`/`Applied` semantics.
- [ ] Add Media Save Transaction roles and cleanup diagnostics.
- [ ] Ensure `ShotResult.pipelineNotes` includes device diagnostics, algorithm notes, transaction notes, and timing notes in a stable order.

## Acceptance Tests

- Single-frame photo graph has one primary still capture node and filter/watermark nodes when effects are requested.
- Night graph has multiple capture nodes and a merge algorithm node.
- Portrait graph degrades to focus fallback when portrait processor capability is unavailable.
- Live graph contains primary still, motion/sidecar artifacts, and thumbnail role.
- Optional sidecar failure does not delete a successful primary still.
- Temp frames are cleaned after merge success and failure.

Suggested commands:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.DocumentAutoCropPostProcessorTest
```

## Non-Goals

- Do not tune visual filter look here.
- Do not require real segmentation or optical flow in this pass.
- Do not move post-processing into mode plugins.
- Do not make the adapter own media algorithm decisions.

