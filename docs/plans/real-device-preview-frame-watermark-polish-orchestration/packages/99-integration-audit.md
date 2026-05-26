# Package 99 - Integration Audit

## Package ID

`99-integration-audit`

## Goal

Codex validates that the preview/frame contract and natural blur-border rendering packages actually satisfy the user's real-device findings, without accepting status-only claims.

## Scope

Read the orchestration index, both package documents, and all status files. Then audit source changes, file ownership, tests, and unresolved risks. Fix only tiny, obvious documentation/status omissions; do not expand runtime scope.

## Acceptance Criteria

- [ ] Package `01-preview-frame-contract` meets every acceptance criterion or lists exact unmet criteria.
- [ ] Package `02-natural-blur-border-rendering` meets every acceptance criterion or lists exact unmet criteria.
- [ ] No package edited forbidden paths or another package's status file.
- [ ] No implementation introduced a hidden session/camera owner outside the established architecture.
- [ ] No implementation relies on fixed whitening to hide blur-frame visual defects.
- [ ] Final verification commands pass, or failures are classified as product blockers, environment blockers, or unrelated existing failures with evidence.
- [ ] Final result is reported as PASS, PARTIAL, or FAIL.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

Run the stage gate only if the focused verification is clean and the machine is not already running parallel Gradle work:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Evidence Required

- Per-package acceptance status.
- Git status and diff/commit summary.
- Verification command summaries.
- Cross-package conflict report.
- Final recommendation: merge, fix-then-merge, or do-not-merge.
