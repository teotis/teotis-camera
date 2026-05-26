# 2026-05-25 Live Photo 2.0 Product Upgrade Index

> For agentic workers: use `rtk` for every shell command. This package is a follow-up to the existing Live Motion Photo and Watermark 2.0 plans. Keep implementation inside Mode Plugin, Session Kernel, Device Adapter, Media Pipeline, Settings, and UI render-model boundaries.

## Package Goal

Upgrade Live Photo from a locally assembled temporal-media capability into a product-grade feature with honest Live watermark behavior, compatibility diagnostics, and user-selectable save/export format.

## User Request

The user approved the following Live Photo 2.0 scope:

- Live watermark and border honest version.
- Product-level compatibility verification and diagnostics.
- Share/export support, including a setting that lets the user choose the Live save format.

## Verified Current Facts

- `2026-05-24-live-motion-photo-index.md` and related package docs already define and partly implement preview-ring-buffer motion, MP4 motion segment encoding, Google Motion Photo JPEG materialization, sidecar metadata, and honest fallback states.
- `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt` already has `LivePhotoCaptureSpec`, `LivePhotoBundle`, `LiveMotionSource`, `LiveBundleStatus`, `LiveTemporalWindow`, `isTemporalMedia()`, and `temporalNotes()`.
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` already records `motion-photo:container=google-jpeg` only after successful materialization and records failure notes otherwise.
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt` already has `LiveMediaBundle` with `motionDurationMillis`, `motionContainer`, `sidecarMimeType`, and `watermarkMotionBehavior`.
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt` already has `LiveMediaBundle.liveWatermarkMetadataTags(...)`, but those tags are not yet a full persisted product setting or verified save-format contract.
- `2026-05-25-watermark-2-product-upgrade-index.md` already covers still-photo Watermark 2.0 templates and OCWM reversible archive behavior. This Live package must not duplicate still-watermark productization.

## Package Documents

| Package | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Live Watermark Honest Strategy](./2026-05-25-live-watermark-honest-strategy.md) | Text/code agent + Codex visual QA | planned | Make Live still/motion watermark behavior explicit, diagnosable, and impossible to overclaim. |
| [Live Compatibility Diagnostics](./2026-05-25-live-compatibility-diagnostics.md) | Text/code agent + Codex/user real-device QA | planned | Add product-level diagnostics and acceptance evidence for Motion Photo file/container compatibility. |
| [Live Export Format Settings](./2026-05-25-live-export-format-settings.md) | Text/code agent + Codex/user acceptance | planned | Add a persisted setting for Live save format and deterministic export/share choices. |

## Related Recent Plans

- `2026-05-24-live-motion-photo-index.md`: base Motion Photo implementation package. This package depends on it and extends product behavior after the core local code path exists.
- `2026-05-24-live-motion-photo-google-container.md`: source for Google Motion Photo writer constraints and XMP/appended MP4 verification.
- `2026-05-24-live-motion-photo-session-integration.md`: source for session/device/media ownership and fallback state mapping.
- `2026-05-25-watermark-2-product-upgrade-index.md`: source for still-photo watermark templates. Live watermark work must reuse its metadata and renderer boundaries.
- `codex/documentation.md`: current status says Live/Google Motion Photo has local tests, but product pass still requires real-device compatibility smoke.

## Recommended Execution Order

1. Execute [Live Export Format Settings](./2026-05-25-live-export-format-settings.md) first, because watermark and compatibility diagnostics need one canonical setting for intended Live output.
2. Execute [Live Watermark Honest Strategy](./2026-05-25-live-watermark-honest-strategy.md) second, because the selected output format determines whether motion watermark can be burned in, metadata-only, or still-only.
3. Execute [Live Compatibility Diagnostics](./2026-05-25-live-compatibility-diagnostics.md) third, because it validates the final product behavior and captures remaining real-device gaps.
4. Codex/user keeps final real-device QA: capture a Live photo, inspect diagnostics, open in target gallery apps, and verify export/share choices.

## Global Acceptance

- Settings can express the selected Live save format without using raw strings throughout the codebase.
- Live capture diagnostics always include intended format, actual artifact status, watermark behavior, and compatibility notes.
- The app never claims dynamic watermark rendering unless the motion segment itself contains the visible watermark or a supported player-side metadata contract exists.
- Google Motion Photo remains the preferred default when available.
- MP4 export and still JPEG export are available as explicit share/export targets, not as silent fallback surprises.
- Still-only fallback remains a successful still photo with visible Live degradation notes.
- Existing Live, watermark, and Stage 7 verification commands remain passable after implementation.

## Codex-Retained Work

- Real-device gallery compatibility smoke.
- Visual comparison of saved still watermark versus motion watermark/metadata behavior.
- Final product judgment on whether a gallery recognizes Google Motion Photo as dynamic media.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.MotionPhotoJpegContainerTest --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./scripts/verify_stage_6b7_live_photo.sh
rtk ./scripts/verify_stage_7_observability.sh
```

## Non-Goals

- Do not implement audio Live Photo.
- Do not implement HEIC/AVIF Motion Photo in this package.
- Do not promise Apple/vivo/OPPO visual or ecosystem parity.
- Do not burn Live watermarks into video frames unless a bounded, tested encoder step proves it can do so.
- Do not move Live orchestration into `CameraSessionCoordinator`.
