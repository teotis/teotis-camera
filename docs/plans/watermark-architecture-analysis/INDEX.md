# Watermark Architecture Analysis

## Scope

This package records the 2026-06-03 watermark review requested after real-device feedback that `blur-four-border` still does not look convincingly blurred.

The review used:

- current repository code in `app/`, `core/effect`, `core/settings`, and `feature/mode-*`;
- prior session skeletons for Watermark 2.0 and latest vivo-reference feedback;
- existing plan documents under `docs/plans/`;
- the `abstraction-architect` lens for missing invariants, duplicate projections, and transition seams.

No production code was changed in this analysis pass.

## Key Finding

The immediate product bug is local: `blur-four-border` uses downsample-upscale edge strips, not a real blur kernel, and the tests mostly prove color derivation rather than blur strength.

The structural bug is broader: a watermark template is not represented as one canonical render contract. Template identity, allowed controls, preview shape, metadata tags, output renderer, visual QA, and tests are separate projections that can drift.

## Report

- [Interactive report](./report.html)

## Recommended Next Closed Loop

1. Add a failing pixel test using high-frequency edge detail that proves the current downsample-upscale renderer preserves too much structure.
2. Replace `drawContentAwareEdgeBorder` internals with a deterministic separable box/Gaussian-style blur helper that is testable on JVM/Robolectric.
3. Add a minimal `WatermarkTemplateRenderContract` builder or helper that maps template id to preview shape, allowed style values, default style, and output render behavior in one place.
4. Keep the first implementation scoped to `blur-four-border`; defer broader template unification until the local visual gap is fixed and verified.
