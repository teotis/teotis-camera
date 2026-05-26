# Photo Quick Quality And Resolution Plan

> For text-only agents: this task adds photo-mode `快捷` quality and resolution controls. UI renders state and dispatches session intents only. Use `rtk` for every command.

## Goal

Make photo-mode quick controls truthful and complete:

- `画质` / `Quality` shows and toggles active still capture quality.
- `像素` / `Size` shows and toggles active still output resolution.

This is a runtime quick control, not a new persisted settings page.

## Required Semantics

- Use existing `SessionIntent.StillCaptureQualityToggled`.
- Use existing `SessionIntent.StillCaptureResolutionToggled`.
- Do not add CameraX calls in UI.
- Do not add a second session kernel in `MainActivity`, renderers, or coordinators.
- Disable or allow session-side blocking when countdown or active photo capture is in progress.
- Hide or disable photo resolution in modes where `activeDeviceGraph.template != CaptureTemplate.STILL_CAPTURE`.
- If native output sizes are available, show a compact megapixel value derived from the active output size.
- If no native size list is available, show the active `StillCaptureResolutionPreset.label`.
- Keep Chinese quick row titles at 2 characters where possible. Use `像素` for the new row instead of `分辨率`.

## Current Gap

- `QuickPanelSheetRenderModel.qualityRow.value` is always `strings.buttonStillFast`, so the quick UI lies when active quality is `QUALITY`.
- The session already has still resolution toggling, but the quick sheet has no row for it.
- `buttonQuickFlash` is named after an old flash row but is now used as quality. Keep the id for this pass unless the implementing agent wants a narrow rename with tests.

## Files To Inspect Or Modify

- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Render Model Shape

Extend `QuickPanelSheetRenderModel`:

```kotlin
internal data class QuickPanelSheetRenderModel(
    val gridRow: QuickPanelRowRenderModel,
    val qualityRow: QuickPanelRowRenderModel,
    val resolutionRow: QuickPanelRowRenderModel,
    val frameRatioRow: QuickPanelRowRenderModel,
    val frameRatioOptions: List<FrameRatioOptionRenderModel>,
    val frameRatioEnabled: Boolean,
    val frameRatioDisabledReason: String?,
    val liveRow: QuickPanelRowRenderModel,
    val timerRow: QuickPanelRowRenderModel
)
```

Add small helpers in `SessionCockpitRenderModel.kt`:

```kotlin
private fun stillQualityQuickLabel(state: SessionState, strings: SessionUiStrings): String {
    return when (state.activeDeviceGraph.stillCapture.qualityPreference) {
        StillCaptureQualityPreference.LATENCY -> strings.buttonStillFast
        StillCaptureQualityPreference.QUALITY -> strings.buttonStillMax
    }
}

private fun stillResolutionQuickLabel(state: SessionState): String {
    val native = selectedNativeStillCaptureOutputSizeOrNull(state)
    return native?.quickMegapixelLabel()
        ?: state.activeDeviceGraph.stillCapture.resolutionPreset.label
}

private fun StillCaptureOutputSize.quickMegapixelLabel(): String {
    val megapixels = (pixelCount / 1_000_000.0).roundToInt()
    return "${megapixels}MP"
}
```

Import `kotlin.math.roundToInt` and existing media/device types as needed. If the project prefers Chinese values from resources, keep the helper resource-backed for preset labels, but native sizes should still be compact.

Set row availability:

```kotlin
val stillTemplate = state.activeDeviceGraph.template == CaptureTemplate.STILL_CAPTURE
val stillBusy = state.activeShot != null || state.countdownRemainingSeconds != null
val qualityEnabled = stillTemplate && !stillBusy && state.activeDeviceCapabilities.supportsStillCapture
val resolutionEnabled = qualityEnabled && isStillResolutionToggleEnabled(state)
```

For disabled reasons, reuse existing text where practical:

