# OpenCamera Capability Kernel 2.0 Index

## Purpose

Camera Capability Kernel 2.0 is the next architecture design package for making OpenCamera behave like a strong general-purpose Android camera app without relying on vendor system-camera privileges.

The central premise is explicit:

- OpenCamera is not a vendor system camera.
- It cannot assume native cache pipelines, ISP/BSP cooperation, hidden vendor algorithms, private multi-frame prefetch, or privileged post-processing engines.
- It should therefore make upper-layer capability orchestration excellent: request more useful data from the platform where allowed, keep ownership boundaries strict, process intelligently in app space, and degrade honestly when device, memory, thermal, or API limits block a feature.

This package is a design and agent handoff set. It does not approve a stage transition by itself. Current stage rules still apply: do not leave Stage 7 or start a new implementation stage without explicit user approval.

## Kernel 2.0 Thesis

Version 1.0 already established the main four-layer chain:

```text
Mode Plugin -> Session Kernel -> Device Adapter -> Media Pipeline
```

Version 2.0 keeps that chain and adds a capability kernel inside it, not beside it:

```text
Mode Plugin
  declares product intent and acceptable degradation

Session Kernel
  owns runtime state, capture admission, recovery, and effect/capability resolution

Device Adapter
  owns platform graph binding, frame/data exposure, ImageAnalysis/ImageCapture/VideoCapture, Camera2 interop, runtime issue forwarding

Media Pipeline
  owns shot graph, app-level algorithms, temporal media assembly, save transactions, thumbnails, sidecars

Cross-cutting Governance
  resource budgets, diagnostics, quality/perf telemetry, deterministic tests, real-device validation hooks
```

The design goal is to move "camera feature intelligence" upward while keeping "camera runtime ownership" centralized. No coordinator, UI class, algorithm worker, or mode plugin may become a second hidden session kernel.

## Document Map

- `01_capability_contract_and_graph.md`
  Defines capability semantics, support/degrade/unsupported rules, feature graph resolution, and how mode intent becomes an executable capability plan.
- `02_frame_stream_and_buffer_orchestration.md`
  Defines preview/still/video frame data exposure, frame descriptors, app-side ring buffers, ImageAnalysis/ImageReader policy, backpressure, timestamps, and low-level metadata flow.
- `03_capture_graph_and_algorithm_pipeline.md`
  Defines ShotGraph 2.0, algorithm job contracts, filter/night/portrait/document processing, deterministic processor registry, and save/post-process ownership.
- `04_live_photo_and_temporal_media.md`
  Defines Live Photo 2.0 and temporal capture: still + motion + pre-shutter frame cache + metadata + transaction semantics.
- `05_resource_scheduler_and_performance_governance.md`
  Defines CPU/GPU/memory/thermal budgets, admission control, queueing, cancellation, quality downgrade, and perf telemetry.
- `06_mode_product_integration_plan.md`
  Defines how Photo, Night/Scenery, Portrait, Humanistic, Pro, Document, and Video consume the 2.0 kernel without duplicating runtime ownership.
- `07_observability_test_and_agent_handoff.md`
  Defines verification strategy, new test seams, dev-log/diagnostic output, scripts, and parallel agent ownership boundaries.
- `90_multimodal_deferred_capability_qa.md`
  Isolates visual/image-quality tasks that require multimodal review, screenshot/recording inspection, or saved-image comparison.

## Recommended Landing Order

### Phase 0: Current Bug Fixes Stay First

If current 1.0 remediation work is still open, land it before heavy 2.0 work:

- media output/filter/thumbnail stability
- panel IA/localization cleanup
- first launch preview recovery
- zero-latency thumbnail feedback

2.0 agents should not edit the same production files in parallel with those fixes unless one integrator owns the merge.

### Phase 1: Pure Contracts And Tests

Start with contracts that can be implemented and tested without real camera hardware:

- capability graph and support status model
- frame descriptor and buffer policy model
- shot graph and algorithm job model
- resource budget and admission model

Suggested owners:

- Agent A: `core:device`, `core:media`, optional new `core:capability` contracts.
- Agent B: `core:session` capability resolution tests.
- Agent C: `core:media` algorithm graph tests.

### Phase 2: Device Data Exposure

After contracts stabilize, connect app-layer data sources:

- CameraX `ImageAnalysis` as the first frame stream source.
- Camera2 metadata where available through existing adapter boundaries.
- Preview snapshot and capture feedback events as low-cost fallback sources.
- Explicit degraded semantics when high-frequency frame access is not available.

Suggested owner:

- Agent D: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` plus app camera tests.

### Phase 3: Algorithm Pipeline

Build processors as replaceable app-level jobs:

- filter/color render
- multi-frame placeholder upgraded to deterministic merge interface
- portrait render interface
- document enhancement interface
- temporal/Live assembly helpers

Suggested owner:

- Agent E: `core:media` and app post-processors. Avoid touching mode plugins in this phase except metadata tests.

### Phase 4: Mode Integration

Mode plugins begin declaring richer product intent:

- Photo: fast still, Live, filter, watermark.
- Night/Scenery: multi-frame policy and fallback.
- Portrait: profile, beauty, bokeh, segmentation requirement.
- Humanistic: color/temporal street capture defaults.
- Pro: manual parameters plus app-level processing constraints.
- Video: recording spec and low-light policy.

Suggested owner:

- Agent F: feature modules only, with narrow tests per mode.

### Phase 5: Resource Governance And Real-Device Loops

Only after basic 2.0 functionality exists:

- tune buffer limits
- measure first-frame/capture/post-process timings
- build per-device thresholds
- run long-session and thermal smoke

This phase should stay explicitly capability-gated. Do not claim image-quality improvements without real saved-output review.

## Non-Goals

- Do not bypass Session Kernel for camera runtime decisions.
- Do not put raw frame streams into `StateFlow`.
- Do not add a vendor-specific algorithm dependency as the default path.
- Do not make UI panels call `CameraX` or `Camera2` directly.
- Do not promise native-level system-camera quality where Android public APIs cannot provide the needed signal.
- Do not use screenshot or saved-image visual judgments in non-multimodal agent tasks; those are isolated in `90_multimodal_deferred_capability_qa.md`.

## Global Acceptance

- Every hardware-dependent feature has `supported`, `degraded`, or `unsupported` semantics.
- Every app-level algorithm has deterministic input/output contracts and can be tested with fake frames or files.
- Every temporal feature has bounded memory and cleanup rules.
- Every new frame stream has backpressure and release semantics.
- Every capture plan can be traced from mode intent to device request to media output.
- Every degraded path is user-visible through render models or diagnostics, not silently hidden.

