# OpenCamera UI/Interaction 2.0 Index

## Purpose

OpenCamera 2.0 turns the current screen from a set of feature panels into a coherent camera cockpit. The goal is not to add a new architecture layer. The goal is to make the existing View/XML app beautiful, obvious, and convenient while preserving the current ownership contract:

- UI renders state and dispatches intents.
- Session Kernel owns runtime camera state.
- Device Adapter owns CameraX and platform execution.
- Media Pipeline owns saved media and thumbnails.
- App shell owns Android-only behavior such as opening the gallery and dismissing panels.

## Design Principles

1. Beauty comes from restraint: preview-first surface, dark translucent controls, stable spacing, short Chinese labels, and a small cyan/green accent.
2. Interaction must be predictable: tap exact zoom selects exact zoom; tap exact mode selects exact mode; outside tap closes panels; recording always gives immediate feedback.
3. Camera convenience wins over feature exposure: first screen keeps high-frequency capture actions; secondary panels carry medium-frequency setup and lab features.
4. Every secondary surface uses one shared panel grammar so Lens Lab, Tone Lab, Portrait Lab, Watermark Lab, Pro Controls, Quick, and Dev Log feel like one product.
5. Text specs are authoritative. Reference images are supporting material for humans and later multimodal review, not the source of truth for text-only implementation agents.

## Document Map

- `01_camera_cockpit_wireframes.md` defines the main cockpit layout, text wireframes, zone sizing, portrait/small-screen adaptation, and migration from the current XML structure.
- `02_visual_system.md` defines color, typography, spacing, shape, opacity, control states, and component styling.
- `03_interaction_grammar.md` defines tap, swipe, pinch, long press, outside-close, disabled, conflict, feedback, and analytics/dev-log interaction rules.
- `04_camera_function_convenience.md` defines how capture, recording, modes, zoom, lens, tone, portrait, watermark, and pro features are layered.
- `05_panel_system_and_labs.md` defines the shared panel architecture and per-lab content: Lens Lab, Tone Lab, Portrait Lab, Watermark Lab, Pro Controls, Quick, and Dev Log.
- `06_reference_image_pack.md` defines the reference image inventory, prompts, output paths, usage rules, and regeneration checklist.
- `90_multimodal_deferred_visual_review.md` isolates work that needs visual interpretation: screenshot annotation, device visual QA, image comparison, icon/animation review.

## Relationship With 1.0 Fix Packages

The 1.0 remediation plans should land first when they touch active bugs:

- `docs/plans/2026-05-21-interaction-routing-and-hit-targets.md`
- `docs/plans/2026-05-21-media-result-thumbnail-latency-devlog.md`
- `docs/plans/2026-05-21-panel-localization-and-visual-system.md`

2.0 builds on those fixes and should not reintroduce the old behavior:

- Zoom chips dispatch exact `ApplyZoomRatio(ratio)`.
- The visible thumbnail prefers the latest saved media and is not overwritten by preview snapshots after capture.
- Secondary panels dismiss on outside tap.
- Default UI copy is Chinese via `AppTextResolver` and string resources.
- Recording has visible start, recording, stopping, and saved feedback.
- Thumbnail tap opens the latest saved media in the Android viewer.

If a 1.0 implementation is still in progress, 2.0 agents should avoid editing the same production files in parallel. Use these documents to stage work, then assign one integrator for `MainActivity.kt` and `activity_main.xml`.

## Recommended Implementation Phases

### Phase A: 2.0 IA Skeleton

Files likely touched:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

Work:

- Introduce a single app-shell panel route enum, for example `CockpitPanelRoute`.
- Collapse scattered booleans such as settings/filter/quick/dev panel flags into one active route plus optional nested route data.
- Add `CameraCockpitRenderModel` as an app-layer render model that groups top status, right rail, zoom strip, mode track, bottom cockpit, thumbnail command, recording affordance, disabled reasons, and active panel summary.
- Keep session state ownership in the Session Kernel. The render model can derive UI affordances only.

Acceptance:

- One active panel at a time.
- Back/outside tap closes the active panel.
- Main screen can render photo, saving, recording, stopping, permission-denied, and preview-error states from one model.

### Phase B: 2.0 Visual Components

Files likely touched:

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/*`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`

Work:

- Rebuild the main screen into stable zones: top status, preview action rail, zoom strip, mode track, bottom cockpit.
- Replace multi-line button blocks with structured rows and chips.
- Create shared panel shell styles, row styles, segmented tabs, chips, status badges, and disabled reason labels.
- Keep `FilterPaletteView` or equivalent for Tone Lab palette; make it visually recognizable.

Acceptance:

- Chinese labels fit on small screens without vertical wrapping.
- Bottom cockpit is shorter, balanced, and usable with one thumb.
- Top content respects safe area and does not collide with status/cutout regions.

### Phase C: 2.0 Feature Convenience

Files likely touched:

- `SessionUiRenderModel.kt`
- `AppTextResolver.kt`
- string resources
- panel rendering code in `MainActivity.kt`
- tests for render models and gesture policies

Work:

- Reorder labs by user goal: Lens Lab, Tone Lab, Portrait Lab, Watermark Lab, Pro Controls, Quick, Dev Log.
- Add visible support/degraded/unsupported semantics to every control row.
- Add short feedback for mode change, lens switch, zoom, saving, recording, and panel actions.
- Make Dev Log a readable product surface while retaining machine-useful key events.

Acceptance:

- Common capture tasks complete from first screen.
- Medium-frequency features are one tap plus one obvious panel action away.
- Unsupported camera features explain why they are unavailable.

## Global Acceptance Checklist

- UI美观性: no oversized dark blocks, no nested-card clutter, consistent type scale, consistent buttons, preview remains the dominant surface.
- 交互直观性: tap targets match visible controls, all state changes have short feedback, panel route is predictable, disabled state includes reason.
- 功能方便性: photo/video shutter, mode, zoom, lens, thumbnail, tone, and quick entry are reachable on first screen; advanced controls are organized by lab.
- Localization: default Chinese copy, English fallback, no hard-coded user-facing English in render models.
- Accessibility baseline: touch targets at least 44dp where practical, content descriptions for icon-only controls, readable contrast over preview.
- Architecture: no second session kernel; no UI-local camera runtime state; no Device Adapter calls directly from UI.

## Verification Plan

Focused tests should be added before production behavior changes. Minimum commands after each implementation phase:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

After a meaningful Stage 7-adjacent loop:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Manual smoke on a device remains required for visual and camera timing judgments.

## Non-Goals

- Do not migrate to Compose.
- Do not start a new project stage.
- Do not duplicate Session Kernel state in `MainActivity`.
- Do not hide unsupported hardware behavior without an explicit unsupported/degraded reason.
- Do not make the reference images the only implementation input.
