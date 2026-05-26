# 2026-05-24 Multi Watermark Design Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are written for text-only agents and do not require screenshot or video analysis unless explicitly marked as visual QA.

## Goal

Upgrade OpenCamera still-photo watermarks into a scalable multi-template system with at least:

- a pure text watermark that burns only typography into the captured image;
- a blurred four-border watermark that frames the photo with source-image blur on all four sides;
- continued support for existing `classic-overlay`, `travel-polaroid`, and `retro-frame` behavior.

The feature must preserve the project boundary: mode plugins declare watermark intent, session carries effect settings, media pipeline renders final output, and UI only renders settings/preview state and dispatches settings actions.

## Current Code Facts

- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt` defines `WatermarkTemplate`, `WatermarkStyleSettings`, `PhotoSettings`, and `FeatureCatalog`.
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt` currently registers three templates: `classic-overlay`, `travel-polaroid`, and `retro-frame`.
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt` already supports per-template placement, scale, opacity, and frame background updates.
- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt` persists the three existing per-template styles.
- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt` writes watermark template/style values into metadata tags.
- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt` builds `WatermarkEffect` from persisted settings and selected catalog template.
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` owns final JPEG watermark rendering and OCWM reversible archive embedding.
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` renders only lightweight preview watermark hints, not final output.
- `scripts/verify_stage_6b3_watermark_v2.sh` is the focused watermark verification entry.

## Product Direction

Use a flagship-camera product grammar without copying any OEM brand:

- **Pure text** should feel like Apple-style quiet output metadata: small, legible, no card, no decorative container, strong shadow only when needed for contrast.
- **Blurred four-border** should feel closer to vivo/OPPO travel/frame styles: the center photo remains inspectable, the border is made from the same image, and text sits in the lower border with calm contrast.
- **Existing templates** should keep their current semantics. Do not turn `classic-overlay` into pure text or `retro-frame` into the new blur frame.
- Settings must show only controls that apply to the selected template. For example, pure text has no frame background control; blurred four-border shows only blur-family backgrounds, not dark or white solid backgrounds.

## Recommended Work Packages

1. [Settings And Contract Expansion](./2026-05-24-multi-watermark-settings-and-contracts.md)
   - Add template capabilities, new template IDs, per-template persisted styles, serializer support, render-model filtering, and focused settings tests.

2. [Renderer And Pipeline Integration](./2026-05-24-multi-watermark-renderer-and-pipeline.md)
   - Split the current watermark renderer enough to add `pure-text` and `blur-four-border` safely inside `PhotoWatermarkPostProcessor.kt`.
   - Preserve EXIF restore, OCWM archive embedding, and pipeline diagnostics.

3. [UI Preview And Verification](./2026-05-24-multi-watermark-ui-preview-verification.md)
   - Improve watermark selector/detail render models and preview hints so users understand the chosen template before capture.
   - Extend focused verification and document the visual QA that remains device/multimodal-owned.

## Recommended Sequence

Execute package 1 first. It establishes durable settings/catalog semantics and prevents UI from offering invalid controls.

Execute package 2 second. It consumes the new template IDs and capability hints in the final JPEG renderer.

Execute package 3 last. It tightens preview/settings feedback and adds the regression gate that proves the two new templates are not metadata-only placeholders.

## Target Template Set

```text
classic-overlay
  Existing in-image info overlay with translucent dark backing.

pure-text
  New in-image typography-only watermark.
  No frame expansion.
  No backing card.
  Placement, text size, and opacity apply.

travel-polaroid
  Existing expanded lower-frame travel style.

retro-frame
  Existing expanded lower-frame retro style.

blur-four-border
  New expanded frame.
  Source-image blur visible on left, top, right, and bottom borders.
  Center image remains unblurred.
  Text sits in the lower border and uses filtered contrast.
```

## Non-Goals

- Do not implement AI watermark removal.
- Do not change the OCWM reversible archive format.
- Do not burn watermarks into video frames in this package.
- Do not implement Live Photo motion-segment watermark rendering in this package.
- Do not introduce a second hidden session owner in UI, coordinator, or adapter code.
- Do not move watermark rendering into mode plugins or `MainActivity`.
- Do not enter a new project stage without explicit user approval.

## Global Acceptance

- `FeatureCatalog.DEFAULT_WATERMARK_TEMPLATES` exposes at least five templates: the current three plus `pure-text` and `blur-four-border`.
- Persisted settings round-trip every template style through `PersistedSettingsSerializer`.
- Watermark detail UI only exposes valid controls for each template.
- Still capture metadata carries the selected template and style through `EffectBridge`.
- `PhotoWatermarkPostProcessor` renders visible output for `pure-text` and `blur-four-border`.
- Unknown templates still degrade to `classic-overlay` with the existing warning path.
- Existing watermark rendering, OCWM archive embedding, and metadata notes continue to pass.
- Focused verification passes:

```bash
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
```

- Stage 7 observability verification remains the broader regression gate after implementation:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Conflict Warnings

- Do not make `watermarkText` alone decide the visual style. Template ID plus style settings are the source of truth.
- Do not expose solid `dark` or `white` frame backgrounds for `blur-four-border`; this template is specifically source-blur based.
- Do not let preview hints claim exact final rendering. The final output is owned by the media postprocessor.
- Do not change postprocessor order in `AppContainer` for this feature. The watermark should still run after upstream visual processors and before selfie mirror/pipeline metadata unless a separate approved plan changes that order.
- Do not mark the feature complete from settings changes alone. The saved JPEG must visibly change for both new templates.
