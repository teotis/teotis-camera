# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/INDEX.md`
- Packages:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/packages/01-preview-frame-contract.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/packages/02-natural-blur-border-rendering.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/packages/99-integration-audit.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/*.md`

## Audit Steps

1. Read `INDEX.md` and all package docs.
2. Read all status files.
3. Run:
   ```bash
   rtk git status --short
   rtk git diff --stat
   rtk git log --oneline -8
   ```
4. For each package, check every acceptance criterion.
5. Run integration-level verification:
   ```bash
   rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
   rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
   ```
6. Run `rtk ./scripts/verify_stage_7_observability.sh` only if focused verification is clean and no parallel Gradle work is active.
7. Check for cross-package conflicts:
   - Did any agent edit a file it was not supposed to?
   - Are there duplicate implementations of preview crop or blur background behavior?
   - Do changes conflict semantically with existing real-device UI or Watermark 2.0 remediation packages?
8. Report: PASS / PARTIAL / FAIL with specific evidence for each gap.

## Evidence Required

- Per-package acceptance criteria status: met / unmet / unverifiable.
- Integration test results.
- Cross-package conflict report.
- Final recommendation: merge / fix-then-merge / do-not-merge.
