# Capability Contract And Graph 2.0

> **For agentic workers:** This is a non-multimodal implementation plan. Do not evaluate image beauty or saved-output visual quality in this pass. Build deterministic contracts, tests, and capability semantics.

## Goal

Create a unified capability graph so OpenCamera can decide which app-level camera features are executable, degraded, saved-only, preview-only, or unsupported before it binds a graph or launches a shot.

## Problem

The 1.0 architecture already has `DeviceCapabilities`, `EffectCapabilityResolver`, mode declarations, and per-feature capability flags. That is enough for coarse features, but not enough for a 2.0 camera where the app tries to replace some vendor-camera lower-layer advantages with upper-layer processing.

2.0 needs to reason about compound requirements:

- Live Photo needs still capture, temporal motion, sidecar save, thumbnail, and optional dynamic watermark.
- Night needs multiple frames, inter-frame timing, stable memory, merge processor, and a fallback when the device cannot keep up.
- Portrait needs subject/depth/segmentation capability or a conservative focus/bokeh fallback.
- Filters need preview approximation, capture render, metadata continuity, and saved-media edit access.
- Pro needs manual controls that can be applied, saved-only, or unsupported per control.

## Core Model

Introduce a capability graph model as contracts first. It can live in a new `core:capability` module or in `core:device`/`core:media` if the project prefers fewer modules.

Suggested types:

```kotlin
enum class CapabilitySupport {
    SUPPORTED,
    DEGRADED,
    SAVED_ONLY,
    PREVIEW_ONLY,
    UNSUPPORTED
}

data class CapabilityRequirement(
    val id: String,
    val kind: CapabilityRequirementKind,
    val requiredFor: Set<CapabilityUseSite>,
    val fallbackIds: List<String> = emptyList()
)

enum class CapabilityRequirementKind {
    STILL_CAPTURE,
    VIDEO_RECORDING,
    PREVIEW_FRAME_STREAM,
    ANALYSIS_FRAME_STREAM,
    MULTI_FRAME_CAPTURE,
    TEMPORAL_RING_BUFFER,
    MOTION_SIDE_CAR,
    MANUAL_CONTROL,
    RAW_OUTPUT,
    FILTER_CAPTURE_RENDER,
    FILTER_PREVIEW_RENDER,
    PORTRAIT_SEGMENTATION,
    DOCUMENT_GEOMETRY,
    WATERMARK_RENDER,
    SAVE_TRANSACTION,
    THUMBNAIL_RESULT
}

enum class CapabilityUseSite {
    PREVIEW,
    CAPTURE,
    VIDEO,
    LIVE_PHOTO,
    DIAGNOSTICS
}

data class CapabilityResolution(
    val requirement: CapabilityRequirement,
    val support: CapabilitySupport,
    val reason: String,
    val selectedFallbackId: String? = null,
    val diagnostics: Map<String, String> = emptyMap()
)

data class CapabilityGraphReport(
    val featureId: String,
    val requested: List<CapabilityRequirement>,
    val resolved: List<CapabilityResolution>
)
```

Do not overfit the exact names. The required behavior is the important part: every feature can explain what it asked for and what it actually got.

## Ownership

### Mode Plugin

Mode plugins declare product intent:

```text
Portrait wants profile=transparent, beauty=light, bokeh=soft, filter=vivid.
Night wants frameCount=6, longExposure=1200ms, merge=night-basic.
Photo wants Live on, filter custom-vivid-1, watermark travel.
```

Mode plugins do not inspect `ImageReader`, `ImageAnalysis`, file handles, or Camera2 request state directly.

### Session Kernel

Session Kernel owns resolution timing:

```text
settings + mode intent + device capabilities + resource budget -> capability graph report
```

It stores only low-frequency resolution summaries in `SessionState`. It must not store raw frames or per-frame metadata.

### Device Adapter

Device Adapter provides actual platform capability evidence:

- available lenses
- zoom support
- preview/capture graph compatibility
- supported output sizes
- manual control matrix
- video spec constraints
- availability of app-visible frame streams
- runtime issue reports

### Media Pipeline

Media Pipeline owns app-level processor capability:

- filter render processor available
- watermark render processor available
- multi-frame merge processor available
- portrait render processor available
- document processor available
- temporal media assembler available

## Required Contract Behavior

- Capability graph resolution is pure and unit-testable.
- Unsupported and degraded states include a stable reason string.
- Saved-only is not treated as applied. This is important for RAW, WB, some manual controls, video watermark sidecars, and Live metadata-only fallbacks.
- Preview-only is not treated as capture-applied. This is important for filter previews and composition guides.
- A mode can still be shown when one sub-feature is unsupported, if its main capture path remains supported.
- Diagnostics include selected fallback, for example:
  - `capability:night-merge=degraded:single-frame`
  - `capability:portrait-bokeh=degraded:focus-fallback`
  - `capability:live-motion=unsupported:no-temporal-buffer`
  - `capability:filter-preview=preview-only`

## Likely Files

Create or modify:

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`

Tests:

- `core/device/src/test/kotlin/com/opencamera/core/device/*`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectCapabilityResolverTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/*`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

Optional new module if approved by the implementing agent:

- `core/capability`

If adding a new module causes Gradle churn, keep the first implementation inside existing `core:device` and `core:media`.

## Implementation Tasks

- [ ] Add capability support enums and pure data contracts.
- [ ] Add a pure resolver that combines `DeviceCapabilities`, `EffectSpec`, `CaptureStrategy`, and media processor availability.
- [ ] Extend diagnostics strings so applied/degraded/saved-only states survive into `ShotResult.pipelineNotes`.
- [ ] Add tests for Photo Live, Night multi-frame, Portrait bokeh, Filter preview/capture, Pro manual controls, and Video watermark sidecar states.
- [ ] Wire the first low-frequency capability report into `SessionState` or diagnostics without exposing raw stream data.

## Acceptance Tests

Suggested test cases:

- Live Photo requested with no temporal buffer support resolves to `DEGRADED` still-only, not `SUPPORTED`.
- RAW requested on a device with `ManualControlSupport.SAVED_ONLY` resolves to `SAVED_ONLY`, not applied.
- Portrait bokeh requested without portrait depth support resolves to `DEGRADED` focus fallback.
- Filter preview available but capture processor missing resolves preview as `PREVIEW_ONLY` and capture as `UNSUPPORTED`.
- Night multi-frame on a memory-limited budget resolves to lower frame count or single-frame fallback with a stable diagnostic reason.

Suggested commands:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test :core:media:test :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Non-Goals

- Do not implement real image-quality algorithms here.
- Do not bind new CameraX use cases here.
- Do not move mode state into the capability graph.
- Do not let the graph become a second session state machine.

