# Live Export Format Settings

## Goal

Add a user-visible, persisted Live save/export format setting so OpenCamera can choose between Google Motion Photo, MP4 sidecar/bundle, and still-only JPEG behavior without relying on hardcoded strings or silent fallback.

## Context

- User request: share/export support, including a setting where the user can choose the Live save format.
- Verified facts:
  - `LiveMediaBundle.motionContainer` is currently a raw string defaulting to `video/mp4`.
  - `LivePhotoCaptureSpec.motionMimeType` and `sidecarMimeType` are raw strings.
  - The app currently materializes Google Motion Photo when preview motion and container writing succeed.
  - Settings serialization currently persists `photo.livePhotoEnabledByDefault`, but not a selected Live save format.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
- Non-goals:
  - Do not build a full Android share sheet wrapper if the app currently lacks that architecture.
  - Do not add HEIC/AVIF as selectable formats in this pass.
  - Do not remove the honest still-only fallback path.

## Implementation Scope

- Add a typed enum, for example:
  - `GOOGLE_MOTION_PHOTO_JPEG`: default product format, one JPEG with appended MP4 and Motion Photo XMP.
  - `MOTION_MP4_SIDECAR`: primary JPEG plus MP4/sidecar bundle for debugging or gallery-incompatible devices.
  - `STILL_JPEG_ONLY`: save a still photo while retaining diagnostics that Live was disabled by format choice.
- Persist the selected format under a stable key such as `photo.live.saveFormat`.
- Add `PersistedSettingsAction.UpdateLiveSaveFormat`.
- Update settings render model so the photo settings page cycles or opens the selected format.
- Feed the selected format into `LivePhotoCaptureSpec` or the shot metadata path used by mode plugins.
- Make export/share diagnostics explicit:
  - `live-export:format=<format>`
  - `live-export:share-target=motion-photo|mp4|still`
  - `live-export:fallback=<reason>` when selected format cannot be produced.

## Steps

1. Define `LiveSaveFormat` in `SettingsEnums.kt` with storage keys and labels.
2. Add it to `LiveMediaBundle` or `PhotoSettings`; prefer `PhotoSettings` if it is a persisted user choice rather than only catalog capability.
3. Add reducer and serializer coverage.
4. Update settings render models/tests to show and update the selected format.
5. Update mode-to-capture-spec conversion in Photo, Portrait, and Humanistic mode plugins only if those modes already use shared Live settings; keep all CameraX/container logic out of plugins.
6. Update `CameraXCaptureAdapter.captureLivePhoto()` to branch by typed selected format:
   - Google Motion Photo attempts container materialization.
   - MP4 sidecar keeps still + MP4 + sidecar paths and does not claim Google container success.
   - Still JPEG only returns still success with Live disabled-by-format diagnostics.
7. Add tests for each format and fallback.

## Acceptance Criteria

- The selected Live save format survives settings serialization round-trip.
- Settings UI render model exposes the selected format and a valid next action.
- Google Motion Photo remains default.
- MP4 sidecar mode never emits `motion-photo:container=google-jpeg`.
- Still JPEG only mode does not create a usable `latestLivePhotoBundle`.
- Capture diagnostics include selected and actual export format.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./scripts/verify_stage_6b7_live_photo.sh
```

## Risks And Notes

- If the selected format lives only in `FeatureCatalog.liveMediaBundleDraft`, it may not persist as a user setting. Prefer a persisted `PhotoSettings` field for user choice.
- MP4 sidecar may be useful for debugging but should not be presented as gallery-native Live Photo.
- Do not let share/export code hide capture failure. A failed export after a successful still should remain visible as degraded export, not a missing photo.
