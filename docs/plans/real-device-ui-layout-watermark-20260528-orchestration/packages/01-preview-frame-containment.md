# Package 01 - Preview Frame Containment

## Package ID

`01-preview-frame-containment`

## Goal

Fix issue 2 from the 2026-05-28 screenshots: the frame-ratio guide must fit inside the actual live preview content. It must never extend beyond the preview window, into black cockpit space, or outside the visible camera surface.

## User Symptom Covered

- In screenshot 1, the frame/ratio guide visually exceeds the preview frame. This breaks the design contract and feels wrong on device.

## Branch And Worktree

- Branch: `agent/real-device-ui-layout-watermark-20260528/01-preview-frame-containment`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-01-preview-frame-containment`

## Allowed Paths

- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/res/layout/activity_main.xml` only if proving the overlay is constrained to a larger area than `PreviewView`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/SessionPreviewRenderModelTest.kt`
- narrowly scoped `app/src/test/java/com/opencamera/app/*Preview*Test.kt` tests if needed

## Forbidden Paths

- Do not change CameraX preview binding, capture pipeline, or saved-output frame-ratio processing unless a focused failing test proves frame selection leaks into runtime preview configuration.
- Do not shrink the guide by hardcoding screenshot-specific pixel offsets.
- Do not create another preview/session state owner in UI.
- Do not edit another package status file or `INDEX.md`.

## Required Investigation

1. Inspect `PreviewView` and `PreviewOverlayView` constraints in `activity_main.xml`; confirm whether both occupy exactly the same bounds.
2. Audit `PreviewOverlayView.computeFrameRect(...)` and any preview-content geometry helper. Determine whether it uses view bounds rather than actual preview-content bounds.
3. Reproduce the failing geometry with deterministic tests for tall portrait surfaces similar to the provided screenshots.
4. If the live preview content may letterbox inside the overlay, introduce an explicit content rect and compute the frame guide inside that rect.
5. Preserve the existing architecture: UI renders state and dispatches intents; session/device ownership stays outside the overlay.

## Acceptance Criteria

- `16:9`, `4:3`, and `1:1` frame rectangles are always contained by the computed preview content rect in portrait and landscape tests.
- `PreviewOverlayView` distinguishes overlay view bounds from preview content bounds where needed.
- The guide remains visually centered and stable when bottom controls and the mode track are present.
- The fix does not alter CameraX preview scale type or request a preview rebind solely because frame ratio changes.
- The status file records any remaining true-device visual risk.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.SessionPreviewRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

If one of the named tests does not exist, add the smallest relevant test or record why the nearest existing test is the correct substitute.

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Geometry before/after summary.
- Proof that frame rectangles are contained by preview content bounds.
- Verification command summaries.
- Residual device QA notes tied to screenshot 1.

## Unlock Condition

Package `02-bottom-cockpit-density` may start after this package is completed and has a recorded commit.
