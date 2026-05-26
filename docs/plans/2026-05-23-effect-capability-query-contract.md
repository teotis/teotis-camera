# Effect Capability Query Contract Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is behavior-preserving and should not move the capability graph resolver yet.

**Goal:** Make `EffectCapabilityResolver` depend on an effect-owned capability query interface instead of concrete `DeviceCapabilities`.

**Architecture:** `core:effect` defines the query it needs. `core:device` adapts `DeviceCapabilities` to that query. `app` composition passes the adapter to the resolver.

---

## Current Code Facts

- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt` imports `com.opencamera.core.device.DeviceCapabilities`.
- `EffectCapabilityResolver` reads:
  - `supportsPortraitDepthEffect`
  - `supportsDocumentScanEnhancement`
- The external review also names manual controls. The current `EffectCapabilityResolver` does not use manual controls yet, but the interface may include it for near-future effect gating without exposing `DeviceCapabilities`.
- Tests in `EffectCapabilityResolverTest` construct resolver instances with `DeviceCapabilities.DEFAULT`.

## Files

Modify:

- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectCapabilityResolverTest.kt`
- `core/device/build.gradle.kts`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceEffectCapabilityQuery.kt` (new)
- `app/src/main/java/com/opencamera/app/AppContainer.kt`

Do not modify in this package:

- `core/effect/src/main/kotlin/com/opencamera/core/effect/CapabilityGraphResolver.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/CapabilityContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`

Those belong to the next package.

## Contract Shape

Add to `EffectCapability.kt`:

```kotlin
interface EffectCapabilityQuery {
    fun supportsPortraitDepth(): Boolean
    fun supportsDocumentGeometry(): Boolean
    fun supportsManualControls(): Boolean

    companion object {
        val DefaultSupported = object : EffectCapabilityQuery {
            override fun supportsPortraitDepth(): Boolean = true
            override fun supportsDocumentGeometry(): Boolean = true
            override fun supportsManualControls(): Boolean = true
        }
    }
}
```

Change resolver construction:

```kotlin
class EffectCapabilityResolver(
    private val capabilities: EffectCapabilityQuery = EffectCapabilityQuery.DefaultSupported
)
```

Change internal checks:

```kotlin
private fun resolvePortrait(entry: PortraitEffect): EffectCapabilityResult {
    return if (capabilities.supportsPortraitDepth()) {
        EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
    } else {
        val degraded = entry.copy(renderPath = "focus")
        EffectCapabilityResult(
            degraded,
            EffectSupport.DEGRADED,
            "Device does not support depth effect, using focus mode"
        )
    }
}
```

```kotlin
private fun resolveDocument(entry: DocumentEffect): EffectCapabilityResult {
    return if (capabilities.supportsDocumentGeometry()) {
        EffectCapabilityResult(entry, EffectSupport.SUPPORTED)
    } else {
        val degraded = entry.copy(autoCrop = false, contrastProfile = null)
        EffectCapabilityResult(
            degraded,
            EffectSupport.DEGRADED,
            "Device does not support document scan enhancement"
        )
    }
}
```

## Device Adapter

Add `core/device/src/main/kotlin/com/opencamera/core/device/DeviceEffectCapabilityQuery.kt`:

```kotlin
package com.opencamera.core.device

import com.opencamera.core.effect.EffectCapabilityQuery

class DeviceCapabilitiesEffectQuery(
    private val capabilities: DeviceCapabilities
) : EffectCapabilityQuery {
    override fun supportsPortraitDepth(): Boolean =
        capabilities.supportsPortraitDepthEffect

    override fun supportsDocumentGeometry(): Boolean =
        capabilities.supportsDocumentScanEnhancement

    override fun supportsManualControls(): Boolean =
        capabilities.supportsAppliedManualControls
}

fun DeviceCapabilities.asEffectCapabilityQuery(): EffectCapabilityQuery =
    DeviceCapabilitiesEffectQuery(this)
```

Update `core/device/build.gradle.kts`:

```kotlin
dependencies {
    api(project(":core:media"))
    implementation(project(":core:settings"))
    implementation(project(":core:effect"))
    testImplementation(kotlin("test"))
}
```

This is acceptable only after `core:effect` no longer depends on `core:device` for this resolver path. Package 2 must remove the remaining effect-to-device edge.

## App Composition

In `AppContainer.kt`:

```kotlin
import com.opencamera.core.device.asEffectCapabilityQuery
```

Change:

```kotlin
val effectCapabilityResolver = EffectCapabilityResolver(cameraAdapter.capabilities)
```

to:

```kotlin
val effectCapabilityResolver = EffectCapabilityResolver(
    cameraAdapter.capabilities.asEffectCapabilityQuery()
)
```

## Tests

Update `EffectCapabilityResolverTest` to stop importing `DeviceCapabilities`.

Use a local fake:

```kotlin
private data class FakeEffectCapabilityQuery(
    val portraitDepth: Boolean = true,
    val documentGeometry: Boolean = true,
    val manualControls: Boolean = true
) : EffectCapabilityQuery {
    override fun supportsPortraitDepth(): Boolean = portraitDepth
    override fun supportsDocumentGeometry(): Boolean = documentGeometry
    override fun supportsManualControls(): Boolean = manualControls
}
```

Replace examples:

```kotlin
val resolver = EffectCapabilityResolver(FakeEffectCapabilityQuery())
```

```kotlin
val resolver = EffectCapabilityResolver(
    FakeEffectCapabilityQuery(portraitDepth = false)
)
```

```kotlin
val resolver = EffectCapabilityResolver(
    FakeEffectCapabilityQuery(documentGeometry = false)
)
```

Add one `core:device` test only if the team wants explicit adapter coverage:

- `DeviceCapabilitiesEffectQuery` maps `supportsPortraitDepthEffect`.
- It maps `supportsDocumentScanEnhancement`.
- It maps `supportsAppliedManualControls`, including the case where `supportsManualControls = false`.

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectCapabilityResolverTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test
rtk ./gradlew --no-daemon :app:assembleDebug
```

Expected result after only this package:

- `EffectCapabilityResolver` no longer imports `DeviceCapabilities`.
- `core:effect` may still depend on `core:device` because `CapabilityGraphResolver` has not moved yet.
- Do not claim full dependency inversion until the next package is complete.

## Acceptance

- `rtk rg "DeviceCapabilities" core/effect/src/main/kotlin/com/opencamera/core/effect/EffectCapability.kt` returns no matches.
- `EffectCapabilityResolverTest` has no `com.opencamera.core.device.DeviceCapabilities` import.
- Existing effect degradation output is unchanged:
  - missing portrait depth degrades `renderPath` to `focus`;
  - missing document geometry disables `autoCrop` and clears `contrastProfile`.
