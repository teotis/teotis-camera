# Final Report

## Orchestration: Real Device Watermark And Zoom Preview Fix

**Date**: 2026-05-28
**Status**: finalized

## Summary

Two real-device preview problems were fixed:
1. Watermark template preview now shows template-specific visual affordances (BOTTOM_BAR, TEXT_ONLY, BACKED_TEXT, EXPANDED_FRAME, FOUR_BORDER) on the live preview surface
2. Zoom threshold crossing now switches the CameraX live preview to the corresponding physical lens node (2x/5x) for both still-photo and video preview

## Packages

| Package | State | Commit | Key Changes |
|---|---|---|---|
| 01-watermark-template-preview-expectation | completed | `dad5284` | Added BOTTOM_BAR shape, template-specific preview mapping, staged watermark hint override, drawWatermarkBottomBarHint |
| 02-zoom-threshold-live-preview-switch | completed | `18e1334` | Removed 3 STILL_CAPTURE guards from CameraXCaptureAdapter so still-photo preview follows slider lens node switch |
| 03-integration-real-device-smoke-protocol | completed | (verification-only) | Ran all integration tests, produced real-device smoke checklist, verified package 01+02 evidence |
| 99-finalize | finalized | (integration) | Merged all packages, verified, merged to main |

## Integration

- **Integration branch**: `agent/watermark-zoom-preview-fix/integration`
- **Mainline merge**: `733f584` (fast-forward)
- **Merge order**: 01 → 02 → 03 (no conflicts)

## Verification

All 6 integration verification commands passed:

| Command | Result |
|---|---|
| `:core:effect:test --tests PreviewEffectAdapterTest` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest --tests SessionPreviewRenderModelTest + SessionUiRenderModelTest + PreviewOverlayGeometryTest` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest --tests FocalLengthSliderViewTest + SessionCockpitRenderModelTest` | BUILD SUCCESSFUL |
| `:app:testDebugUnitTest --tests CameraSessionCoordinatorTest + CameraXCaptureAdapterCapabilityDetectionTest + CameraXCaptureAdapterRuntimeIssueTest` | BUILD SUCCESSFUL |
| `:core:session:test --tests DefaultCameraSessionTest` | BUILD SUCCESSFUL |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

Stage 7 observability: BUILD SUCCESSFUL

## APK

- Path: `/Volumes/Extreme_SSD/project/open_camera/public/app-debug.apk`

## Real-Device Smoke Checklist (from package 03)

### A. Watermark Template Preview
| # | Check | Expected |
|---|---|---|
| A1 | Select `professional-bottom-bar` | Preview shows bottom parameter bar strip |
| A2 | Select `pure-text` | Preview shows text-only overlay |
| A3 | Select `blur-four-border` | Preview shows four-border effect |
| A4 | Select `travel-polaroid`/`retro-frame` | Preview shows expanded frame border |
| A5 | Select `classic-overlay` | Preview shows text with backing background |
| A6 | Switch template on detail page | Preview updates immediately |
| A7 | Adjust placement/opacity/scale | Approximate changes visible in preview |

### B. Zoom Threshold Preview Switch
| # | Check | Expected |
|---|---|---|
| B1 | Drag from 1x past 2x | Preview switches to 2x optical view |
| B2 | Drag back below 2x | Preview returns smoothly |
| B3 | Drag from 1x past 5x | Preview switches to 5x periscope view |
| B4 | Drag back below 5x | Preview returns smoothly |
| B5 | Device lacks 2x/5x nodes | Degraded/digital behavior shown honestly |
| B6 | Drag near 2x threshold repeatedly | Hysteresis prevents jitter |
| B7 | Drag slider in still-photo mode | Preview follows lens node switch |

### C. Combined
| # | Check | Expected |
|---|---|---|
| C1 | Set watermark + drag past 2x/5x | Watermark overlay persists correctly |
| C2 | Take photo and check saved output | Watermark renders via saved-photo pipeline |

## Residual Risks

1. Physical lens switching perception requires real-device verification
2. Bottom-bar placement and text sizing need on-device visual confirmation
3. Hysteresis delta (0.1) may need tuning for real-device slider sensitivity
4. Preview watermark is approximate — differs from saved-photo rendering
5. Template selector page does not yet show per-item live preview
6. vivo X300 CameraX zoom ratio behavior may differ from reference implementation

## Cleanup

- Package worktrees and branches will be deleted after this report is confirmed.
