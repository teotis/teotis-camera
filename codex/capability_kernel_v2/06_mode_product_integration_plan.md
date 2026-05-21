# Mode Product Integration Plan 2.0

> **For agentic workers:** This is a non-multimodal implementation plan. Work through mode declarations, capture strategies, metadata, support state, and tests. Do not perform visual quality evaluation.

## Goal

Integrate the 2.0 capability kernel into existing modes without duplicating runtime ownership or splitting the app into mode-specific camera stacks.

## Global Mode Rule

Each mode describes:

```text
what the product wants
what degradation it accepts
what metadata should be saved
what UI/support state should be shown
```

Each mode must not:

```text
bind CameraX/Camera2
own frame buffers
own recovery
start background algorithm jobs directly
store duplicate device capabilities
become a second session state machine
```

## Photo

Primary 2.0 product role:

- fast still capture
- filter/tone
- watermark
- Live Photo
- frame ratio
- timer
- common preview analysis source

2.0 declarations:

- still capture required
- filter capture render optional but visible if degraded
- Live motion optional and degrades to still-only
- watermark still render optional if output editor unavailable

Acceptance:

- Photo can explain whether Live is complete, degraded, or still-only.
- Selected filter reaches both preview approximation and capture metadata.
- Filter capture processor invocation is testable without visual comparison.

Likely files:

- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `core/media` capture graph tests

## Night / Scenery

Primary 2.0 product role:

- app-level multi-frame capture
- long-exposure metadata
- tripod/handheld policy
- conservative merge fallback

2.0 declarations:

- requested frame count
- inter-frame timing tolerance
- merge processor requirement
- resource fallback minimum

Degradation examples:

- 6 frames -> 3 frames under memory pressure
- multi-frame -> single-frame with night metadata when capture graph cannot support burst
- long exposure metadata saved-only if device request cannot apply it

Acceptance:

- Night mode never silently claims multi-frame when only single-frame executed.
- Pipeline notes include executed frame count and merge strategy.

Likely files:

- `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- `core/device/MultiFrameCaptureExecutionPlanner.kt`
- `core/media` merge tests

## Portrait

Primary 2.0 product role:

- portrait profile
- beauty preset/strength
- bokeh
- filter/tone
- optional depth/segmentation

2.0 declarations:

- portrait render requirement
- segmentation/depth support state
- focus fallback
- saved metadata for unsupported controls

Degradation examples:

- true segmentation unsupported -> focus/fake depth fallback
- bokeh shape unsupported -> generic blur/focus note
- beauty unsupported -> metadata-only

Acceptance:

- Portrait output notes distinguish real/applied, approximate, and saved-only effects.
- User-facing support state does not hide unsupported rows.

Likely files:

- `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`

## Humanistic

Primary 2.0 product role:

- street/humanistic color profile
- fast response
- lightweight app-level tone
- optional Live/photo defaults

2.0 declarations:

- prioritize latency over heavy processing unless user opts into stronger effect
- filter render required for capture only when processor is available
- preview tone approximation allowed

Acceptance:

- Humanistic mode can prefer lower algorithm budget than Portrait/Night.
- Filter/tone metadata flow is shared with Photo, not copied.

Likely files:

- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`

## Pro

Primary 2.0 product role:

- manual parameter request
- applied/saved-only/unsupported per control
- RAW capability semantics
- optional app-level processing restrained by user intent

2.0 declarations:

- manual controls mapped through existing capability matrix
- RAW remains saved-only or unsupported unless real output path is implemented
- filters/watermarks should be explicit user choices, not forced

Acceptance:

- Pro capture notes distinguish Camera2-applied controls from saved-only metadata.
- RAW is never represented as applied unless a real RAW output exists.

Likely files:

- `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

## Document

Primary 2.0 product role:

- document framing
- crop/geometry cleanup
- contrast enhancement
- fallback when geometry is unavailable

2.0 declarations:

- geometry/crop optional
- contrast profile optional
- save original or enhanced output according to transaction result

Acceptance:

- Document mode can show auto-crop applied/skipped reason.
- Failure to auto-crop does not fail the primary still unless configured as required.

Likely files:

- `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- `app/src/main/java/com/opencamera/app/camera/DocumentAutoCropPostProcessor.kt`

## Video

Primary 2.0 product role:

- video spec
- audio profile
- low-light fps policy
- video tone metadata or sidecar
- optional frame extraction future

2.0 declarations:

- recording spec resolved by capability matrix
- low-light policy can be applied, degraded, or unsupported
- video watermark defaults to sidecar-only unless burn-in is implemented

Acceptance:

- Video mode never claims 8K/high-fps/low-light auto unless adapter capability confirms it.
- Recording notes show requested and resolved video spec.

Likely files:

- `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/VideoSpecSelection.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`

## Cross-Mode Implementation Tasks

- [ ] Add per-mode product intent declarations using the capability graph model.
- [ ] Add tests that each mode produces the correct capture graph under default capabilities.
- [ ] Add tests that each mode degrades correctly when key capabilities are absent.
- [ ] Keep effect metadata generation shared through `EffectBridge` or a replacement common helper.
- [ ] Keep support state user-visible through render models, not only diagnostics.

## Suggested Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

For mode-specific implementation, run the touched module tests plus:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Non-Goals

- Do not add new modes in this pass.
- Do not rename internal `ModeId` values as part of capability work.
- Do not implement visual algorithm tuning here.
- Do not make modes directly consume frame payloads.

