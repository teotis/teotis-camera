# Final Integration Audit

## Context

- INDEX: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/rendering-2-0-validation-fix-orchestration/INDEX.md`
- Packages:
  - `packages/01-postprocess-outer-guard.md`
  - `packages/02-recipe-single-truth.md`
  - `packages/03-preview-fidelity-honesty.md`
  - `packages/04-ledger-and-gate-honesty.md`
- Status files: `status/*.md`

## Audit Steps

1. Read INDEX.md and all package docs.
2. Read all status files.
3. Run `rtk git status --short --untracked-files=all`, `rtk git diff --stat`, and recent git log.
4. For each package, check every acceptance criterion.
5. Run integration-level verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest --tests com.opencamera.core.settings.ColorLabSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.RenderRecipeTest --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.PreviewColorTransformTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.CompositeMediaPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

6. Check cross-package conflicts:
   - Did any agent edit a file it was not supposed to?
   - Are there duplicate implementations of recipe parsing or preview transform?
   - Does app preview fidelity match actual rendering behavior?
   - Does save reliability preserve valid media after optional postprocess failure?
7. Report PASS / PARTIAL / FAIL with specific evidence for each gap.

## Evidence Required

- Per-package acceptance criteria status.
- Integration test results.
- Cross-package conflict report.
- Final recommendation: merge, fix-then-merge, or do-not-merge.

