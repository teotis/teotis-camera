# Live Photo And Temporal Media 2.0

> **For agentic workers:** This is a non-multimodal implementation plan. Build transaction semantics, buffer contracts, fallback behavior, metadata, and tests. Motion quality review is deferred.

## Goal

Make Live Photo a real temporal media feature instead of a still image with a flag. In 2.0, Live Photo is:

```text
primary still + short motion segment + timestamp alignment + thumbnail + sidecar/container metadata + cleanup/fallback rules
```

## Current Baseline

1.0 already has:

- `ShotKind.LIVE_PHOTO`
- `LivePhotoCaptureSpec`
- `LivePhotoBundle`
- `CaptureStrategy.LivePhoto`
- adapter-side sidecar materialization
- session state for latest live bundle

Current known issue:

- Generic `MediaStore.Files` sidecar under `Pictures` can fail on Android scoped storage rules. That must be fixed in the 1.0 media-output plan before 2.0 relies on Live as a stable building block.

## 2.0 Temporal Capture Model

### Capture Timeline

```text
t - 1500ms ... t - 100ms: pre-shutter low-res preview/analysis frames
t: shutter intent accepted
t + device capture: primary still captured
t + 0..500ms: optional post-shutter motion frames
after still: assemble sidecar/container, pick thumbnail, save transaction
```

2.0 does not need perfect native Live Photo parity. It needs honest app-level semantics:

- If pre-shutter frame cache exists, use it.
- If only a post-shutter motion clip is available, mark it degraded.
- If motion cannot be captured, save still-only with explicit notes.
- If primary still fails, the shot fails.

### Live Capability States

```text
SUPPORTED:
  primary still + motion segment + sidecar/container + thumbnail all succeed

DEGRADED:
  primary still succeeds, motion exists but low-res/short/metadata-limited

SAVED_ONLY:
  user default is saved and metadata is written, but no live motion is produced

UNSUPPORTED:
  still capture itself or storage transaction cannot support live semantics
```

## Live Bundle Contract

Extend `LivePhotoBundle` only if needed. The important additions are temporal truth and artifact status.

Suggested types:

```kotlin
enum class LiveMotionSource {
    PREVIEW_RING_BUFFER,
    VIDEO_RECORDER_SEGMENT,
    POST_SHUTTER_FRAMES,
    METADATA_ONLY
}

enum class LiveBundleStatus {
    COMPLETE,
    DEGRADED_MOTION,
    STILL_ONLY_FALLBACK
}

data class LiveTemporalWindow(
    val requestedDurationMillis: Long,
    val preShutterMillis: Long,
    val postShutterMillis: Long,
    val frameCount: Int,
    val source: LiveMotionSource
)
```

Pipeline notes examples:

- `live:status=complete`
- `live:source=preview-ring-buffer`
- `live:frames=18`
- `live:window=-1200ms,+300ms`
- `live:degraded=metadata-only`
- `live:sidecar=app-private`

## Motion Source Strategy

### Strategy A: Preview Ring Buffer

Recommended first 2.0 direction.

Pros:

- fits general app constraints
- avoids recorder startup latency
- aligns with frame stream design
- easy to bound memory

Cons:

- lower quality than native video motion
- may lack audio
- frame rate and format vary by device

### Strategy B: Short Video Recorder Segment

Use later as an optional capability.

Pros:

- real MP4 motion segment
- easier gallery compatibility

Cons:

- harder to run concurrently with still capture
- more CameraX graph conflicts
- can increase shutter lag

### Strategy C: Metadata-Only Fallback

Use when no real motion source is available.

Pros:

- preserves user setting and metadata continuity

Cons:

- should not be marketed as a complete Live Photo

## Save Transaction Rules

Required:

- Primary still success is the minimum for a successful photo shot.
- Sidecar failure should not delete the still if Live is optional.
- Motion failure should degrade to still-only unless the mode specifically requires motion.
- All temp frames and motion temp files are cleaned on failure.
- `latestLivePhotoBundle` is set only when the bundle is usable.
- Thumbnail source should remain the primary still unless a better explicit thumbnail is produced.

Suggested transaction statuses:

```text
photo.complete
photo.complete.live.complete
photo.complete.live.degraded
photo.complete.live.still-only
photo.failed.primary-still
photo.failed.transaction
```

## Watermark Semantics

Live watermark must be explicit:

- Still watermark rendered into primary still.
- Motion watermark is either rendered per frame, represented as metadata/sidecar, or unsupported.
- If motion watermark is unsupported, note `live-watermark=still-only`.

Non-multimodal agents should only implement metadata and routing. Visual review of motion watermark belongs in the deferred multimodal document.

## Likely Files

Core:

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`

App:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- new temporal helpers under `app/src/main/java/com/opencamera/app/camera/live/` if splitting is helpful

Tests:

- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Implementation Tasks

- [ ] Fix sidecar storage fallback before relying on Live 2.0.
- [ ] Add Live temporal window/status contracts and tests.
- [ ] Add a pure Live assembly planner from `LivePhotoCaptureSpec`, frame buffer availability, and resource budget.
- [ ] Add transaction status notes for complete, degraded, and still-only outcomes.
- [ ] Ensure `DefaultCameraSession` updates `latestLivePhotoBundle` only when a usable bundle exists.
- [ ] Add cleanup tests for primary still failure, sidecar failure, and motion failure.

## Acceptance Tests

- Live requested with usable frame buffer produces a complete bundle plan.
- Live requested without frame buffer produces still-only fallback with diagnostics.
- Sidecar storage failure after still success returns photo success with Live degraded/skipped notes.
- Motion temp artifacts are cleaned when assembly fails.
- Thumbnail remains the primary saved still.
- `latestLivePhotoBundle` is null for still-only fallback unless the sidecar explicitly describes the fallback as usable.

Suggested commands:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Non-Goals

- Do not claim iOS-style Live Photo compatibility without a tested container/export decision.
- Do not add audio capture to Live in the first pass.
- Do not use unbounded frame caches.
- Do not visually tune motion or watermark rendering in this pass.

