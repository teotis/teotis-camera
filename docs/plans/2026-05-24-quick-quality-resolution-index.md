# 2026-05-24 Quick Quality And Resolution Index

> For text-only agents: this is the master handoff for adding `快捷` quality / resolution switching in photo and video modes. Pick one linked plan, keep edits scoped, and run every shell command through `rtk`.

## Source Request

Add quick-panel switching for:

1. Photo mode quality / resolution.
2. Video mode quality / resolution, where a video quality option binds resolution and fps together, for example `1080p60`, `1080p30`, `4K30`.

The product treatment may reference Apple and vivo, but implementation must obey the OpenCamera architecture: UI renders state and dispatches intents/actions only; Session Kernel, Mode Plugin, Device Adapter, and Media Pipeline keep their existing ownership.

## Reference Treatment

- Apple Camera exposes video resolution and frame-rate controls directly in the camera UI, while keeping broader defaults in Settings: https://support.apple.com/guide/iphone/change-video-recording-settings-iphc1827d32f/ios
- vivo support material frames video recording quality as a user-facing resolution/fps choice in the Camera path: https://www.vivo.com.cn/service/questions/all?categoryId=192&questionId=2072
- vivo X 系列 product positioning advertises high video combinations such as all-focal 4K 120fps on X300 Ultra, which reinforces that OpenCamera must capability-gate combinations instead of assuming every option exists: https://www.vivo.com.cn/vivo/x300ultra

## Current-Code Evidence

- `app/src/main/res/layout/activity_main.xml` has a `quickBubblePanel` with rows for grid, quality, frame ratio, live photo, and timer. There is no photo resolution row and no video-specific combined spec row.
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` maps `buttonQuickFlash` to `SessionIntent.StillCaptureQualityToggled` for every mode. In video mode this means the existing quick quality button does the wrong thing.
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` hard-codes `qualityRow.value = strings.buttonStillFast`; it does not reflect `state.activeDeviceGraph.stillCapture.qualityPreference`, `outputSize`, or `recording.videoSpec`.
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` already owns still-quality and still-resolution runtime toggles and correctly blocks countdown / active-shot cases.
- `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt` has `cycleQuality()`, but it cycles only `VideoResolution`; fps stays whatever was previously requested. This violates the requested `1080p60` / `4K30` combined option model.
- `core/settings/src/main/kotlin/com/opencamera/core/settings/VideoSpecConstraints` already has the right capability matrix shape: `resolution -> supported fps set`.
- `core/device/src/main/kotlin/com/opencamera/core/device/VideoSpecSelection.kt` already resolves requested `VideoSpec` into supported or degraded applied specs. Reuse it; do not invent adapter-side fallback logic in UI.
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` settings page still exposes resolution and fps separately for defaults. This can remain; the new quick control is the active shooting shortcut.

## Architecture Decision

Use mode-aware quick-panel rows backed by existing session/mode/device contracts.

Photo:

- Keep quality and resolution as two separate quick controls because photo quality (`Fast` / `Max`) and output size (`12MP` / native size) are different product dimensions.
- Reuse `SessionIntent.StillCaptureQualityToggled` and `SessionIntent.StillCaptureResolutionToggled`.
- Render current values from `SessionState.activeDeviceGraph`, not hard-coded labels.

Video:

- Replace the quick quality behavior in video mode with a combined `VideoSpec(resolution, frameRate)` option.
- Reuse `VideoModePlugin.cycleQuality()` as the runtime owner, but change it to cycle supported `(resolution, fps)` combinations, not resolution alone.
- Do not add a second quick fps button. In `快捷`, fps is part of the quality choice.
- Do not make UI call CameraX or construct CameraX `QualitySelector`.

Rejected alternatives:

- Only adding settings-page controls. The user explicitly asked for `快捷`.
- Adding separate video `分辨率` and `帧率` quick rows. That recreates the split the user asked to avoid.
- Persisting every quick change through settings as the first pass. Current quick still quality is runtime session state; matching that pattern keeps this scoped. A later product pass may choose persistence.
- Letting `MainActivity` mutate `VideoModeController` or `CameraXCaptureAdapter` directly. That would create hidden runtime ownership outside the Session Kernel / Mode boundary.

## Work Packages

1. [Photo Quick Quality And Resolution](./2026-05-24-photo-quick-quality-resolution.md)
   - Fix hard-coded still quality label.
   - Add photo resolution / pixel quick row.
   - Wire row clicks to existing session intents.

2. [Video Quick Combined Spec Presets](./2026-05-24-video-quick-spec-preset.md)
   - Add a deterministic capability-filtered quick video spec option helper.
   - Change video `cycleQuality()` to cycle combined specs.
   - Render quick quality as `4K30`, `1080p60`, etc. in video mode.

3. [Verification And Anti-Regression Gate](./2026-05-24-quick-quality-resolution-verification.md)
   - Adds focused render-model, session, video-spec, and adapter checks.
   - Runs Stage 7 verification after either package lands.

## Parallelization

- Agent A can implement the photo quick row package.
- Agent B can implement the video combined spec helper and mode-plugin package.
- Agent C can implement the verification script after Agent A and Agent B settle names.

Avoid parallel edits to:

- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

## Global Acceptance

- In photo mode, quick quality shows the active still quality and toggles `Fast` / `Max`.
- In photo mode, quick resolution / pixel row shows the active output size or preset and toggles available still resolutions.
- During active photo capture or countdown, photo quick quality / resolution controls are disabled or blocked through existing session behavior.
- In video mode, quick quality shows one combined option such as `4K30`, `4K60`, `1080p60`, or `1080p30`.
- Video quick quality changes both resolution and fps together before recording starts.
- During recording, video quick quality is blocked and the UI does not imply the recording graph changed.
- Unsupported video combinations are not offered as quick choices. If the current requested spec degrades, UI displays the applied fallback explicitly.
- CameraX adapter still receives a resolved `VideoSpec` through `DeviceGraphSpec.recording.videoSpec`.
- UI does not call CameraX APIs directly.
- Stage 7 verification still passes:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Multimodal Boundary

This package does not need new bitmap assets or screenshot interpretation. Text-only agents can implement contracts, render models, layout rows, and tests. Exact visual polish against Apple/vivo screenshots and real-device readability should be handled as a later multimodal QA pass after code lands.
