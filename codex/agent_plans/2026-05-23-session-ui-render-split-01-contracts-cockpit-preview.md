# Session UI Render Split 01: Contracts, Cockpit, And Preview

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is text-only and does not require screenshots.

**Goal:** Extract shared render contracts plus cockpit and preview render-model builders out of `SessionUiRenderModel.kt` without changing behavior.

**Architecture:** Keep the same `com.opencamera.app` package and `internal` top-level APIs. Move declarations mechanically first, then split tests by copied assertions. The old monolithic test remains as a safety net until later packages finish.

**Tech Stack:** Kotlin app module, JVM unit tests, Android Views are not touched, `rtk ./gradlew`.

---

## Current Code Facts

- `SessionUiRenderModel.kt` lines 59-145 define shared/cockpit/preview data classes.
- Cockpit and preview functions are currently mixed with settings/filter/dev helpers:
  - `captureDisabledReason`
  - `sessionControlsRenderModel`
  - `focusReticleRenderModel`
  - `previewOverlayRenderModel`
  - `frameRatioControlRenderModel`
  - `quickPanelSheetRenderModel`
  - `sessionSummaryText`
  - `modeSummaryText`
  - `modeDirectoryRenderModel`
  - `modeTrackRenderModel`
  - `primaryStatusRenderModel`
  - `modeDirectoryText`
  - `sessionCaptureOutputText`
- Existing consumers include `MainActivity.kt`, `CockpitSurfaceRenderer.kt`, `PreviewOverlayView.kt`, and `SessionUiRenderModelTest.kt`.

## Files

Create:

- `app/src/main/java/com/opencamera/app/SessionUiRenderContracts.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionPreviewRenderModelTest.kt`

Modify:

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

Do not modify:

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/res/layout/activity_main.xml`

## Step 1: Baseline

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected: `BUILD SUCCESSFUL`.

If this fails before edits, stop and report the pre-existing failure.

## Step 2: Create The Small Shared Contract File

Move only types reused across at least two domains into `SessionUiRenderContracts.kt`:

```kotlin
package com.opencamera.app

import com.opencamera.core.settings.FeatureCatalogAction
import com.opencamera.core.settings.PersistedSettingsAction

internal data class SessionUiStrings(
    val buttonSwitchToFront: String,
    val buttonSwitchToBack: String,
    val buttonSingleLens: String,
    val buttonZoomPrefix: String,
    val buttonZoomUnavailable: String,
    val buttonStillFast: String,
    val buttonStillMax: String,
    val buttonStillQualityUnavailable: String,
    val buttonStill12Mp: String,
    val buttonStill8Mp: String,
    val buttonStill2Mp: String,
    val buttonStillResolutionUnavailable: String,
    val outputErrorPrefix: String,
    val outputVideoPrefix: String,
    val outputLivePrefix: String,
    val outputSavedPrefix: String,
    val outputPreviewPrefix: String,
    val outputWaiting: String
)

internal enum class SettingsControlAvailability {
    SUPPORTED,
    DEGRADED,
    UNSUPPORTED
}

internal data class SettingsControlRenderModel(
    val label: String,
    val value: String,
    val availability: SettingsControlAvailability = SettingsControlAvailability.SUPPORTED,
    val availabilityLabel: String = "",
    val supportLabel: String? = null,
    val nextAction: PersistedSettingsAction? = null,
    val enabled: Boolean = true,
    val disabledReason: String? = null
) {
    val isInteractive: Boolean
        get() = enabled && availability != SettingsControlAvailability.UNSUPPORTED && nextAction != null

    val buttonLabel: String
        get() = buildString {
            append(label)
            append('\n')
            append(value)
            append('\n')
            append(availabilityLabel.ifEmpty { availability.name.lowercase().replaceFirstChar(Char::titlecase) })
            supportLabel?.let {
                append(" • ")
                append(it)
            }
        }
}

internal data class FeatureCatalogControlRenderModel(
    val label: String,
    val value: String,
    val availability: SettingsControlAvailability = SettingsControlAvailability.SUPPORTED,
    val availabilityLabel: String = "",
    val supportLabel: String? = null,
    val nextAction: FeatureCatalogAction? = null
) {
    val isInteractive: Boolean
        get() = nextAction != null

    val buttonLabel: String
        get() = buildString {
            append(label)
            append('\n')
            append(value)
            append('\n')
            append(availabilityLabel.ifEmpty { availability.name.lowercase().replaceFirstChar(Char::titlecase) })
            supportLabel?.let {
                append(" • ")
                append(it)
            }
        }
}
```

Remove the moved declarations from `SessionUiRenderModel.kt`. Keep imports minimal in the new file.

## Step 3: Create `SessionPreviewRenderModel.kt`

Move preview-only declarations and functions:

- `PreviewOverlayRenderModel`
- `PreviewFrameRenderModel`
- `focusReticleRenderModel`
- `previewOverlayRenderModel`

The new file imports only what these declarations need:

```kotlin
package com.opencamera.app

