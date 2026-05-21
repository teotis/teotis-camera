# Observability, Testing, And Agent Handoff 2.0

> **For agentic workers:** Use this document to split implementation safely. Do not start all file-overlapping tasks in parallel. Start with pure contracts and tests.

## Goal

Make Capability Kernel 2.0 observable and testable enough that multiple agents can implement it without turning the camera stack into a set of hidden, conflicting state machines.

## Required Diagnostics Shape

Every 2.0 feature should answer these questions in diagnostics:

- What did the mode request?
- What did capability resolution allow?
- What did the device adapter actually execute?
- What algorithm processors ran, skipped, degraded, or failed?
- What artifacts were saved?
- What was cleaned up?
- How long did capture and post-processing take?
- What resource budget or thermal state affected the result?

## Diagnostics Names

Use stable key-value style notes. Examples:

```text
capability:live-motion=degraded:no-frame-buffer
capability:portrait-bokeh=degraded:focus-fallback
frame-buffer:policy=live-preview:12fps:24frames:2000ms
frame-buffer:protected=8
device:burst-executed=3
algorithm:filter=applied:custom-vivid-1
algorithm:night-merge=skipped:single-frame-fallback
transaction:primary=photo
transaction:sidecar=skipped:scoped-storage
resource:thermal=warm
resource:live=degraded:max-frames
timing:device=210ms
timing:postprocess=380ms
```

Avoid long prose in machine diagnostics. UI render models can localize user-facing explanations separately.

## Test Pyramid

### Pure Unit Tests

Fastest and highest priority:

- capability graph resolution
- frame buffer policy
- ring buffer eviction/protection
- shot graph planning
- algorithm processor registry
- transaction cleanup
- resource admission
- mode declarations and degradation

### App Unit Tests

Use fakes where Android APIs make direct tests difficult:

- CameraX adapter helper functions
- sidecar path and handle selection
- processor invocation
- thumbnail precedence
- diagnostics mapping
- thermal/runtime issue mapping

### Assemble And Stage Script

Run after meaningful integration:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

### Real-Device Smoke

Required before claiming product quality:

- first launch and permission flow
- preview ready
- Photo capture with filter
- Live enabled capture
- Night/Scenery capture
- Portrait capture
- Video recording start/stop
- mode switch under preview
- thermal/recovery long-session smoke where possible

Non-multimodal agents can write the smoke checklist. Multimodal agents or human reviewers must judge saved-output visuals.

## Parallel Agent Work Split

### Agent A: Capability Graph Contracts

Owns:

- `core/device`
- `core/effect`
- optional new pure contract files

Avoids:

- `CameraXCaptureAdapter.kt`
- mode plugins
- UI

Deliverables:

- support status contracts
- pure resolver
- capability diagnostics tests

### Agent B: Frame Stream And Buffer Contracts

Owns:

- `core/media` frame descriptor and buffer policy
- pure frame buffer tests

Avoids:

- CameraX binding at first
- mode plugins

Deliverables:

- `FrameDescriptor`
- `FrameBufferPolicy`
- fake ring buffer tests

### Agent C: ShotGraph And Algorithm Contracts

Owns:

- `core/media`
- post-processor interfaces

Avoids:

- adapter binding
- UI

Deliverables:

- ShotGraph summary
- AlgorithmProcessor contracts
- transaction role model
- pure graph tests

### Agent D: CameraX Data Exposure

Starts after Agents A/B contracts land.

Owns:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- new app camera frame helper files
- app camera tests

Deliverables:

- ImageAnalysis/frame descriptor source if feasible
- degraded diagnostics if binding fails
- cleanup on preview stop/recovery

### Agent E: Live And Temporal Media

Starts after frame contracts and media transaction contracts land.

Owns:

- `core/media` Live extensions
- `CameraXCaptureAdapter` Live helpers
- Live tests

Coordinates with:

- Agent D if both touch adapter code

Deliverables:

- temporal window plan
- still-only fallback
- sidecar storage fallback
- cleanup tests

### Agent F: Resource Governance

Owns:

- `core/session` diagnostics additions
- resource admission pure policy
- thermal/resource tests

Avoids:

- visual UI changes

Deliverables:

- budget model
- admission decisions
- diagnostics integration

### Agent G: Mode Integration

Starts after capability/shot graph contracts land.

Owns:

- feature modules
- mode-specific tests

Avoids:

- adapter and media transaction internals

Deliverables:

- mode product intent declarations
- degradation tests per mode

## Merge Coordination

High-conflict files:

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`

Rules:

- Do pure contracts first, then adapter wiring.
- Split large files once there is a concrete reason, for example `FrameStreamContracts.kt` or `AlgorithmPipelineContracts.kt`.
- Do not run parallel workers against `CameraXCaptureAdapter.kt` unless their write scopes are explicitly separated.
- Prefer additive contracts and tests over broad refactors.

## Exit Criteria For A 2.0 Closed Loop

A useful first 2.0 closed loop is not "all algorithms are great." It is:

- Photo capture can request a filter + Live + watermark.
- Capability graph resolves what is supported/degraded.
- Device adapter executes still capture and emits frame/bundle diagnostics.
- Media pipeline applies deterministic filter/watermark or records explicit skip.
- Live saves complete or degrades still-only without failing primary still.
- Thumbnail updates from saved media.
- Dev diagnostics explain the full path.
- Unit tests cover each step.

## Verification Commands

Use focused commands first:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
```

Then run:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not require a real-device visual pass for pure contract changes.
- Do not let each agent invent separate diagnostic formats.
- Do not update `codex/plan.md` stage rules unless the user explicitly asks.
- Do not call a 2.0 loop complete unless tests and diagnostics show the path end to end.

