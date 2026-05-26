# Package 01 - Preview Frame Contract

## Package ID

`01-preview-frame-contract`

## Goal

Make frame-ratio selection a composition/capture concern, not a preview-stream concern. Selecting `16:9`, `4:3`, or `1:1` must keep the live preview as the current lens full-field view; the visible frame is only a crop guide, and saved still output must match that guide through a deterministic crop path or an explicitly degraded capability result.

## Problem Statement

Real-device feedback reports that switching to `16:9` still lets the frame exceed the preview window. The likely root cause is one of two failures:

- frame-ratio request leaks into CameraX preview/use-case configuration, changing preview stream aspect/resolution; or
- overlay geometry still computes content/frame rects from the wrong coordinate space and treats the frame ratio as the preview content shape.

Either way, the product contract is the same: preview content is the lens view; frame ratio is a saved-photo crop and UI composition guide.

## Architecture Direction

- Keep `FrameEffect` and `FrameRatioSelected` as mode/session composition signals.
- Do not rebind or resize CameraX preview just because frame ratio changed.
- Use a single content geometry owner for preview content bounds and active crop frame.
- Keep saved JPEG crop in media/postprocess unless a tested device capability path proves better.
- If capability is unsupported or degraded, surface that explicitly in diagnostics/status; do not silently claim visual parity.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt`
- narrowly scoped `core/device/**` or `core/media/**` contract files only if needed for explicit supported/degraded semantics
- `docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/01-preview-frame-contract.md`

Forbidden paths:
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
- `core/settings/**` unless a focused failing test proves settings are the source
- broad session kernel refactors
- new UI-only hidden camera state owners
- other packages' status files or orchestration indexes

## Dependencies

None, but do not run concurrently with existing active agents from:
- `docs/plans/real-device-ui-upgrade-remediation/packages/03-preview-content-bounds.md`
- `docs/plans/real-device-ui-upgrade-remediation/packages/04-preview-capture-zoom-semantics.md`

## Parallel Safety

Safe with package `02-natural-blur-border-rendering` because file ownership is disjoint.

## Implementation Scope

1. Audit the `FrameRatioSelected -> FrameEffect -> preview overlay -> saved crop` chain.
2. Prove with code/tests whether frame ratio changes CameraX preview binding, preview request aspect ratio, preview resolution, or only metadata/overlay state.
3. If leakage exists, remove it so still preview remains full-lens/full-content while frame ratio changes only the crop frame and saved still metadata/postprocess.
4. If geometry is the failure, make preview content bounds explicit and ensure 16:9/4:3/1:1 frames fit within actual preview content.
5. Make saved crop and overlay crop share the same center/ratio semantics.
6. Record any true-device residual risk in the status file.

## Acceptance Criteria

- [ ] A focused test proves frame-ratio selection does not request a CameraX preview rebind, preview aspect-ratio change, or preview resolution change solely due to `FrameRatioSelected`.
- [ ] `PreviewOverlayRenderModel` / preview geometry carries enough information to distinguish preview content bounds from active crop frame.
- [ ] `16:9`, `4:3`, and `1:1` frame rectangles never exceed the computed preview content rect in portrait and landscape tests.
- [ ] The visible frame and `PhotoFrameRatioPostProcessor` saved crop use matching center-crop semantics.
- [ ] The package preserves existing session/device ownership: UI renders state and dispatches intents only.
- [ ] If saved output cannot match the visible frame on a device path, diagnostics/status explicitly report degraded or unsupported behavior.

## Verification Commands

External agents in a worktree MUST use isolated Gradle:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

If this package changes device contracts, also run the narrow affected device tests and list them in the status file.

## Expected Evidence Pack

- Exact code path for frame-ratio selection before and after.
- Proof that still preview is not cropped/reconfigured by frame ratio selection.
- Geometry formula and tests for 16:9/4:3/1:1.
- Saved-output crop proof or explicit degraded/unsupported note.
- Verification command summaries.
- Self-certification that only allowed paths were touched.

## Risks And Notes

- Do not solve this by shrinking the overlay while CameraX preview is still being cropped or rebound.
- Do not solve this by making `PreviewView` crop itself to the selected frame ratio.
- Real-device QA remains required after deterministic tests, because the original symptom was visual.
