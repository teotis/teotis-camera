# UI Components And Animation V2 Upgrade Index

## Package Goal

Upgrade the current camera cockpit from functional UI controls to a coherent 2.0 interaction surface for the approved scope: focus/exposure feedback V2, shutter state animation V2, Zoom Cockpit V2, panel transition and route continuity, and Quick Panel semantic controls.

This package is for non-multimodal implementation agents plus Codex/user final visual acceptance. It must preserve the existing architecture rule: UI renders state and dispatches intents; Session Kernel owns camera runtime state; Device Adapter owns CameraX/platform execution; Media Pipeline owns saved media.

## User Request

The user approved these UI/animation upgrade directions for OpenCamera 2.0:

- Focus / exposure feedback V2.
- Shutter button state animation V2.
- Zoom Cockpit V2.
- Panel transition and route continuity.
- Quick Panel semantic controls upgrade.

## Package Documents

| Work Package | Owner | Status | Notes |
| --- | --- | --- | --- |
| [Focus And Exposure Feedback V2](./2026-05-25-focus-exposure-feedback-v2.md) | Text/code agent + Codex visual QA | planned | Builds on the tap-focus geometry/lifetime package; adds polished transient feedback and optional EV gesture UI. |
| [Shutter State Animation V2](./2026-05-25-shutter-state-animation-v2.md) | Text/code agent + Codex visual QA | planned | Builds on shutter visual semantics repair; adds stateful Canvas/Drawable animation without changing capture ownership. |
| [Zoom Cockpit V2](./2026-05-25-zoom-cockpit-v2.md) | Text/code agent + Codex real-device QA | planned | Uses existing zoom session/device owner; adds exact preset strip plus continuous drag/pinch feedback. |
| [Panel Transition And Route Continuity](./2026-05-25-panel-transition-route-continuity.md) | Text/code agent | planned | Adds shared panel transition behavior around existing `CockpitPanelRoute`; no new session state. |
| [Quick Panel Semantic Controls V2](./2026-05-25-quick-panel-semantic-controls-v2.md) | Text/code agent + Codex visual QA | planned | Builds on quick panel regression repair; replaces button-like rows with semantic controls and state badges. |

## Related Recent Plans Used As References

- [OpenCamera UI/Interaction 2.0 Index](../v2_ui/00_v2_ui_index.md)
- [Camera Cockpit Wireframes](../v2_ui/01_camera_cockpit_wireframes.md)
- [Interaction Grammar](../v2_ui/03_interaction_grammar.md)
- [Preview Tap Feedback Geometry And Lifetime](./2026-05-25-preview-tap-feedback-geometry-and-lifetime.md)
- [Shutter State Visual Semantics Repair](./2026-05-25-shutter-state-visual-semantics-repair.md)
- [Quick Panel Regression Repair](./2026-05-25-quick-panel-regression-repair.md)
- [Capture, Preview, And Mode Track Repair](./2026-05-25-capture-preview-mode-track-repair.md)
- [vivo X300 UI Panel And Cockpit Polish](./2026-05-24-vivo-x300-ui-panel-cockpit-polish.md)

## Recommended Execution Order

1. Land or verify prerequisite bugfix packages first:
   - `2026-05-25-preview-tap-feedback-geometry-and-lifetime.md`
   - `2026-05-25-shutter-state-visual-semantics-repair.md`
   - `2026-05-25-quick-panel-regression-repair.md`
2. Implement Panel Transition And Route Continuity, because it gives other packages one route/animation grammar.
3. Implement Focus And Exposure Feedback V2 and Shutter State Animation V2. These touch different surfaces but both need the same short/interruption-friendly motion rules.
4. Implement Zoom Cockpit V2.
5. Implement Quick Panel Semantic Controls V2 after the regression repair is clean, because it changes panel controls rather than only repairing them.
6. Codex/user performs final visual acceptance on device or screenshots/recordings.

## Shared Constraints

- Do not migrate the app to Compose.
- Do not create a second session kernel in UI, renderer, adapter, coordinator, or panel code.
- Do not call CameraX or device APIs directly from UI controls.
- Do not hide unsupported or degraded device behavior behind polished animation.
- Do not add visible instructional text to the camera surface as a substitute for clear controls.
- Use short, interruptible animations. A polished animation that masks save failure, unsupported zoom, or recovery state is a product bug.

## Codex-Retained Work

Non-multimodal agents should implement deterministic code and tests only. Reserve these for Codex/user:

- Final visual judgment of animation timing, easing, color weight, and readability.
- Screenshot/recording comparison on vivo/OPPO/Apple-inspired product expectations.
- Real-device smoke for touch feel, panel layering, shutter feedback, and zoom drag latency.
- Final 2.0 GO/NO-GO product judgment.

## Package Acceptance

- Each visible state-changing action has immediate, truthful feedback.
- Focus, shutter, zoom, panel, and quick controls use one product grammar instead of five unrelated UI styles.
- Existing Stage 7 verification remains meaningful and passes after code implementation.
- All hardware-dependent or unavailable behavior remains supported/degraded/unsupported in UI and tests.

## Verification Baseline

Run focused verification from each package first. After all five packages land:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Status Notes

- Created: 2026-05-25.
- Current status: planned.
- Git status note: `rtk git status --short` failed in this workspace because the current `.git` metadata references a missing worktree path. This package only adds handoff documents and does not modify runtime code.
