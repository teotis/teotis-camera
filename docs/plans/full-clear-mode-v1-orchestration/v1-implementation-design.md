# Full Clear V1 - Implementation Design

## Architecture Placement

Full Clear stays inside the existing four-layer architecture:

- Mode Plugin: declares the product mode and requests Full Clear capture behavior.
- Session Kernel: remains the owner of runtime state, intents, capture submission, and mode switching.
- Device Adapter: translates focus bracket requests into CameraX/Camera2 execution.
- Media Pipeline: owns bracket artifacts, fusion/fallback, saved output, metadata, and diagnostics.

UI and mode plugins must not drive CameraX directly.

## V1 Capture Model

Introduce a focus-bracket contract instead of overloading generic `frameCount`:

- `FocusBracketSpec`: near/far focus plan, frame roles, max capture duration, fallback policy.
- `FocusBracketFrameRole`: `NEAR_SUBJECT`, `FAR_BACKGROUND`, optional `REFERENCE`.
- `FocusBracketSupport`: `SUPPORTED`, `DEGRADED_REBIND`, `UNSUPPORTED`.
- Metadata tags: `fullClearBracket`, `focusBracketFrames`, `focusBracketSupport`, `fullClearFusionStatus`.

The first V1 can use two frames: near subject and far/infinity. A third reference frame is optional and should not be required for V1.

## V1 Processing Model

`FOCUS_STACK_FUSION` should be distinct from existing `MULTI_FRAME_MERGE` so diagnostics do not confuse night merge with focus stacking.

Allowed V1 outcomes:

- `fused`: synthetic tests and runtime confidence indicate the fused JPEG is acceptable.
- `best-frame`: fusion skipped and the best single frame was saved.
- `degraded`: bracket succeeded but alignment/fusion confidence was too low.
- `unsupported`: device could not apply bracket focus.
- `failed`: capture or processing failed and ordinary failure path handled the result.

## Verification Strategy

Local tests must prove contracts, graph construction, adapter diagnostics, and synthetic bitmap behavior. Real-device QA must compare saved JPEGs under real close-plus-far scenes and inspect artifacts, latency, and pipeline notes.