import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.effect.PreviewEffectRenderModel
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.session.PreviewMeteringFeedback
import com.opencamera.core.session.PreviewMeteringFeedbackStatus
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.CompositionGridMode
```

Do not rename functions. `MainActivity` and `PreviewOverlayView` should continue compiling without call-site changes.

## Step 4: Create `SessionCockpitRenderModel.kt`

Move cockpit and mode/quick-panel declarations and functions:

- `ZoomCapsuleRenderModel`
- `SessionControlsRenderModel`
- `FrameRatioOptionRenderModel`
- `FrameRatioControlRenderModel`
- `QuickPanelRowRenderModel`
- `QuickPanelSheetRenderModel`
- `ModeDirectoryItemRenderModel`
- `ModeDirectoryRenderModel`
- `ModeTrackItemRenderModel`
- `ModeTrackRenderModel`
- `PrimaryStatusRenderModel`
- `captureDisabledReason`
- `sessionControlsRenderModel`
- `frameRatioControlRenderModel`
- `quickPanelSheetRenderModel`
- `sessionSummaryText`
- `modeSummaryText`
- `modeDirectoryRenderModel`
- `modeTrackRenderModel`
- `primaryStatusRenderModel`
- `modeDirectoryText`
- `sessionCaptureOutputText`
- helper vals/functions used only by the moved declarations, including zoom labels, frame ratio support, live asset lines, mode entry ordering, and pipeline notes.

Keep helper visibility `private` when the helper is used only in this new file. Keep top-level APIs `internal`.

## Step 5: Add Focused Tests

Create `SessionPreviewRenderModelTest.kt` by moving these existing tests from `SessionUiRenderModelTest.kt`:

- `preview overlay render model exposes active grid and countdown`
- `preview overlay render model hides aids when preview is unavailable`

Create `SessionCockpitRenderModelTest.kt` by moving these existing tests:

- `controls model reflects active lens and still settings`
- `controls model degrades labels for video template`
- `capture output prefers explicit error over saved media`
- `capture output appends pipeline notes for saved video`
- `capture output renders live photo bundle paths`
- `capture output renders live photo media store uris when available`
- `preview thumbnail output stays compact when only preview is available`
- `mode summary reflects current mode snapshot`
- `mode directory render model hides humanistic entry and uses product order`
- `mode directory render model degrades scenery and portrait features with capability fallback`
- `zoom capsule render model carries individual ratio and active state`
- `zoom capsule labels use compact format`
- `zoom capsule compact label handles 1x and integer ratios`
- `mode track render model hides humanistic entry and uses product order`
- `mode track labels are short and stable`
- `active mode track item has distinct visual state`
- `quick button labels fit within 96dp button width without ellipsis`
- `quick panel sheet exposes all five rows`
- `quick panel sheet frame ratio options remain present when one is selected`
- `quick panel sheet frame ratio disabled for video mode`
- `quick panel sheet frame ratio disabled during active shot`
- `primary status shows recording starting`
- `primary status shows recording active`
- `primary status shows saving`
- `primary status shows capture status when not idle`
- `frame ratio control disabled for unsupported mode`
- `frame ratio control disabled during active shot`
- `capture disabled reason returns null when idle`
- `capture disabled reason returns saving photo`
- `capture disabled reason returns recording`
- `capture disabled reason returns permission required`

Leave copied tests in `SessionUiRenderModelTest.kt` until package 3, unless the move is clean and the old test still has enough coverage. Avoid changing test expectations.

## Step 6: Verify

Run focused tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionPreviewRenderModelTest
```

Run legacy safety net:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Run app compile:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Expected: all commands end with `BUILD SUCCESSFUL`.

## Acceptance

- `SessionUiRenderModel.kt` no longer contains cockpit or preview implementations.
- `SessionUiRenderContracts.kt` remains limited to shared contracts, not a new giant file.
- No user-visible behavior changes.
- Existing `MainActivity`, cockpit renderer, and preview overlay call sites compile without API renames.
