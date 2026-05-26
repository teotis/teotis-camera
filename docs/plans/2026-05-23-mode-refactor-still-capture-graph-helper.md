# Still Capture Graph Helper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Remove duplicated still-photo `DeviceGraphSpec.stillCapture(...)` construction from six mode controllers.

**Architecture:** Add a pure helper in `core/mode` because mode controllers already depend on `ModeRuntimeState`, and `core/mode` already depends on `core/device`. Feature controllers keep their ownership of mode behavior and only delegate still graph construction.

**Tech Stack:** Kotlin/JVM, Gradle Kotlin DSL, `kotlin.test`.

---

## Files

- Create: `core/mode/src/main/kotlin/com/opencamera/core/mode/StillCaptureGraphHelper.kt`
- Create: `core/mode/src/test/kotlin/com/opencamera/core/mode/StillCaptureGraphHelperTest.kt`
- Modify: `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- Modify: `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- Modify: `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- Modify: `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- Modify: `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- Modify: `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- Do not modify: `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`

## Current Code Facts

Each still controller currently has this body with only local line-number differences:

```kotlin
private fun currentDeviceGraph(): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState().lensFacing,
        enablePreviewSnapshots = runtimeState().deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState().stillCaptureQuality,
        resolutionPreset = runtimeState().stillCaptureResolutionPreset
    )
}
```

`VideoModeController` uses `DeviceGraphSpec.videoRecording(...)` and must stay unchanged.

## Task 1: Add The Helper

- [ ] **Step 1: Create the helper file**

Add `core/mode/src/main/kotlin/com/opencamera/core/mode/StillCaptureGraphHelper.kt`:

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.device.DeviceGraphSpec

fun stillCaptureDeviceGraph(runtimeState: ModeRuntimeState): DeviceGraphSpec {
    return DeviceGraphSpec.stillCapture(
        preferredLensFacing = runtimeState.lensFacing,
        enablePreviewSnapshots = runtimeState.deviceCapabilities.supportsPreviewSnapshots,
        qualityPreference = runtimeState.stillCaptureQuality,
        resolutionPreset = runtimeState.stillCaptureResolutionPreset
    )
}
```

- [ ] **Step 2: Add a pure unit test**

Add `core/mode/src/test/kotlin/com/opencamera/core/mode/StillCaptureGraphHelperTest.kt`:

```kotlin
package com.opencamera.core.mode

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StillCaptureGraphHelperTest {

    @Test
    fun `builds still capture graph from runtime state`() {
        val runtimeState = ModeRuntimeState(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                supportsPreviewSnapshots = false
            ),
            lensFacing = LensFacing.FRONT,
            stillCaptureQuality = StillCaptureQualityPreference.QUALITY,
            stillCaptureResolutionPreset = StillCaptureResolutionPreset.SMALL_2MP
        )

        val graph = stillCaptureDeviceGraph(runtimeState)

        assertEquals(CaptureTemplate.STILL_CAPTURE, graph.template)
        assertEquals(LensFacing.FRONT, graph.preferredLensFacing)
        assertFalse(graph.preview.snapshotsEnabled)
        assertEquals(
            StillCaptureQualityPreference.QUALITY,
            graph.stillCapture.qualityPreference
        )
        assertEquals(
            StillCaptureResolutionPreset.SMALL_2MP,
            graph.stillCapture.resolutionPreset
        )
    }
}
```

- [ ] **Step 3: Verify the new helper test**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.StillCaptureGraphHelperTest
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Migrate Six Still Controllers

- [ ] **Step 1: Add imports**

Add this import to each of the six still mode plugin files:

```kotlin
import com.opencamera.core.mode.stillCaptureDeviceGraph
```

- [ ] **Step 2: Replace each duplicated method**

In all six still controllers, replace the full `currentDeviceGraph()` body with:

```kotlin
private fun currentDeviceGraph(): DeviceGraphSpec =
    stillCaptureDeviceGraph(runtimeState())
```

Files to update:

- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`

- [ ] **Step 3: Confirm Video stayed untouched**

Run:

```bash
rtk rg -n "DeviceGraphSpec\\.videoRecording|stillCaptureDeviceGraph" feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt
```

Expected:

- `DeviceGraphSpec.videoRecording` is present.
- `stillCaptureDeviceGraph` is not present.

## Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test :feature:mode-photo:test :feature:mode-portrait:test :feature:mode-pro:test :feature:mode-night:test :feature:mode-document:test :feature:mode-humanistic:test
```

Expected: `BUILD SUCCESSFUL`.

Then run a focused session regression that exercises active device graphs:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Expected: `BUILD SUCCESSFUL`.

## Acceptance Criteria

- Six still controllers call `stillCaptureDeviceGraph(runtimeState())`.
- `VideoModePlugin.kt` still uses `DeviceGraphSpec.videoRecording(...)`.
- The helper test proves lens facing, preview snapshot support, still quality, and still resolution are copied from `ModeRuntimeState`.
- No session/device ownership boundary changes.

