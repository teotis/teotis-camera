# Full Clear V2 - Implementation Design

## Architecture

V2 should extend the V1 Full Clear contracts rather than creating a parallel capture path.

- Mode Plugin: selects Full Clear V2 intent and policy.
- Session Kernel: owns state transitions, user guidance state, and capture submission.
- Device Adapter: owns lens/focus/metering execution and capability reporting.
- Media Pipeline: owns artifacts, alignment, fusion, fallback, diagnostics, and saved output.

## V2 Core Contracts

Add design-level contracts before implementation:

- `FullClearSceneAssessment`: foreground target confidence, background confidence, motion/stability, recommended route.
- `FullClearRoute`: `DEEP_DOF_LENS`, `FOCUS_BRACKET`, `LENS_PAIR_BRACKET`, `BEST_FRAME_ONLY`, `UNSUPPORTED`.
- `FullClearCapturePlan`: selected route, lens node, frame roles, focus distances, max latency, fallback policy.
- `FullClearFusionReport`: alignment score, fusion score, selected output, fallback reason, artifact warnings.

## Device Strategy

Route choice should happen before capture:

1. If ultra-wide/deep-DOF lens route is available and subject distance is extreme, prefer it over bracket.
2. If main camera focus bracket is supported, capture near/far bracket.
3. If lens node switching can produce a useful close/far pair without unacceptable framing shift, mark as experimental/degraded.
4. If none are supported, guide the user to distance/framing changes and save best available single frame.

## Media Strategy

V2 fusion should require:

- normalized exposure/color between frames,
- coarse alignment,
- lens-breathing compensation,
- sharpness map generation,
- edge-aware confidence,
- fallback if confidence is below threshold.

The saved JPEG must include the chosen route and `FullClearFusionReport` metadata tags.

