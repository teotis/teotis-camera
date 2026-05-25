# Real Device UI Regression Index

Planning location: `codex/agent_plans/`

Date: 2026-05-25

## Goal

Convert three real-device findings into executable handoff work without asking non-multimodal agents to judge screenshots or device feel. The implementation package is delegated; final visual and interaction acceptance stays with Codex/user on device.

## User Findings

| Item | User finding | Status | Handoff |
| --- | --- | --- | --- |
| 1 | Shutter button is an inappropriate purple color, disappears after tap, and blocks user operation until final photo appears. | Verified as plausible from code: Material `Button` tint can override custom shutter drawable; current render path disables shutter while photo active/saving. | [Capture, Preview, And Mode Track Repair](./2026-05-25-capture-preview-mode-track-repair.md) |
| 2 | Preview window is too low and should move upward, sitting properly above the mode switch column. | Verified as plausible from layout: preview is full-screen while mode track and bottom sheet overlay it at the bottom; there is no explicit preview-to-mode-track guide. | [Capture, Preview, And Mode Track Repair](./2026-05-25-capture-preview-mode-track-repair.md) |
| 3 | Document mode disappeared. | Verified as code bug: model may emit four product entries, but `CockpitSurfaceRenderer.renderModeTrack()` binds only three buttons (`photo`, `video`, `document`), so the fourth entry is dropped. | [Capture, Preview, And Mode Track Repair](./2026-05-25-capture-preview-mode-track-repair.md) |

## Recommended Execution

1. Assign implementation to one Android UI agent because all three findings converge in `app/src/main/res/layout/activity_main.xml`, `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`, and app render-model tests.
2. Run the package verification commands after implementation.
3. Use [Real Device UI Regression Acceptance QA](./2026-05-25-real-device-ui-regression-acceptance-qa.md) for Codex/user final acceptance on device.

## Codex-Retained Work

- Final judgment that the shutter no longer reads purple on the target device.
- Final judgment that shutter feedback remains visible during save without inviting unsafe duplicate captures.
- Device screenshot/recording review for preview vertical placement and mode track visibility.

## Delegable Work

- Resource/style fixes for the shutter surface and disabled/pressed states.
- Layout constraint adjustment for preview/mode-track/bottom-sheet geometry.
- Renderer mapping fix so visible mode entries bind to their actual mode buttons, including `DOCUMENT` after `HUMANISTIC`.
- Focused JVM/unit tests and app build.

## Notes

- `rtk git status --short` failed in this workspace with a stale worktree metadata error. Do not rely on git status as the only source of truth for user changes.
- Stage 7 observability remains the broader project gate; this feedback is UI regression work and should stay scoped to the current files.
