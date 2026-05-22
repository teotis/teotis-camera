# 2026-05-23 Thumbnail Gallery And Tap Focus/EV Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are written for text-only agents and do not require screenshot or video analysis.

## Goal

Close two current real-device defects:

1. Tapping the latest thumbnail still fails to open the gallery and shows `无法打开媒体文件`.
2. Tapping the preview should trigger focus metering and automatic exposure adaptation at the tapped area.

## Current Code Facts

- Thumbnail rendering already prefers `pendingCaptureFeedback` and then `state.presentation.latestThumbnailSource?.renderUriOrNull()` in `MainActivity.render()`.
- Thumbnail click handling still uses `presentation.latestCapturePath ?: latestVideoPath`, checks `File.exists()`, and wraps the path through `FileProvider`.
- On Android Q+ saved media often has a MediaStore display path such as `Pictures/OpenCamera/...jpg`, plus a usable `content://...` URI in `ThumbnailSource.SavedMedia.renderUri`. `File("Pictures/OpenCamera/...").exists()` is false, so the click path shows `gallery_open_failed`.
- `GesturePolicy` already maps `GestureEvent.Tap` to `GestureAction.FocusAt(x, y)`.
- `MainActivity.bindGestureRouter()` receives `GestureAction.FocusAt` but leaves the integration empty.
- `core:session` and `core:device` already use `SessionIntent -> SessionEffect -> DeviceCommand` for zoom. Tap focus/AE should follow the same ownership boundary.

## Work Packages

1. [Thumbnail Gallery Open Fix](./2026-05-23-thumbnail-gallery-open-fix.md)
   - Independent and highest priority.
   - Fixes the broken thumbnail click path by opening only official saved media through a stable `content://` URI or FileProvider fallback.

2. [Tap Focus Session/Device Contract](./2026-05-23-tap-focus-session-device-contract.md)
   - Foundation for preview tap focus and AE metering.
   - Adds normalized metering requests, session state, effects, device commands, and tests.

3. [CameraX Focus And AE Execution](./2026-05-23-tap-focus-camerax-execution.md)
   - Depends on package 2 contract names.
   - Implements CameraX `FocusMeteringAction` using AF + AE metering regions and emits success/degraded/unsupported results.

4. [Tap Focus UI Reticle And Routing](./2026-05-23-tap-focus-ui-reticle.md)
   - Depends on package 2 contract names.
   - Replaces the existing `FocusAt` TODO, dispatches normalized preview taps, and draws a small focus reticle from session state.

5. [Integration And Verification](./2026-05-23-focus-ev-integration-verification.md)
   - Runs after packages 1-4.
   - Defines focused tests, stage verification, and non-multimodal real-device smoke checks.

## Recommended Parallelization

- Agent A can implement package 1 immediately.
- Agent B should implement package 2 first.
- Agent C can prepare package 3 after Agent B publishes the final `DeviceCommand` / `DeviceEvent` names.
- Agent D can prepare package 4 after Agent B publishes the final `SessionIntent` / presentation state names.
- Agent E should own package 5 integration after A-D land.

## Conflict Warnings

- Packages 1 and 4 both touch `MainActivity.kt`. Merge them through one integrator or keep edits in separate helper functions.
- Packages 2 and 3 both touch `DeviceContracts.kt`. Package 2 owns the contract shape; package 3 should not rename it.
- Do not open `pendingCaptureFeedback` or `PreviewSnapshot` as gallery media.
- Do not call CameraX directly from UI. UI dispatches session intents; session emits effects; coordinator forwards device commands.
- Do not persist tap focus or auto EV into settings. This is session-ephemeral runtime behavior.

## Global Acceptance

- Tapping a thumbnail after a saved photo/video opens Android media viewer instead of showing `无法打开媒体文件`.
- Tapping during save, when only transient preview feedback exists, does not attempt to open the feedback file as gallery media.
- Tapping preview while preview is active dispatches a session-owned metering request.
- CameraX applies AF + AE metering at the tapped point where supported; if full support is unavailable, the app reports a degraded or unsupported result without crashing.
- Existing Stage 7 verification remains green.

