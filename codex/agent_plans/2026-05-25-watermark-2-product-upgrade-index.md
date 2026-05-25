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
| [Professional Parameter Bottom Bar](./2026-05-25-watermark-2-professional-parameter-bottom-bar.md) | Text/code agent + Codex visual QA | planned | Add a new still-photo template for OPPO/Hasselblad-style bottom parameter bar without copying OEM branding. |
| [Minimalist Text Polish](./2026-05-25-watermark-2-minimalist-text-polish.md) | Text/code agent + Codex visual QA | planned | Productize existing `pure-text` so it is quiet, legible, and does not expose frame-only controls. |
| [Blurred Four-Border Polish](./2026-05-25-watermark-2-blur-four-border-polish.md) | Text/code agent + Codex visual QA | planned | Tighten existing `blur-four-border` template behavior, controls, and renderer evidence. |
| [Reversible Watermark Productization](./2026-05-25-watermark-2-reversible-productization.md) | Text/code agent + Codex/user acceptance | planned | Surface and verify OCWM reversible watermark behavior without changing the archive format. |

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
