# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/INDEX.md`
- Packages:
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/01-effect-test-contract.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/02-color-lab-preview-execution.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/03-preview-content-bounds.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/04-preview-capture-zoom-semantics.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/05-focal-slider-interaction.md`
  - `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/packages/99-integration-audit.md`
- Status files: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-upgrade-remediation/status/*.md`

## Audit Steps

1. Read INDEX.md and all package docs.
2. Read all status files.
3. Run:
   ```bash
   rtk git status --short
   rtk git diff --stat
   rtk git log --oneline -20
   ```
4. For each package, check every acceptance criterion.
5. Run integration-level verification:
   ```bash
   rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
   rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.PreviewColorTransformOverlayTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
   rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
   ```
6. Check cross-package conflicts:
   - Did any agent edit a file it was not assigned?
   - Are there duplicate implementations of color preview, frame geometry, or zoom crop?
   - Does preview/capture zoom now match the original user requirement?
   - Does Color Lab preview avoid false fidelity claims?
   - Does focal slider keep continuous final values and preset jumps?
7. Report PASS / PARTIAL / FAIL with specific evidence for each gap.

## Evidence Required

- Per-package acceptance criteria status: met / unmet / unverifiable.
- Integration test results.
- Cross-package conflict report.
- Final recommendation: merge / fix-then-merge / do-not-merge.
