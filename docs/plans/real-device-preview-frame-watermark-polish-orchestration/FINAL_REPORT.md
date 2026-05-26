# Real Device Preview Frame And Blur Watermark Polish - Final Report

## Verdict

PASS for local source integration and focused verification. Real-device visual QA is still recommended because both original findings came from device screenshots/experience.

## What Was Integrated

- `01-preview-frame-contract`
  - Integrated as `c7c5f06`.
  - Adds deterministic tests proving `FrameEffect` is capture-targeted, frame rectangles stay within preview content bounds, and saved crop semantics stay centered and ratio-correct.
  - Conflict resolution preserved current main's `captureCropZoom` tests and added the package's center/ratio acceptance tests.

- `02-natural-blur-border-rendering`
  - Integrated as `34b234c`.
  - Adds content-aware edge extension for `blur-four-border` using source top/bottom/left/right strips instead of global stretched blur plus heavy pale tint.
  - Adds Robolectric bitmap tests for edge-derived border color and non-washed-out dark edges.

## Verification

Passed:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

Not run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Merge / Branch State

- Main branch contains both functional commits.
- `01-preview-frame-contract` original worktree branch remains preserved.
- `02-natural-blur-border-rendering` original commit was cherry-picked from `a7370b6`; its recorded worktree path no longer exists in the current worktree list.
- No worktrees or branches were deleted.

## Remaining Risks

- Confirm on a real device that `16:9`, `4:3`, and `1:1` frames stay inside the visible preview content under actual `PreviewView` scaling.
- Confirm on real photos that `blur-four-border` is visually natural across dark, bright, high-saturation, and low-detail edges.
- Stage 7 full gate was intentionally not run during this finalize pass.
