# Live Motion Photo Preview Ring Buffer

> For text-only agents: this is a core/media plus app-camera task. Build deterministic frame-buffer contracts and CameraX preview-frame source plumbing. Do not judge motion visual quality.

## Goal

Turn the current Live path from `METADATA_ONLY` into a real temporal source by buffering low-resolution preview frames around the shutter moment. The output of this plan is not the final Google Motion Photo file. It is a bounded, testable motion-source layer that later feeds MP4 assembly and container writing.

## Current Baseline

- `FrameStreamContracts.kt` defines `FrameDescriptor`, `FramePayload`, `FrameLease`, and default buffer policies.
- `LiveTemporalAssemblyPlanner.kt` already prefers `PREVIEW_RING_BUFFER` when `ringBufferDepthMillis > 0`.
- `CameraXCaptureAdapter.captureLivePhoto()` currently passes `LiveMotionSource.METADATA_ONLY`, `ringBufferDepthMillis = 0`, and `postShutterBudgetMillis = 0`.
- Existing preview snapshots use `PreviewView.bitmap`, which is suitable for thumbnail feedback but not enough for a smooth motion segment by itself.

## Required Behavior

- When Live is enabled and preview is active, the app keeps a bounded low-resolution frame buffer owned by the device/media side, not by `SessionState`.
- On shutter acceptance, the adapter asks the buffer for frames near the shutter timestamp.
- If enough pre-shutter frames are available, `planLiveTemporalAssembly()` sees `LiveMotionSource.PREVIEW_RING_BUFFER` and a positive depth.
- If the buffer is unavailable or disabled, the system returns still-only fallback with diagnostics, not silent success.
- Buffer cleanup runs on preview unbind, host detach, shutdown, recovery rebind, mode switch, and shot failure.

## Product Semantics

This models the product behavior users expect from Apple/vivo/oppo style "Live":

- The still image remains the high-quality capture.
- The motion part is a short memory around the shutter, biased toward pre-shutter frames.
- The motion part can be lower resolution than the still.
- If the app cannot capture motion without hurting preview or shutter reliability, it must degrade honestly.

## Suggested Design

### Core Pure Buffer

Create a pure Kotlin buffer in `core/media` so retention and selection are testable without Android:

- Create `core/media/src/main/kotlin/com/opencamera/core/media/FrameRingBuffer.kt`.
- Add `FrameRingBufferSnapshot`, `FrameSelectionWindow`, and `SelectedFrameSet` if needed.
- Store descriptors plus lightweight payload references or fake leases. Do not store Android `ImageProxy` in core.
- Evict by max frames and retention window.
- Protect selected frames around a shutter timestamp long enough for motion assembly.

Suggested data shapes:

```kotlin
data class FrameSelectionWindow(
    val shutterTimestampNanos: Long,
    val preShutterMillis: Long,
    val postShutterMillis: Long
)

data class SelectedFrameSet(
    val frames: List<FrameDescriptor>,
    val preShutterCount: Int,
    val postShutterCount: Int,
    val coveredPreShutterMillis: Long,
    val coveredPostShutterMillis: Long,
    val diagnostics: List<String>
)
```

Keep payload access separate. The first pass may select descriptors and file refs only.

### App Camera Source

Add app-side CameraX integration behind a small boundary:

