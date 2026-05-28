# 01-preview-frame-containment Status

## State

`completed`

## Evidence

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-01-preview-frame-containment`
- Branch: `agent/real-device-ui-layout-watermark-20260528/01-preview-frame-containment`
- Base commit: `e6e878b`
- Commit hash: `b131f81`
- Changed files:
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt`
- Verification:
  - `PreviewOverlayGeometryTest`: all tests passed
  - `PreviewContentGeometryTest`: all tests passed (including 10 new containment tests)
  - `SessionPreviewRenderModelTest`: all tests passed
  - `SessionUiRenderModelTest`: all tests passed
  - `SessionCockpitRenderModelTest`: all tests passed
  - `assembleDebug`: BUILD SUCCESSFUL

## Geometry Summary

**Before**: `previewContentGeometry` computed `activeFrameRect` from `computeFrameRect` without explicit containment guard. Mathematically, `computeFrameRect` always produces rects within the given dimensions (verified by proof), but no defensive clamp existed.

**After**: Added explicit `coerceIn` containment guard on all four edges of `activeFrameRect` to ensure it stays within `contentRect`. Added 10 new integration tests verifying frame containment across all ratio combinations (4:3, 16:9, 1:1 frames in 4:3, 16:9, 1:1 content aspects) in portrait and tall portrait views.

## Residual Device QA Notes

- The `PreviewOverlayView` and `PreviewView` share identical layout constraints in `activity_main.xml` (both constrained from parent top to `modeTrackScroll` bottom, both with `-20dp` top margin).
- `previewContentAspect` is set from `state.previewRatio` via `previewRatioToContentAspect()`. When `previewRatio == FULL`, `previewContentAspect` is null, and the content rect defaults to the full view dimensions.
- **Risk**: If the camera sensor aspect ratio (typically 4:3) differs from the view aspect ratio (typically 9:16), and the user selects FULL preview ratio, the content rect may include letterbox areas. The frame guide could visually appear in these letterbox areas. This is an architectural gap: the camera sensor aspect ratio is not available in `SessionState`. A future enhancement could add `sensorContentAspect` to `SessionState` to always compute the content rect based on the actual camera sensor dimensions.
- Real-device visual verification of frame guide containment is required for all preview ratios (FULL, 4:3, 16:9, 1:1) in portrait orientation.

## Blocker Context

- last_error: none
- failed_command: none
- conflict_files: none
- log_summary: all tests passed, APK built successfully
- recovery_hint: none
