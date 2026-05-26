# 2026-05-25 Watermark 2.0 Product Upgrade Index

> For agentic workers: use `rtk` for every shell command. Use `superpowers:executing-plans` or `superpowers:subagent-driven-development` before implementing any package from this index. These plans are for text/code agents; Codex/user keeps final visual QA for saved JPEG appearance.

## Goal

Turn the approved Watermark 2.0 direction into an executable package: professional parameter bottom-bar watermark, minimalist text watermark, blurred four-border watermark, and reversible watermark. The package must build on the current implementation instead of restarting the older 2026-05-24 plans.

## User Request

The user approved upgrading the watermark plan with:

- professional parameter bottom-bar watermark;
- minimalist text watermark;
- blurred four-border watermark;
- reversible watermark.

## Verified Current Facts

- `codex/agent_plans/2026-05-24-multi-watermark-design-index.md` already planned multi-template still-photo watermark work.
- `codex/agent_plans/2026-05-24-reversible-watermark-archive-index.md` already planned OCWM self-contained reversible watermark archive work.
- Current `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt` registers five templates: `classic-overlay`, `travel-polaroid`, `retro-frame`, `pure-text`, and `blur-four-border`.
- Current `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` recognizes and renders `pure-text` and `blur-four-border`, resolves EXIF-backed datetime/location/camera params, and embeds OCWM archive bytes after visible write/EXIF restore.
- Current `scripts/verify_stage_6b3_watermark_v2.sh` covers settings/render/effect/session/app assembly, but its internal Gradle helper still invokes `./gradlew`; invoke the script itself through `rtk` per project rules.
- Current `scripts/verify_reversible_watermark_archive.sh` verifies OCWM container tests, app archive tests, Python extractor syntax, and byte-identical extraction.

## Package Documents

| Package | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Professional Parameter Bottom Bar](./2026-05-25-watermark-2-professional-parameter-bottom-bar.md) | Text/code agent + Codex visual QA | implemented | Added still-photo `professional-bottom-bar` template, persisted style, resolver, renderer branch, UI model coverage, and archive tests; final visual QA remains. |
| [Minimalist Text Polish](./2026-05-25-watermark-2-minimalist-text-polish.md) | Text/code agent + Codex visual QA | validated | Existing `pure-text` remains typography-only in renderer and hides frame background in Watermark Lab; final visual QA remains. |
| [Blurred Four-Border Polish](./2026-05-25-watermark-2-blur-four-border-polish.md) | Text/code agent + Codex visual QA | blocked | UI cycles only blur backgrounds and resolver clamps invalid background, but direct persisted action still accepts invalid solid backgrounds; see validation notes. |
| [Reversible Watermark Productization](./2026-05-25-watermark-2-reversible-productization.md) | Text/code agent + Codex/user acceptance | validated | OCWM archive verification passes, including byte-identical Python extraction; final gallery/device smoke remains. |

## Recommended Execution Order

1. Execute minimalist text and blurred four-border polish only if the current implementation has not already satisfied their acceptance criteria. They are refinements on existing code.
2. Execute professional parameter bottom bar after confirming template/style serialization patterns, because it adds a new template and will touch settings, renderer, strings, and tests.
3. Execute reversible watermark productization after renderer changes, so the archive continues to embed the pre-watermark bytes for every visible template.
4. Run focused watermark verification first, reversible archive verification next, then Stage 7 observability only after implementation packages land.

## Related Recent Plans

- `2026-05-24-multi-watermark-design-index.md`: base multi-template watermark plan. Current code appears to have implemented the target five-template set, so this package supersedes it for Watermark 2.0 product polish and the new professional bottom bar.
- `2026-05-24-reversible-watermark-archive-index.md`: base OCWM archive plan. Current code appears to have implemented core archive embedding/extraction; this package keeps productization and verification scope.
- `2026-05-23-session-ui-render-model-split-index.md`: relevant if implementation touches the large `SessionUiRenderModel.kt`; keep changes localized and do not use Watermark 2.0 as an excuse for broad render-model splitting.

## Non-Goals

- Do not burn watermarks into video frames.
- Do not implement AI watermark removal, weather/location network lookup, branded OEM marks, or a template marketplace.
- Do not change OCWM archive binary format unless the reversible archive tests prove an actual bug.
- Do not move final JPEG rendering into UI, mode plugins, session, coordinator, or adapter code.
- Do not enter a new project stage without explicit user approval.

## Validation After Packages Land

- Focused still-watermark gate:

```bash
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
```

- Reversible archive gate:

```bash
rtk ./scripts/verify_reversible_watermark_archive.sh
```

- Broader Stage 7 regression gate:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Final product acceptance still needs Codex/user visual review of saved JPEGs for at least a bright scene, dark scene, portrait-or-human subject scene, and narrow/tall crop.

## Validation 2026-05-26

Status: `blocked`, not an overall pass.

What passed:

- Focused Gradle subset passed:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
```

- Reversible archive gate passed outside the sandbox after the in-sandbox Gradle wrapper lock failed:

```bash
rtk ./scripts/verify_reversible_watermark_archive.sh
```

Evidence from code inspection:

- `professional-bottom-bar` is present in `DEFAULT_WATERMARK_TEMPLATES`, `PhotoSettings`, `SettingsActions.watermarkStyleFor`, `PersistedSettingsSerializer`, `PhotoWatermarkPostProcessor` resolver/render path, and archive tests.
- `pure-text` hides frame background control in Watermark Lab and still renders through the photo watermark postprocessor.
- `blur-four-border` UI cycles blur-family backgrounds and bottom placements, and resolver clamps unsupported solid backgrounds.
- OCWM archive embedding still runs in the watermark editor path and extractor verification produced a byte-identical payload.

Blocking gaps:

- `rtk ./scripts/verify_stage_6b3_watermark_v2.sh` fails in `:core:effect:compileTestKotlin` because tests still reference removed/renamed `PreviewColorTransform.colorMatrix` and `PreviewEffectRenderModel.colorFidelity`. This blocks the official focused Watermark V2 gate even though the watermark-specific subset passed.
- `blur-four-border` is still not fully protected at the persisted-action boundary: `PersistedSettingsAction.UpdateWatermarkFrameBackground(templateId = "blur-four-border", background = WHITE/DARK)` can store an invalid solid background even though UI does not generate it and the resolver clamps at render time.

Not completed:

- Stage 7 gate was started but hung for several minutes at `:core:session:test`; the run was stopped and is not counted as pass evidence.
- No Codex/user visual QA of saved JPEGs has been performed.
