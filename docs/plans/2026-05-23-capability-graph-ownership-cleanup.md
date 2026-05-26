# Capability Graph Ownership Cleanup Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is the one that actually removes the remaining `core:effect -> core:device` edge.

**Goal:** Move generic capability graph ownership out of `core:effect` and stop using concrete `DeviceCapabilities` as the graph resolver input.

**Recommended architecture:** Introduce a small `core:capability` module that owns capability graph contracts and resolution. It depends on semantic query interfaces, not on `core:device`.

---

## Why This Package Exists

After the effect query contract package, `EffectCapabilityResolver` can be clean. But `core/effect/src/main/kotlin/com/opencamera/core/effect/CapabilityGraphResolver.kt` still imports:

- `com.opencamera.core.device.CapabilityGraphReport`
- `com.opencamera.core.device.CapabilityRequirement`
- `com.opencamera.core.device.CapabilityRequirementKind`
- `com.opencamera.core.device.CapabilityResolution`
- `com.opencamera.core.device.CapabilitySupport`
- `com.opencamera.core.device.DeviceCapabilities`
- `com.opencamera.core.device.ManualControlSupport`

As long as that file remains in `core:effect`, the module dependency is still real.

## Files

Add:

- `core/capability/build.gradle.kts`
- `core/capability/src/main/kotlin/com/opencamera/core/capability/CapabilityContracts.kt`
- `core/capability/src/main/kotlin/com/opencamera/core/capability/CapabilityGraphResolver.kt`
- `core/capability/src/test/kotlin/com/opencamera/core/capability/CapabilityGraphResolverTest.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceCapabilityGraphQuery.kt`

Modify:

- `settings.gradle.kts`
- `core/effect/build.gradle.kts`
- `core/device/build.gradle.kts`
- `core/mode/build.gradle.kts`
- `core/session/build.gradle.kts`
- `app/build.gradle.kts`
- `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeProductDeclaration.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/AppContainer.kt`
- related tests that import `com.opencamera.core.device.Capability*`

Delete or migrate:

- `core/effect/src/main/kotlin/com/opencamera/core/effect/CapabilityGraphResolver.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/CapabilityGraphResolverTest.kt`

Do not move `DeviceCapabilities` itself.

## New Module

Add to `settings.gradle.kts`:

```kotlin
include(":core:capability")
```

`core/capability/build.gradle.kts`:

```kotlin
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:effect"))
    implementation(project(":core:media"))
    testImplementation(kotlin("test"))
}
```

## Capability Contracts

Move these generic types from `core/device/src/main/kotlin/com/opencamera/core/device/CapabilityContracts.kt` to `core/capability/src/main/kotlin/com/opencamera/core/capability/CapabilityContracts.kt`:

- `CapabilitySupport`
- `CapabilityRequirementKind`
- `CapabilityUseSite`
- `CapabilityRequirement`
- `CapabilityResolution`
- `CapabilityGraphReport`

Use package:

```kotlin
package com.opencamera.core.capability
```

Then update imports in `core:mode`, `core:session`, tests, and app code from `com.opencamera.core.device.Capability...` to `com.opencamera.core.capability.Capability...`.

Compatibility option:

- Do not leave production code importing capability graph contracts from `core.device`.
- If a short migration bridge is needed, `core/device` may temporarily add typealiases to `core.capability`, but the final acceptance requires no `core/effect` dependency on `core/device`.

## Query Interfaces

In `core:capability`, define device-facing query semantics without importing `DeviceCapabilities`:

```kotlin
interface CapabilityGraphDeviceQuery {
    fun supportsStillCapture(): Boolean
    fun supportsVideoRecording(): Boolean
    fun supportsPreviewSnapshots(): Boolean
    fun supportsNightMultiFrame(): Boolean
    fun manualControlSummary(): CapabilityManualControlSummary
    fun rawOutputSupport(): CapabilitySupport
    fun supportsPortraitDepth(): Boolean
    fun supportsDocumentGeometry(): Boolean
}

data class CapabilityManualControlSummary(
    val hasAppliedControls: Boolean,
    val hasSavedOnlyControls: Boolean
)
```

