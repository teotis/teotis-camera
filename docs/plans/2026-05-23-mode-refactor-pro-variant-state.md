# Pro Variant State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Remove duplicated Pro variant and manual-draft helper logic from Portrait, Night, and Humanistic controllers.

**Architecture:** Add `ProVariantState` in `core/mode` to own shared Pro/manual semantics. Controllers keep mode-specific headlines, profile summaries, watermark strings, and exif key names because those are product behavior, not shared state machinery.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, `kotlin.test`.

---

## Files

- Create: `core/mode/src/main/kotlin/com/opencamera/core/mode/ProVariantState.kt`
- Create: `core/mode/src/test/kotlin/com/opencamera/core/mode/ProVariantStateTest.kt`
- Modify: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- Modify: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- Modify: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- Do not modify behavior: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`

## Task 1: Add Shared State

- [ ] **Step 1: Create `ProVariantState.kt`**

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.settings.compactSummary
import com.opencamera.core.settings.toMetadataTags

data class ProVariantToggleResult(
    val enabled: Boolean,
    val eventSuffix: String,
    val signal: ModeSignal
)

class ProVariantState(
    private val context: ModeContext
) {
    var isEnabled: Boolean = false
        private set

    fun toggle(modeDisplayName: String): ProVariantToggleResult {
        isEnabled = !isEnabled
        val eventSuffix = if (isEnabled) "entered" else "exited"
        val hint = when {
            isEnabled && manualControlsEnabled() -> "$modeDisplayName Pro on"
            isEnabled -> "$modeDisplayName Pro assist on"
            else -> "$modeDisplayName Pro off"
        }
        return ProVariantToggleResult(
            enabled = isEnabled,
            eventSuffix = eventSuffix,
            signal = ModeSignal.ShowHint(hint)
        )
    }

    fun manualControlsEnabled(): Boolean =
        context.runtimeState().deviceCapabilities.supportsAppliedManualControls

    fun currentManualDraft() =
        context.settingsSnapshot.catalog.manualCaptureDraft

    fun currentManualDraftOrNull() =
        currentManualDraft().takeIf { isEnabled }

    fun modeVariantTag(): String =
        if (isEnabled) "pro" else "standard"

    fun resolvedControlMode(): String =
        if (manualControlsEnabled()) "manual" else "assisted"

    fun manualDraftState(): String =
        if (manualControlsEnabled()) "metadata-draft" else "unsupported"

    fun resolvedAlgorithmProfile(base: String): String {
        return if (!isEnabled) {
            base
        } else if (manualControlsEnabled()) {
            "$base-pro"
        } else {
            "$base-pro-assist"
        }
    }

    fun proActionLabel(): String {
        return if (isEnabled) {
            if (manualControlsEnabled()) "Exit Pro" else "Exit Pro Assist"
        } else if (manualControlsEnabled()) {
            "Enter Pro"
        } else {
            "Enter Pro Assist"
        }
    }

    fun variantExifLabel(): String =
        if (manualControlsEnabled()) "Pro" else "Pro Assist"

    fun metadataTags(): Map<String, String> {
        if (!isEnabled) return emptyMap()
        return buildMap {
            put("controlMode", resolvedControlMode())
            put("manualDraftState", manualDraftState())
            putAll(currentManualDraft().toMetadataTags())
        }
    }

    fun summaryText(requestName: String): String {
        return if (manualControlsEnabled()) {
            "Pro draft ${currentManualDraft().compactSummary()} is attached to the $requestName request."
        } else {
            "Pro assist keeps ${currentManualDraft().compactSummary()} as saved-only draft because manual controls are unavailable on this device."
        }
    }
}
```

- [ ] **Step 2: Add state tests**

