# Package 01 — Backend Capability Matrix

## Package ID

`01-backend-capability-matrix`

## Goal

Produce an evidence-backed capability matrix for Scene Mask backends that OpenCamera can honestly expose as `SUPPORTED`, `DEGRADED`, or `UNSUPPORTED`.

## Allowed Paths

- Read any repository file.
- Write only `docs/plans/scene-mask-foundation-research-orchestration/status/01-backend-capability-matrix.md`.

## Forbidden Paths

- Runtime code and tests.
- `INDEX.md`.
- Other package status files.

## Inputs

- Existing plan: `docs/plans/2026-05-25-scene-mask-segmentation-index.md`
- Existing plan: `docs/plans/2026-05-25-preview-subject-mask-pipeline.md`
- Existing plan: `docs/plans/2026-05-25-saved-photo-mask-rendering.md`
- Official docs listed in `INDEX.md` research baseline.

## Research Questions

1. Which backend should be the recommended first default for:
   - preview person mask;
   - saved photo person mask;
   - general subject/object foreground mask;
   - future semantic regions such as sky/background/object?
2. What is the honest capability label for each backend on a typical Android client:
   - `SUPPORTED`;
   - `DEGRADED`;
   - `UNSUPPORTED`;
   - conditions that flip one label to another?
3. Which backends require Google Play services model download, bundled model size, min API, beta disclaimers, static-image only constraints, or device performance warnings?
4. What should OpenCamera avoid claiming until it has real depth or semantic classes?

## Required Output In Status File

- Backend matrix covering at least:
  - ML Kit Selfie Segmentation;
  - ML Kit Subject Segmentation;
  - MediaPipe Image Segmenter;
  - Camera2 depth / CameraX extensions if relevant;
  - self-managed TFLite/ONNX as deferred option.
- Recommended phase-1 backend decision and alternatives.
- Capability labels for preview, saved photo, portrait bokeh, Color Lab subject protection, background tuning, depth slider.
- Official source links used.
- Risks that require real-device validation.

## Acceptance Criteria

- Matrix distinguishes person/selfie mask from general subject segmentation.
- Matrix explicitly states that 2D mask is not true depth.
- Matrix treats beta/unbundled/download/latency/minSdk constraints as degradation inputs.
- Recommendations are compatible with OpenCamera's current no-network/no-secret client assumptions.

## Verification Commands

```bash
rtk rg -n "SceneMask|MlKit|SubjectSegmentation|Segmentation|getClient|ImageAnalysis" app core docs/plans
rtk sed -n '1,220p' docs/plans/2026-05-25-scene-mask-segmentation-index.md
```

## Expected Evidence Pack

Use the standard status template and add a `## Capability Matrix` section.

