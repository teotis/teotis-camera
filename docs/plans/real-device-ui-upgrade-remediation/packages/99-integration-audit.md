# Package 99 — Final Integration Audit

## Package ID
`99-integration-audit`

## Goal

Codex validates that packages 01-05 actually satisfy the original user problems 9, 11, 12, and 15. The audit must verify code, tests, status evidence, and cross-package semantics.

## Audit Acceptance Criteria

- [ ] Read INDEX.md, all package docs, and all status files.
- [ ] Confirm no package agent edited forbidden paths.
- [ ] Confirm effect tests compile and pass.
- [ ] Confirm app assemble passes.
- [ ] Confirm 16:9 overlay is bounded to actual preview content, not full view.
- [ ] Confirm preview/capture zoom semantics match: full-lens preview plus smaller saved capture frame, or a clearly documented degraded state.
- [ ] Confirm Color Lab preview either truly applies to preview pixels or is honestly labeled approximate/degraded with no false GOOD claim.
- [ ] Confirm focal slider allows continuous final values and tested preset jumps.
- [ ] Output PASS / PARTIAL / FAIL with concrete file and command evidence.

## Verification Commands

```bash
rtk git status --short
rtk git log --oneline -20
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.PreviewColorTransformOverlayTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```
