# Package 99 - Integration Audit

## Package ID

`99-integration-audit`

## Goal

After packages 01 and 02 finish, Codex verifies that Watermark 2.0 can be moved from blocked to acceptance-ready for local/text gates, or records the remaining blocker with exact evidence.

## Context

Known validation state before this orchestration:
- Professional parameter bottom bar is implemented across settings, serializer, resolver, renderer, UI model, and archive tests.
- Minimalist text watermark is validated as typography-only and hides frame background controls.
- Reversible watermark archive verification passed with byte-identical extraction.
- Official 6B3 Watermark V2 gate is blocked by `core:effect` test compile drift.
- `blur-four-border` still accepts invalid solid backgrounds through direct persisted action.

## File Ownership

- Allowed paths:
  - `docs/plans/2026-05-25-watermark-2-product-upgrade-index.md`
  - `docs/plans/2026-05-25-watermark-2-blur-four-border-polish.md`
  - `docs/plans/INDEX.md`
  - `codex/documentation.md`
  - `docs/plans/watermark-2-validation-fix-orchestration/status/99-integration-audit.md`
- Forbidden paths:
  - Runtime Kotlin implementation files unless the user explicitly asks Codex to repair a missed blocker.
  - Other package status files.

## Audit Steps

1. Read `status/01-effect-preview-api-drift.md` and `status/02-blur-border-settings-guard.md`.
2. Inspect diffs/commits from both implementation packages.
3. Run focused verification in the main workspace after merges land.
4. Run the official Watermark V2 gate.
5. Run reversible archive verification if Watermark renderer/archive paths changed or if the official gate passes.
6. Update the Watermark plan ledger and `codex/documentation.md` only with verified evidence.

## Verification Commands

Run from the main workspace unless auditing a worktree. Main workspace Gradle commands may use direct `rtk ./gradlew`; worktrees must use `rtk ./scripts/run_isolated_gradle.sh`.

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewColorTransformTest --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
rtk ./scripts/verify_reversible_watermark_archive.sh
```

Stage 7 remains the broader milestone gate. Run it only after focused gates are green or if the user explicitly asks for the broader stage check:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Acceptance Criteria

- Both implementation status files contain evidence packs.
- The official 6B3 Watermark V2 gate passes, or any remaining failure is documented as a new blocker unrelated to the two known issues.
- Direct persisted-action invalid background behavior is covered by a settings-level test.
- Reversible archive behavior remains passing if touched.
- `docs/plans/2026-05-25-watermark-2-product-upgrade-index.md`, `docs/plans/INDEX.md`, and `codex/documentation.md` reflect the verified result.

## Expected Evidence Pack

- Commands run and pass/fail summary.
- Final status recommendation: `validated`, `blocked`, or `partly validated`.
- Any remaining unresolved risk, especially real-device/gallery smoke gaps.

