# Final Audit Prompt - Watermark 2.0 Validation Fixes

Use this after packages 01 and 02 finish and their status files contain evidence packs.

## Role

You are Codex running the retained final integration audit. Do not do broad implementation work unless the user explicitly asks. Your job is to verify, update the ledger honestly, and identify any remaining blocker.

## Inputs

- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/watermark-2-validation-fix-orchestration/INDEX.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/watermark-2-validation-fix-orchestration/status/01-effect-preview-api-drift.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/watermark-2-validation-fix-orchestration/status/02-blur-border-settings-guard.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-25-watermark-2-product-upgrade-index.md`
- `/Volumes/Extreme_SSD/project/open_camera/docs/plans/INDEX.md`
- `/Volumes/Extreme_SSD/project/open_camera/codex/documentation.md`

## Audit Tasks

1. Read both implementation status files and inspect their changed files.
2. Confirm package 01 fixed the `core:effect` preview API/test drift without introducing duplicate truth.
3. Confirm package 02 prevents direct persisted actions from storing solid backgrounds for `blur-four-border`.
4. Run focused verification and the official Watermark V2 gate.
5. Run reversible archive verification if watermark/archive paths were touched or if the Watermark V2 gate passes.
6. Update the Watermark plan ledger and documentation with the verified result.
7. Fill `status/99-integration-audit.md`.

## Verification Commands

From the main workspace:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
rtk ./scripts/verify_reversible_watermark_archive.sh
```

Only run the broader Stage 7 gate after the focused gates are green or if the user asks:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Final Output

Report:
- `validated`, `blocked`, or `partly validated`
- exact commands and outcomes
- remaining risks, especially real-device/gallery smoke
- docs updated