Manual control graph logic should use this summary:

```kotlin
return when {
    summary.hasAppliedControls -> ok(req)
    summary.hasSavedOnlyControls -> savedOnly(req, "Manual controls available as saved-only draft only")
    else -> unsupported(req, "Manual controls not available on this device")
}
```

Raw output logic should use `rawOutputSupport()`:

```kotlin
return when (deviceQuery.rawOutputSupport()) {
    CapabilitySupport.SUPPORTED -> ok(req)
    CapabilitySupport.SAVED_ONLY -> savedOnly(req, "RAW saved as metadata draft only, not applied to capture")
    else -> unsupported(req, "RAW output not available on this device")
}
```

## Resolver Move

Move `CapabilityGraphResolver` from `com.opencamera.core.effect` to `com.opencamera.core.capability`.

Constructor target:

```kotlin
class CapabilityGraphResolver(
    private val deviceQuery: CapabilityGraphDeviceQuery,
    private val mediaProcessors: MediaProcessorAvailability
)
```

Keep `EffectSpec` as an argument to `resolve(...)`, because the graph still needs to know whether a requested effect is present:

```kotlin
fun resolve(
    featureId: String,
    requirements: List<CapabilityRequirement>,
    effectSpec: EffectSpec = EffectSpec.EMPTY
): CapabilityGraphReport
```

The resolver may still inspect effect entries:

- `FilterEffect`
- `PortraitEffect`
- `DocumentEffect`
- `WatermarkEffect`

That is why `core:capability` depends on `core:effect`, not the other way around.

## Device Adapter

Add `core/device/src/main/kotlin/com/opencamera/core/device/DeviceCapabilityGraphQuery.kt`:

```kotlin
package com.opencamera.core.device

import com.opencamera.core.capability.CapabilityGraphDeviceQuery
import com.opencamera.core.capability.CapabilityManualControlSummary
import com.opencamera.core.capability.CapabilitySupport

class DeviceCapabilitiesGraphQuery(
    private val capabilities: DeviceCapabilities
) : CapabilityGraphDeviceQuery {
    override fun supportsStillCapture(): Boolean = capabilities.supportsStillCapture
    override fun supportsVideoRecording(): Boolean = capabilities.supportsVideoRecording
    override fun supportsPreviewSnapshots(): Boolean = capabilities.supportsPreviewSnapshots
    override fun supportsNightMultiFrame(): Boolean = capabilities.supportsNightMultiFrame
    override fun supportsPortraitDepth(): Boolean = capabilities.supportsPortraitDepthEffect
    override fun supportsDocumentGeometry(): Boolean = capabilities.supportsDocumentScanEnhancement

    override fun manualControlSummary(): CapabilityManualControlSummary {
        val matrix = capabilities.resolvedManualControlCapabilities
        val controls = listOf(
            matrix.raw,
            matrix.iso,
            matrix.shutter,
            matrix.exposureCompensation,
            matrix.focusDistance,
            matrix.aperture,
            matrix.whiteBalance
        )
        return CapabilityManualControlSummary(
            hasAppliedControls = controls.any { it == ManualControlSupport.APPLY },
            hasSavedOnlyControls = controls.any { it == ManualControlSupport.SAVED_ONLY }
        )
    }

    override fun rawOutputSupport(): CapabilitySupport {
        return when (capabilities.resolvedManualControlCapabilities.raw) {
            ManualControlSupport.APPLY -> CapabilitySupport.SUPPORTED
            ManualControlSupport.SAVED_ONLY -> CapabilitySupport.SAVED_ONLY
            ManualControlSupport.UNSUPPORTED -> CapabilitySupport.UNSUPPORTED
        }
    }
}

fun DeviceCapabilities.asCapabilityGraphQuery(): CapabilityGraphDeviceQuery =
    DeviceCapabilitiesGraphQuery(this)
```