Create `core/mode/src/test/kotlin/com/opencamera/core/mode/ProVariantStateTest.kt`:

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.ManualCaptureParams
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProVariantStateTest {

    @Test
    fun `initial state is disabled`() {
        val state = ProVariantState(context = proContext())

        assertFalse(state.isEnabled)
        assertEquals("standard", state.modeVariantTag())
        assertNull(state.currentManualDraftOrNull())
        assertEquals("photo-original", state.resolvedAlgorithmProfile("photo-original"))
    }

    @Test
    fun `toggle enables manual pro state`() {
        val state = ProVariantState(context = proContext())

        val result = state.toggle("Portrait")

        assertTrue(result.enabled)
        assertEquals("entered", result.eventSuffix)
        assertEquals(ModeSignal.ShowHint("Portrait Pro on"), result.signal)
        assertEquals("pro", state.modeVariantTag())
        assertEquals("manual", state.resolvedControlMode())
        assertEquals("metadata-draft", state.manualDraftState())
        assertEquals("photo-original-pro", state.resolvedAlgorithmProfile("photo-original"))
        assertEquals("Exit Pro", state.proActionLabel())
    }

    @Test
    fun `toggle disables after enable`() {
        val state = ProVariantState(context = proContext())

        state.toggle("Scenery")
        val result = state.toggle("Scenery")

        assertFalse(result.enabled)
        assertEquals("exited", result.eventSuffix)
        assertEquals(ModeSignal.ShowHint("Scenery Pro off"), result.signal)
        assertEquals("standard", state.modeVariantTag())
    }

    @Test
    fun `manual unavailable uses assisted metadata and pro assist labels`() {
        val state = ProVariantState(
            context = proContext(
                deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsManualControls = false
                )
            )
        )

        state.toggle("Humanistic")

        assertEquals("assisted", state.resolvedControlMode())
        assertEquals("unsupported", state.manualDraftState())
        assertEquals("photo-original-pro-assist", state.resolvedAlgorithmProfile("photo-original"))
        assertEquals("Exit Pro Assist", state.proActionLabel())
        assertEquals("Pro Assist", state.variantExifLabel())
        assertTrue(
            state.summaryText("humanistic").contains(
                "saved-only draft because manual controls are unavailable"
            )
        )
    }

    @Test
    fun `metadata tags are emitted only when enabled`() {
        val state = ProVariantState(
            context = proContext(
                manualDraft = ManualCaptureParams(
                    rawEnabled = true,
                    iso = 320,
                    shutterSpeedMillis = 33L,
                    whiteBalanceKelvin = 4800
                )
            )
        )

        assertTrue(state.metadataTags().isEmpty())

        state.toggle("Portrait")
        val tags = state.metadataTags()

        assertEquals("manual", tags["controlMode"])
        assertEquals("metadata-draft", tags["manualDraftState"])
        assertEquals("on", tags["manualDraftRaw"])
        assertEquals("320", tags["manualDraftIso"])
        assertEquals("33", tags["manualDraftShutterSpeedMillis"])
        assertEquals("4800", tags["manualDraftWhiteBalanceKelvin"])
    }

    private fun proContext(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        manualDraft: ManualCaptureParams = ManualCaptureParams()
    ): ModeContext {
        return ModeContext(
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = deviceCapabilities,
                    lensFacing = LensFacing.BACK,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
                )
            },
            settingsSnapshotProvider = {
                SessionSettingsSnapshot(
                    catalog = FeatureCatalog(
                        manualCaptureDraft = manualDraft
                    )
                )
            }
        )
    }
}
```

- [ ] **Step 3: Verify the state tests**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ProVariantStateTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Migrate Portrait, Night, And Humanistic

Add this import to each file:

```kotlin
import com.opencamera.core.mode.ProVariantState
```

### Shared Migration Pattern

In each controller:

- [ ] Replace the mutable boolean:

```kotlin
private var proVariantEnabled = false
```

with:

```kotlin
private val proVariantState = ProVariantState(context)
private val proVariantEnabled: Boolean
    get() = proVariantState.isEnabled
```

- [ ] Replace duplicated helper bodies with delegating wrappers:

```kotlin
private fun manualControlsEnabled(): Boolean =
    proVariantState.manualControlsEnabled()

private fun currentManualDraft() =
    proVariantState.currentManualDraft()

private fun currentManualDraftOrNull() =
    proVariantState.currentManualDraftOrNull()

private fun resolvedControlMode(): String =
    proVariantState.resolvedControlMode()

private fun manualDraftState(): String =
    proVariantState.manualDraftState()

private fun resolvedAlgorithmProfile(base: String): String =
    proVariantState.resolvedAlgorithmProfile(base)
