# Frame Stream And Buffer Orchestration 2.0

> **For agentic workers:** This is a non-multimodal implementation plan. Work with descriptors, fake frames, timestamps, buffer limits, and deterministic tests. Do not judge visual quality.

## Goal

Expose more useful low-level camera data to the app layer while keeping memory, latency, and ownership under control. This is the foundation for Live Photo, multi-frame capture, preview-based feedback, filters, and future app-side algorithms.

## Principle

Vendor cameras often rely on hidden native buffers and ISP/BSP cooperation. OpenCamera should not pretend to have that privilege. Instead, Device Adapter should expose only what public Android APIs can provide, in a structured way:

- frame timestamp
- source kind
- format
- size
- rotation
- lens/zoom context
- optional sensor metadata
- lease/release lifecycle
- bounded retention policy
- explicit support/degrade reason

Raw image bytes must not flow through `SessionState` or UI render models.

## Frame Stream Contracts

Add pure contracts before wiring CameraX.

Suggested types:

```kotlin
enum class FrameSourceKind {
    PREVIEW_ANALYSIS,
    STILL_CAPTURE_FEEDBACK,
    PREVIEW_SNAPSHOT,
    VIDEO_MOTION,
    METADATA_ONLY
}

enum class FramePayloadAccess {
    METADATA_ONLY,
    FILE_HANDLE,
    CPU_YUV,
    CPU_RGBA,
    GPU_TEXTURE
}

data class FrameDescriptor(
    val frameId: String,
    val source: FrameSourceKind,
    val timestampNanos: Long,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val payloadAccess: FramePayloadAccess,
    val lensFacingTag: String,
    val zoomRatio: Float,
    val exposureTimeNanos: Long? = null,
    val iso: Int? = null,
    val focusDistanceDiopters: Float? = null,
    val tags: Map<String, String> = emptyMap()
)

data class FrameBufferPolicy(
    val targetFps: Int,
    val maxFrames: Int,
    val retentionWindowMillis: Long,
    val maxBytes: Long,
    val dropPolicy: FrameDropPolicy
)

enum class FrameDropPolicy {
    KEEP_LATEST,
    KEEP_KEYFRAMES_AROUND_SHUTTER,
    BLOCK_PRODUCER_NEVER
}
```

For payloads, use a lease concept. The first implementation can be metadata-only plus file handles; CPU YUV can follow behind feature flags.

```kotlin
interface FrameLease : AutoCloseable {
    val descriptor: FrameDescriptor
    fun payload(): FramePayload?
}

sealed interface FramePayload {
    data class FileRef(val path: String) : FramePayload
    data class YuvPlanesRef(val width: Int, val height: Int) : FramePayload
    data class RgbaBufferRef(val width: Int, val height: Int) : FramePayload
}
```

The exact payload representation can change. The invariant is that every payload has a bounded lifetime and release path.

## Device Adapter Responsibilities

`CameraXCaptureAdapter` should become the only app class that binds and owns platform frame sources.

Initial sources:

- `ImageAnalysis` for low-resolution preview analysis frames when supported.
- Existing preview snapshot events as fallback.
- Capture feedback snapshot events as near-shutter feedback.
- Camera2 capture metadata if available through interop.

Rules:

- Use `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` for continuous preview analysis.
- Keep analysis resolution conservative by default, for example 720p class unless a feature requires more.
- Never allow analysis frames to block preview or still capture.
- Copy frame data only when a feature explicitly protects a frame near shutter time.
- If analysis binding conflicts with preview/capture/video on a device, disable analysis and emit a degraded capability report.

## App-Side Ring Buffer

The ring buffer is a Media/Device support service, not Session state.

Required behavior:

- bounded by frame count, retention window, and approximate byte budget
- can mark frames as protected around a shutter timestamp
- can return only descriptors for diagnostics
- can hand out leases to algorithms
- cleans up all unprotected leases on preview stop, mode switch, lifecycle stop, and recovery

Suggested policies:

```text
Photo idle:
  targetFps=8-12, maxFrames=12, retention=1500ms, low resolution

Live enabled:
  targetFps=12-15, maxFrames=24, retention=2000ms, low resolution

Night candidate:
  targetFps=metadata or low-res only until shutter, protect capture frames after shutter

Video recording:
  metadata-only unless explicit video frame extraction is supported
```

These are defaults, not device guarantees. Resource Governor can lower them.

## Timestamp Model

All frame and shot timing should use monotonic time where possible.

Required links:

- `FrameDescriptor.timestampNanos`
- `ShotTiming.requestedAtElapsedMillis`
- first protected frame before shutter
- first/last device-captured frame
- post-process completion

Diagnostics examples:

- `frame-buffer:policy=live-preview:12fps:24frames:2000ms`
- `frame-buffer:protected=8`
- `frame-buffer:dropped=31:keep-latest`
- `frame-buffer:degraded=image-analysis-bind-failed`

## Likely Files

Core contracts:

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`

Device implementation:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- possible new files under `app/src/main/java/com/opencamera/app/camera/frame/`

Tests:

- new `core/media` tests for buffer policy and descriptor selection
- new app unit tests for adapter policy decisions where possible

## Implementation Tasks

- [ ] Add frame descriptor, payload access, and buffer policy contracts.
- [ ] Add a pure ring-buffer policy selector based on mode, Live enabled, active recording, and resource budget.
- [ ] Add a fake frame buffer test that verifies retention, protected frames, drop policy, and cleanup.
- [ ] Add adapter-level degraded diagnostics when analysis frames cannot be provided.
- [ ] Wire only descriptors into diagnostics first; add payload leases after pure tests are stable.
- [ ] Add cleanup on preview stop, preview host detach, mode switch, and shot failure.

## Acceptance Tests

- Continuous frames keep only the latest when max frame count is exceeded.
- Protected frames around a shutter timestamp survive ordinary eviction.
- `close()` releases unprotected payloads and empties the buffer.
- Buffer policy for Live has a larger retention window than ordinary Photo.
- Video recording defaults to metadata-only unless a capability explicitly enables frame extraction.
- Session state contains capability/buffer summaries, never raw frames.

Suggested commands:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test :core:device:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
```

## Non-Goals

- Do not implement high-quality optical flow, segmentation, or frame interpolation here.
- Do not keep full-resolution preview buffers by default.
- Do not expose frame payloads to UI.
- Do not use unbounded channels or global singleton caches.