## App Composition

In `AppContainer.kt`, change imports:

```kotlin
import com.opencamera.core.capability.CapabilityGraphResolver
import com.opencamera.core.device.asCapabilityGraphQuery
```

Change construction:

```kotlin
val capabilityGraphResolver = CapabilityGraphResolver(
    deviceQuery = cameraAdapter.capabilities.asCapabilityGraphQuery(),
    mediaProcessors = MediaProcessorAvailability.ALL_AVAILABLE
)
```

## Session And Mode Imports

Change `SessionContracts.kt`:

```kotlin
import com.opencamera.core.capability.CapabilityGraphReport
```

Change `DefaultCameraSession.kt` constructor type:

```kotlin
private val capabilityGraphResolver: com.opencamera.core.capability.CapabilityGraphResolver? = null,
private val capabilityRequirements: () -> List<com.opencamera.core.capability.CapabilityRequirement> = { emptyList() }
```

Change `ModeProductDeclaration.kt` imports:

```kotlin
import com.opencamera.core.capability.CapabilityRequirementKind
```

Update tests in `core:mode` and `core:session` similarly.

## Build File Changes

`core/effect/build.gradle.kts` final dependencies should not include `core:device`:

```kotlin
dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:media"))
    testImplementation(kotlin("test"))
}
```

`core/device/build.gradle.kts` should depend on capability and effect only for adapter implementations:

```kotlin
dependencies {
    api(project(":core:media"))
    implementation(project(":core:settings"))
    implementation(project(":core:effect"))
    implementation(project(":core:capability"))
    testImplementation(kotlin("test"))
}
```

`core/mode/build.gradle.kts` should add:

```kotlin
api(project(":core:capability"))
```

`core/session/build.gradle.kts` should add:

```kotlin
implementation(project(":core:capability"))
```

`app/build.gradle.kts` should add:

```kotlin
implementation(project(":core:capability"))
```

## Tests

Move `CapabilityGraphResolverTest` to:

```text
core/capability/src/test/kotlin/com/opencamera/core/capability/CapabilityGraphResolverTest.kt
```

Replace `DeviceCapabilities.DEFAULT.copy(...)` test fixtures with a fake `CapabilityGraphDeviceQuery`:

```kotlin
private data class FakeCapabilityGraphDeviceQuery(
    val still: Boolean = true,
    val video: Boolean = true,
    val previewSnapshots: Boolean = true,
    val nightMultiFrame: Boolean = true,
    val manualSummary: CapabilityManualControlSummary = CapabilityManualControlSummary(
        hasAppliedControls = true,
        hasSavedOnlyControls = false
    ),
    val rawSupport: CapabilitySupport = CapabilitySupport.SAVED_ONLY,
    val portraitDepth: Boolean = true,
    val documentGeometry: Boolean = true
) : CapabilityGraphDeviceQuery {
    override fun supportsStillCapture(): Boolean = still
    override fun supportsVideoRecording(): Boolean = video
    override fun supportsPreviewSnapshots(): Boolean = previewSnapshots
    override fun supportsNightMultiFrame(): Boolean = nightMultiFrame
    override fun manualControlSummary(): CapabilityManualControlSummary = manualSummary
    override fun rawOutputSupport(): CapabilitySupport = rawSupport
    override fun supportsPortraitDepth(): Boolean = portraitDepth
    override fun supportsDocumentGeometry(): Boolean = documentGeometry
}
```

Keep all existing behavioral assertions. The test should become independent of `core:device`.

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:capability:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Acceptance

- `CapabilityGraphResolver` no longer lives in package `com.opencamera.core.effect`.
- `core/effect/src/main` has no imports from `com.opencamera.core.device`.
- `core/effect/src/test` has no imports from `com.opencamera.core.device`.
- `core/effect/build.gradle.kts` has no dependency on `:core:device`.
- Capability graph behavior is unchanged for all existing support/degraded/saved-only/preview-only/unsupported cases.