- Create `app/src/main/java/com/opencamera/app/camera/live/LivePreviewFrameSource.kt`.
- Create `app/src/main/java/com/opencamera/app/camera/live/CameraXLivePreviewFrameSource.kt`.
- Bind CameraX `ImageAnalysis` only when Live is enabled and the current device graph can tolerate it.
- Use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`.
- Use conservative analysis resolution, for example 720p class or lower.
- Convert each accepted frame into a `FrameDescriptor` plus a bounded payload reference.
- Drop frames if the buffer is full or the resource governor says no.

Do not wire ImageAnalysis frames into UI. The adapter owns this source.

### Diagnostics

Use stable notes:

```text
frame-buffer:policy=live-preview:15fps:24frames:2000ms
frame-buffer:accepted=37
frame-buffer:selected=18
frame-buffer:window=-1200ms,+300ms
frame-buffer:degraded=image-analysis-bind-failed
frame-buffer:degraded=no-frames-near-shutter
live:source=preview-ring-buffer
```

## Files To Inspect Or Modify

Core:

- `core/media/src/main/kotlin/com/opencamera/core/media/FrameStreamContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/LiveTemporalAssemblyPlanner.kt`
- Create `core/media/src/main/kotlin/com/opencamera/core/media/FrameRingBuffer.kt`
- Create `core/media/src/test/kotlin/com/opencamera/core/media/FrameRingBufferTest.kt`
- Update `core/media/src/test/kotlin/com/opencamera/core/media/LiveTemporalAssemblyPlannerTest.kt` only if the planner needs richer input.

App:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- Create `app/src/main/java/com/opencamera/app/camera/live/LivePreviewFrameSource.kt`
- Create `app/src/main/java/com/opencamera/app/camera/live/CameraXLivePreviewFrameSource.kt`
- Update `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`

## Implementation Steps

1. Add pure ring-buffer tests first.
   - `append` keeps frames sorted by timestamp.
   - exceeding `maxFrames` evicts oldest unprotected frames.
   - exceeding `retentionWindowMillis` evicts stale frames.
   - selecting around `shutterTimestampNanos` returns pre and post counts.
   - `clear()` releases leases and empties descriptors.

2. Implement `FrameRingBuffer`.
   - Use constructor input `FrameBufferPolicy`.
   - Require `FrameDropPolicy.BLOCK_PRODUCER_NEVER` for the app's first implementation.
   - Never block producer threads.
   - Return diagnostics from selection without logging directly.

3. Add a pure policy selector.
   - Inputs: Live enabled, recording active, thermal state if available, memory budget if available.
   - Output: `FrameBufferPolicy.LIVE_PREVIEW_DEFAULT` for Photo Live idle, `PREVIEW_DEFAULT` for ordinary photo, metadata-only for video unless explicitly supported.

4. Add app boundary interfaces under `app/camera/live`.
   - `LivePreviewFrameSource.start(policy)`.
   - `LivePreviewFrameSource.stop(reason)`.
   - `LivePreviewFrameSource.selectForLive(shutterTimestampNanos, spec)`.
   - Provide a fake implementation for unit tests.

5. Wire `CameraXCaptureAdapter` to lifecycle events.
   - Start source after preview bind if Live default is enabled or current graph says Live can be prepared.
   - Stop source before unbind and on cleanup paths.
   - On `captureLivePhoto()`, query selected frames before or immediately after still capture request timestamp.

6. Keep fallback honest.
   - If source unavailable, pass `LiveMotionSource.METADATA_ONLY`.
   - If source exists but selected frames are too few, pass `LiveMotionSource.POST_SHUTTER_FRAMES` only when actual post-shutter frames exist; otherwise metadata-only.

## Tests

Core:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.FrameRingBufferTest --tests com.opencamera.core.media.FrameStreamContractsTest --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest
```

App focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
```

Expected assertions:

- Fake source with 1500ms of frames causes Live plan status `COMPLETE`.
- Fake source with no frames causes status `STILL_ONLY_FALLBACK`.
- Adapter diagnostics include `live:source=preview-ring-buffer` only when the selected frames exist.
- Preview detach stops and clears the source.

## Manual Smoke Later

After container integration:

1. Enable Live in Photo mode.
2. Keep preview active for at least 2 seconds.
3. Capture one photo.
4. Confirm diagnostics show selected frame count and pre-shutter window.
5. Confirm no preview stall or recovery loop appears.

## Non-Goals

- Do not encode MP4 in this plan.
- Do not add audio.
- Do not tune color, stabilization, interpolation, or frame choice visually.
- Do not store full-resolution frames by default.
- Do not put payload bytes into `SessionState`.