- Non-still mode: `Still quality is only available in photo modes` / `Still resolution is only available in photo modes`.
- Active shot: existing disabled active-shot text.
- Countdown: existing disabled countdown text.
- Single resolution: `No alternate still resolution available on this lens`.

## Layout And Binding

In `activity_main.xml`, add one row after `buttonQuickFlash`:

```xml
<Button
    android:id="@+id/buttonQuickResolution"
    style="@style/Widget.OpenCamera.QuickBubbleButton"
    android:layout_width="match_parent"
    android:layout_height="40dp"
    android:layout_marginTop="4dp" />
```

In `MainActivityViews.QuickPanelViews`, add:

```kotlin
val resolution: Button
```

Wire it in `MainActivityViews.from(activity)`:

```kotlin
resolution = activity.findViewById(R.id.buttonQuickResolution)
```

Render it in `CockpitSurfaceRenderer.renderQuickBubble()`:

```kotlin
quickPanel.resolution.text = "${sheet.resolutionRow.title} ${sheet.resolutionRow.value}"
quickPanel.resolution.isEnabled = sheet.resolutionRow.isEnabled
```

Bind it in `MainActivityActionBinder`:

```kotlin
views.quickPanel.resolution.setOnClickListener {
    callbacks.dispatch(SessionIntent.StillCaptureResolutionToggled)
}
```

Do not put CameraX logic in the click listener.

## Strings

Add to `app/src/main/res/values/strings.xml`:

```xml
<string name="button_quick_resolution">像素</string>
```

Add to `app/src/main/res/values-en/strings.xml`:

```xml
<string name="button_quick_resolution">Size</string>
```

Add to `AppTextResolver`:

```kotlin
open fun quickResolution(): String = str(R.string.button_quick_resolution, "Size")
```

Use `text.quickResolution()` for `resolutionRow.title`.

## Tests

Update `SessionCockpitRenderModelTest`:

```kotlin
@Test
fun `quick panel sheet exposes photo quality and resolution rows`() {
    val state = defaultSessionState(
        activeDeviceGraph = DeviceGraphSpec.stillCapture(
            qualityPreference = StillCaptureQualityPreference.QUALITY,
            resolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
        )
    )

    val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

    assertEquals("Quality", sheet.qualityRow.title)
    assertEquals("Still Max", sheet.qualityRow.value)
    assertEquals("Size", sheet.resolutionRow.title)
    assertEquals("8MP", sheet.resolutionRow.value)
    assertTrue(sheet.qualityRow.isEnabled)
    assertTrue(sheet.resolutionRow.isEnabled)
}
```

Add a native-size label test:

```kotlin
@Test
fun `quick resolution uses active native output size when available`() {
    val state = defaultSessionState(
        activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
            availableStillCaptureOutputSizes = listOf(
                StillCaptureOutputSize(width = 6000, height = 4000),
                StillCaptureOutputSize(width = 4000, height = 3000)
            )
        ),
        activeDeviceGraph = DeviceGraphSpec.stillCapture(
            outputSize = StillCaptureOutputSize(width = 6000, height = 4000)
        )
    )

    val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

    assertEquals("24MP", sheet.resolutionRow.value)
}
```

Update the existing quick-row count test from five rows to six rows.

Update quick label fit test:

```kotlin
val quickLabels = listOf("网格", "画质", "像素", "画幅", "实况", "定时")
```

Add or extend `DefaultCameraSessionTest` only if no current test covers still resolution toggle. Required cases:

- `StillCaptureResolutionToggled` changes active graph output or preset in still mode.
- It is blocked during active shot.
- It is unavailable in video template.

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Then run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Acceptance

- Photo quick `画质` reflects `Fast` / `Max` instead of always showing `Fast`.
- Photo quick `像素` cycles through available still output sizes or presets.
- Quick UI remains mode-aware and does not show enabled photo resolution control in video mode.
- Session trace and state remain the source of truth for quality / resolution changes.