```

The wrappers keep the first migration small. A later cleanup may inline calls to `proVariantState`, but do not combine that cleanup with this behavior-preserving refactor.

### Portrait

- [ ] Replace `toggleProVariant()` with:

```kotlin
private suspend fun toggleProVariant(): ModeSignal {
    val result = proVariantState.toggle("Portrait")
    context.eventSink("portrait.pro-variant.${result.eventSuffix}")
    mutableSnapshot.value = buildSnapshot(
        headline = if (proVariantEnabled) {
            if (manualControlsEnabled()) {
                "Portrait Pro active"
            } else {
                "Portrait Pro assist active"
            }
        } else if (depthEffectEnabled()) {
            "Portrait mode active"
        } else {
            "Portrait focus active"
        }
    )
    return result.signal
}
```

- [ ] In `buildSnapshot`, replace the nested `proActionLabel` expression with:

```kotlin
proActionLabel = proVariantState.proActionLabel()
```

- [ ] In metadata builders, keep existing keys but prefer shared methods where the edit is local:

```kotlin
put("modeVariant", proVariantState.modeVariantTag())
if (proVariantEnabled) {
    putAll(proVariantState.metadataTags())
}
```

- [ ] In exif override code, keep key `PortraitVariant` and replace the variant value with:

```kotlin
put("PortraitVariant", proVariantState.variantExifLabel())
```

- [ ] In `styleSummary`, replace duplicated Pro summary text with:

```kotlin
val proSummary = proVariantState.summaryText("portrait")
```

### Night

- [ ] Replace `toggleProVariant()` with:

```kotlin
private suspend fun toggleProVariant(): ModeSignal {
    val result = proVariantState.toggle("Scenery")
    context.eventSink("night.pro-variant.${result.eventSuffix}")
    mutableSnapshot.value = buildSnapshot(
        headline = if (proVariantEnabled) {
            if (manualControlsEnabled()) {
                "Scenery Pro active"
            } else {
                "Scenery Pro assist active"
            }
        } else if (multiFrameEnabled()) {
            "Scenery mode active"
        } else {
            "Scenery brightening active"
        }
    )
    return result.signal
}
```

- [ ] In `buildSnapshot`, set:

```kotlin
proActionLabel = proVariantState.proActionLabel()
```

- [ ] In metadata builders, keep current keys and replace the shared values:

```kotlin
put("modeVariant", proVariantState.modeVariantTag())
if (proVariantEnabled) {
    putAll(proVariantState.metadataTags())
}
```

- [ ] In exif override code, keep key `NightVariant` and replace the variant value with:

```kotlin
put("NightVariant", proVariantState.variantExifLabel())
```

- [ ] In `profileSummary`, replace duplicated Pro summary text with:

```kotlin
val proSummary = proVariantState.summaryText("scenery capture")
```

### Humanistic

- [ ] Replace `toggleProVariant()` with:

```kotlin
private suspend fun toggleProVariant(): ModeSignal {
    val result = proVariantState.toggle("Humanistic")
    context.eventSink("humanistic.pro-variant.${result.eventSuffix}")
    mutableSnapshot.value = buildSnapshot(
        headline = if (proVariantEnabled) {
            if (manualControlsEnabled()) {
                "Humanistic Pro active"
            } else {
                "Humanistic Pro assist active"
            }
        } else {
            "Humanistic mode active"
        }
    )
    return result.signal
}
```

- [ ] In `buildSnapshot`, set:

```kotlin
proActionLabel = proVariantState.proActionLabel()
```

- [ ] In metadata builders, keep current keys and replace shared values:

```kotlin
put("modeVariant", proVariantState.modeVariantTag())
if (proVariantEnabled) {
    putAll(proVariantState.metadataTags())
}
```

- [ ] In exif override code, keep key `HumanisticVariant` and replace the variant value with:

```kotlin
put("HumanisticVariant", proVariantState.variantExifLabel())
```

- [ ] In `defaultDetail`, replace duplicated Pro summary text with:

```kotlin
val proSummary = proVariantState.summaryText("humanistic")
```

## Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test :feature:mode-portrait:test :feature:mode-night:test :feature:mode-humanistic:test
```

Expected: `BUILD SUCCESSFUL`.

Run session behavior coverage:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Expected: `BUILD SUCCESSFUL`, especially existing tests whose names include `pro variant`.

## Acceptance Criteria

- Portrait, Night, and Humanistic no longer own mutable Pro variant state directly.
- Manual-draft metadata still uses keys `controlMode`, `manualDraftState`, and `manualDraft*`.
- Mode-specific exif keys remain `PortraitVariant`, `NightVariant`, and `HumanisticVariant`.
- Pro mode itself is unchanged because it is always Pro rather than a toggleable variant.
- Existing session tests for manual and assisted Pro variants pass unchanged.

