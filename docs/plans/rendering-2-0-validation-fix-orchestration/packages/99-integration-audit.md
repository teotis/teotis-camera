# Package 99 — Rendering 2.0 Integration Audit

## Goal

After packages 01-04 complete, Codex validates whether Rendering 2.0 can be accepted locally and what remains for real-device visual QA.

## Audit Steps

1. Read this orchestration `INDEX.md` and packages 01-04.
2. Read all `status/*.md` files.
3. Check current git status, recent commits, and changed files.
4. Verify each package touched only allowed paths.
5. Compare each package against its acceptance criteria.
6. Run final integration verification commands.
7. Report `PASS`, `PARTIAL`, or `FAIL`.

## Integration Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PerceptualColorRecipeTest --tests com.opencamera.core.settings.StyleColorPipelineTest --tests com.opencamera.core.settings.ColorLabSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.RenderRecipeTest --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest --tests com.opencamera.core.effect.PreviewColorTransformTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.CompositeMediaPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.PreviewColorTransformOverlayTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Final Report Requirements

- Per-package acceptance status.
- Verification command results.
- Cross-package conflict report.
- Remaining real-device visual QA checklist.
- Recommendation: merge, fix-then-merge, or do-not-merge.

