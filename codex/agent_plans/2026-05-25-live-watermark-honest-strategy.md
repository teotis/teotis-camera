# Live Watermark Honest Strategy

## Goal

Make Live watermark and border behavior explicit and honest: the still frame may receive the normal saved-photo watermark, but the motion segment must be labeled as `still-only`, `metadata-only`, or `burned-in` according to what the pipeline actually produced.

## Context

- User request: add the approved Live watermark/border honest version to Live Photo 2.0.
- Verified facts:
  - Still-photo watermark productization is tracked by `2026-05-25-watermark-2-product-upgrade-index.md`.
  - `LiveMediaBundle.watermarkMotionBehavior` and `LiveWatermarkMotionBehavior` already exist, but current behavior is not yet a full persisted Live product contract.
  - `LiveMediaBundle.liveWatermarkMetadataTags(...)` can generate metadata tags, but agents must verify where those tags enter mode metadata and saved Live sidecar/container diagnostics.
  - `PhotoWatermarkPostProcessor` renders saved JPEG watermark pixels; it does not currently burn watermarks into the MP4 motion segment.
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
- Non-goals:
  - Do not redesign still-photo watermark templates.
  - Do not claim motion watermark burn-in unless MP4 frames visibly contain the watermark.
  - Do not add a video-frame watermark renderer in this first package unless it is isolated and fully verified.

## Implementation Scope

- Add a canonical Live watermark result model, for example:
  - `STILL_ONLY`: primary still has watermark, motion has no visible watermark.
  - `MOTION_METADATA_ONLY`: sidecar/container records intended watermark behavior, motion pixels are unchanged.
  - `MOTION_BURNED_IN`: motion segment pixels contain the watermark.
  - `UNSUPPORTED`: selected output cannot carry the requested watermark behavior.
- Wire Live capture diagnostics to include both requested and actual behavior:
  - `live-watermark:requested=<behavior>`
  - `live-watermark:actual=still-only|metadata-only|burned-in|unsupported`
  - `live-watermark:reason=<short reason>` when degraded.
- Ensure the still-photo watermark path remains owned by `PhotoWatermarkPostProcessor`.
- Ensure motion watermark behavior is decided in the media/device Live assembly path, not in UI or coordinator code.

## Steps

1. Inspect existing Live metadata flow from `PhotoModePlugin`/settings into `ShotPlan` and `CameraXCaptureAdapter.captureLivePhoto()`.
2. Decide the smallest contract location for actual Live watermark outcome, preferably in `core:media` next to `LivePhotoBundle` or in settings if it is only a user preference.
3. Extend tests so a Live capture with still watermark but no motion burn-in records `actual=still-only` or `metadata-only`, never `burned-in`.
4. Add sidecar payload fields for requested and actual watermark behavior.
5. Add UI/render-model support only as compact status text; avoid a new heavy Live editor.
6. If `MOTION_BURNED_IN` is not implemented, keep it absent or unsupported in settings rather than exposing a dead option.

## Acceptance Criteria

- Live sidecar JSON includes requested and actual watermark behavior when Live is enabled.
- Diagnostics distinguish still-watermarked Live from motion-watermarked Live.
- Still-only fallback does not store `latestLivePhotoBundle` as usable temporal media unless it remains consistent with existing `isTemporalMedia()` rules.
- Existing still-photo watermark output remains unchanged.
- Tests prove no code path claims `MOTION_BURNED_IN` without a motion burn-in implementation.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
rtk ./scripts/verify_stage_6b7_live_photo.sh
```

## Risks And Notes

- Live motion is preview-derived low-resolution MP4. Burning complex Watermark 2.0 templates into every frame may be expensive and visually uneven.
- Metadata-only watermark behavior is acceptable only if the UI and diagnostics say it is metadata-only.
- Final visual QA belongs to Codex/user because non-multimodal agents cannot judge saved JPEG and motion playback appearance.
