# Humanistic Mode Product Reopen Plan

日期：2026-05-25

## Goal

Make Humanistic visible and meaningfully distinct from Photo. The first-screen mode should feel like a street-life quick capture mode: 35mm-ish view, fast shutter behavior, restrained humanistic styles, immediate feedback, and clear metadata.

## Context

- User request: 重新开放人文模式，需要独特于拍照模式的功能，倾向快速抓拍。
- Verified facts:
  - `HumanisticModePlugin.kt` already exists and emits `mode=humanistic`, style, watermark, Live default, frame ratio, and Pro variant metadata.
  - `SessionCockpitRenderModel.kt` hides Humanistic from the product mode track by omitting it from `PRODUCT_MODE_ENTRY_ORDER`.
  - `SettingsDefaults.kt` currently defines five Humanistic filter profiles, two of which duplicate Photo semantics too closely: `humanistic-original` and `humanistic-vivid`.
  - The old handoff plan proposed `PHOTO -> HUMANISTIC -> VIDEO -> DOCUMENT`, 35mm-ish 1.3x default, and three styles. This remains useful, but should be updated to quick-snap-first.
- Relevant files:
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDefaults.kt`
  - `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeCatalogContracts.kt`
- Non-goals:
  - Do not add a new `ModeId`.
  - Do not reopen all hidden modes just because Humanistic is being reopened.
  - Do not make Humanistic a second Photo mode with only a label change.
  - Do not add a large new UI panel in the first loop.

## Product Shape

Humanistic should differ from Photo in four visible ways:

- **View**: default 35mm-ish zoom, normalized through `ZoomRatioCapability`; fallback to 1x if 1.3x is unsupported.
- **Capture**: quick-snap priority with ZSL/min-latency fallback and immediate preview feedback.
- **Style**: three built-in profiles only:
  - `humanistic-street` -> `Street` / `街头`
  - `humanistic-portrait` -> `Portrait` / `肖像`
  - `humanistic-life` -> `Life` / `生活`
- **Copy/metadata**: mode details, hints, saved metadata, and diagnostics should say quick snap / humanistic, not generic photo.

## Implementation Scope

- Reopen Humanistic in the primary mode track.
- Add default Humanistic zoom through the existing `DeviceGraphSpec.stillCapture` `zoomRatio` path.
- Request quick capture in `HumanisticModePlugin.submitCurrentStyle()`.
- Simplify built-in Humanistic styles to three profiles and update catalog labels.
- Update render-model tests that currently assert Humanistic remains hidden.

## Steps

1. Reopen mode track:
   - In `SessionCockpitRenderModel.kt`, change `PRODUCT_MODE_ENTRY_ORDER` to:

```kotlin
private val PRODUCT_MODE_ENTRY_ORDER = listOf(
    ModeId.PHOTO,
    ModeId.HUMANISTIC,
    ModeId.VIDEO,
    ModeId.DOCUMENT
)
```

   - Remove or update tests named like `hides humanistic entry`.
   - Verify `MainActivityActionBinder.kt` does not force `views.modeTrack.humanistic.visibility = View.GONE`; if it does, replace with render-model-driven visibility for Humanistic.

2. Add 35mm-ish default zoom:
   - Extend `stillCaptureDeviceGraph(runtimeState, zoomRatio = 1f)` if needed.
   - In `HumanisticModePlugin.currentDeviceGraph()`, request `zoomRatio = 1.3f`.
   - Route the ratio through `resolvedZoomRatioSelection` or equivalent capability normalization so unsupported devices degrade to an available ratio.
   - Add metadata/diagnostic tag `humanisticDefaultFocal=35mm-ish` and `humanisticDefaultZoom=1.3`.

3. Avoid zoom leaking across modes:
   - In `DefaultCameraSession.handleSwitchMode`, ensure a newly entered mode's `deviceGraph()` can establish its own baseline zoom.
   - Preserve user zoom changes inside the active mode.
   - Add tests for Humanistic -> Photo returning to Photo default zoom when supported.

4. Add quick capture request:
   - In `HumanisticModePlugin.submitCurrentStyle()`, set the quick-snap latency contract from the contracts package.
   - Include metadata:
     - `mode=humanistic`
     - `captureIntent=quick-snap`
     - `latencyPriority=zsl-when-supported`
     - `style=<style id>`
   - Keep Live Photo as a setting-driven option, but if Live prevents ZSL, diagnostics must say degraded rather than hiding the tradeoff.

5. Simplify styles:
   - Remove `humanistic-original` and `humanistic-vivid` from built-in Humanistic filter profiles.
   - Keep old persisted IDs readable by falling back to `humanistic-street`.
   - Update labels to `街头 Street`, `肖像 Portrait`, `生活 Life` or use existing localization conventions if the project has split string resources for these labels.
   - Update `mappedAlgorithmProfile` to only special-case the three kept profiles.

6. Update mode catalog:
   - Change Humanistic declared subfeatures to something like:
     `35mm quick snap, Street/Portrait/Life styles, Pro variant, frame ratio, Live default, timer`
   - Make sure Photo still advertises flash/frame/Live/watermark rather than quick snap.

7. Run verification.

## Acceptance Criteria

- Mode track order is `Photo -> Humanistic -> Video -> Doc` when all are available.
- Humanistic is selectable through the same session intent path as other modes.
- Entering Humanistic requests a 1.3x normalized zoom when supported and degrades predictably otherwise.
- Humanistic capture uses the quick-snap latency contract and records capture intent metadata.
- Humanistic shows only Street, Portrait, and Life built-in styles.
- Photo mode behavior and default styles do not change.
- Tests no longer encode the old hidden-Humanistic product decision.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Then run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- If the current XML/view layer still has a hidden Humanistic button, render-model tests alone are not enough; inspect `MainActivityActionBinder.kt` and `MainActivityViews.kt`.
- 1.3x is a practical approximation of 35mm on a 24-26mm main camera. It should be described as 35mm-ish, not an exact optical focal length.
- If device zoom capability only exposes `1x`, Humanistic must remain available and simply degrade the focal preference.
- Do not force Live Photo on by default for quick snap; Live can increase pipeline work and make latency harder to reason about.
