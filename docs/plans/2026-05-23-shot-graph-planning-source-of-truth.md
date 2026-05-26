# ShotGraph Planning Source Of Truth Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This task is contract/planning only; device and CameraX execution migration is a separate package.

**Goal:** Make `ShotGraph` the canonical capture topology produced by media planning while keeping the existing `ShotPlan` API usable for session/device/app call sites.

**Architecture:** `CaptureStrategy` remains the mode plugin intent. `ShotExecutor.plan()` creates `ShotRequest` and `MediaSaveTask`, then immediately builds a `ShotGraph` from the request. `ShotPlan` carries that graph as an explicit field. `ShotPlan.toShotGraph()` becomes a compatibility accessor that returns the stored graph, preventing v1/v2 drift.

**Tech Stack:** Kotlin contracts in `core:media`, pure graph tests in `core:media` and `core:mode`.

---

## Files

Modify:

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`
- `core/mode/src/test/kotlin/com/opencamera/core/mode/ModeCaptureStrategyGraphTest.kt`

Do not modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

Those files belong to the execution migration package.

## Required Semantics

- Every `ShotPlan` must contain exactly one `ShotGraph`.
- `plan.graph.shotId == plan.request.shotId`.
- `plan.toShotGraph()` must return `plan.graph`.
- `ShotGraph` must have deterministic node ids for stable tests.
- Existing `ShotExecutor.resultFor()` behavior must not change.
- Existing session/device/app code must compile without call-site edits.
- Video capture graph must use an explicit video capture role instead of `PRIMARY_STILL`.

## Contract Changes

Add a video capture role:

```kotlin
enum class CaptureNodeRole {
    PRIMARY_STILL,
    PRIMARY_VIDEO,
    TEMPORARY_FRAME,
    PRE_SHUTTER_FRAME,
    MOTION_SEGMENT,
    METADATA_SAMPLE
}
```

Extend `ShotPlan`:

```kotlin
data class ShotPlan(
    val request: ShotRequest,
    val saveTask: MediaSaveTask,
    val graph: ShotGraph
)
```

Add a private or internal builder near `ShotExecutor`:

```kotlin
internal object ShotGraphBuilder {
    fun build(request: ShotRequest): ShotGraph {
        // Move the current ShotPlan.toShotGraph() node construction here.
    }
}
```

Update `ShotExecutor.plan()`:

```kotlin
val request = ShotRequest(...)
val saveTask = MediaSaveTask(...)
return ShotPlan(
    request = request,
    saveTask = saveTask,
    graph = ShotGraphBuilder.build(request)
)
```

Change `ShotPlan.toShotGraph()` to a compatibility accessor:

```kotlin
fun ShotPlan.toShotGraph(): ShotGraph = graph
```

Do not remove the extension yet. Existing tests and docs still reference it.

## Builder Rules

Move the current node construction from `ShotPlan.toShotGraph()` into `ShotGraphBuilder.build(request)`.

Fix these details while moving:

- `ShotKind.VIDEO_RECORDING` capture node role must be `CaptureNodeRole.PRIMARY_VIDEO`.
- Multi-frame graph must use `request.captureProfile.frameCount.coerceAtLeast(1)` so an invalid frame count cannot create a zero-frame capture node.
- Thumbnail algorithm node should remain absent only when `thumbnailPolicy == ThumbnailPolicy.NONE`.
- `FILTER_RENDER`, `WATERMARK_RENDER`, and `THUMBNAIL_SELECT` node behavior should match current tests.

## Tests

Update `ShotExecutorTest` with these cases:

- Planning single-frame capture produces `plan.graph` with one `PRIMARY_STILL` capture node.
- `plan.toShotGraph()` returns the same graph as `plan.graph`.
- Video planning produces a `PRIMARY_VIDEO` capture node and a `PRIMARY_VIDEO` output node.
- Multi-frame planning clamps graph temp frame count to at least `1` while preserving existing request data.
- Existing `resultFor` tests remain unchanged.

Update `ModeCaptureStrategyGraphTest`:

- Change `graphFor(strategy)` from `executor.plan(strategy).toShotGraph()` to `executor.plan(strategy).graph`, or keep one compatibility test that intentionally uses `toShotGraph()`.
- Update the video assertion to expect `CaptureNodeRole.PRIMARY_VIDEO`.

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCaptureStrategyGraphTest
```

Expected: both pass.

Then run the current Stage 7 media/mode slice:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest --tests com.opencamera.core.media.AlgorithmProcessorTest --tests com.opencamera.core.media.AlgorithmJobSchedulerTest :core:mode:test --tests com.opencamera.core.mode.ModeCaptureStrategyGraphTest
```

Expected: pass.

## Acceptance

- There is only one graph construction implementation.
- `ShotPlan` no longer has to be converted by recomputing graph topology.
- All current runtime call sites still compile.
- No app/device/session behavior changes in this package.

## Handoff To Package 2

After this lands, tell the next agent the exact field names used for:

- `ShotPlan.graph`
- `CaptureNodeRole.PRIMARY_VIDEO`
- `ShotGraphBuilder`

Package 2 must treat those names as fixed unless it updates all tests and docs in one change.
