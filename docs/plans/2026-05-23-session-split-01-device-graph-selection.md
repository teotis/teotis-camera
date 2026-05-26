# Session Split 01: Device Graph And Selection Extraction

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this plan. Use `rtk` for every command. This package is a no-behavior-change extraction.

## Goal

Move pure selection and graph-resolution helpers out of `DefaultCameraSession.kt` before any behavior processor split.

## Why This Comes First

`DefaultCameraSession.kt` mixes session transitions with pure policy:

- default lens facing;
- next lens facing;
- preview ratio cycling;
- still quality cycling;
- still resolution preset/output-size clamping;
- zoom/output-size graph resolution;
- still output-size metadata enrichment.

These helpers are easy to test in isolation and are used by later mode/control/capture processors. Extracting them first reduces risk and prevents later agents from copying private helper functions into new processors.

## Files

Create:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionSelectionPolicy.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDeviceGraphResolver.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/SessionSelectionPolicyTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/SessionDeviceGraphResolverTest.kt`

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` only if existing assertions need imports or helper cleanup.

## Extraction Shape

Create `SessionSelectionPolicy.kt`:

```kotlin
package com.opencamera.core.session

import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset

internal object SessionSelectionPolicy {
    fun defaultLensFacing(availableLensFacings: Set<LensFacing>): LensFacing {
        return when {
            LensFacing.BACK in availableLensFacings -> LensFacing.BACK
            LensFacing.FRONT in availableLensFacings -> LensFacing.FRONT
            else -> LensFacing.BACK
        }
    }

    fun nextLensFacing(current: LensFacing, available: Set<LensFacing>): LensFacing {
        val ordered = available.sortedBy { it.ordinal }.ifEmpty { listOf(current) }
        val currentIndex = ordered.indexOf(current)
        if (currentIndex == -1) return ordered.first()
        return ordered[(currentIndex + 1) % ordered.size]
    }

    fun nextPreviewRatio(current: PreviewRatio): PreviewRatio {
        val ordered = PreviewRatio.entries
        val currentIndex = ordered.indexOf(current)
        return ordered[(currentIndex + 1) % ordered.size]
    }

    fun nextStillCaptureQuality(
        current: StillCaptureQualityPreference
    ): StillCaptureQualityPreference {
        return when (current) {
            StillCaptureQualityPreference.LATENCY -> StillCaptureQualityPreference.QUALITY
            StillCaptureQualityPreference.QUALITY -> StillCaptureQualityPreference.LATENCY
        }
    }

    fun clampStillCaptureResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        val ordered = listOf(
            StillCaptureResolutionPreset.LARGE_12MP,
            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP
        )
        if (current in available) return current
        val currentIndex = ordered.indexOf(current)
        if (currentIndex != -1) {
            for (index in currentIndex + 1..ordered.lastIndex) {
                val candidate = ordered[index]
                if (candidate in available) return candidate
            }
        }
        return ordered.firstOrNull { it in available } ?: ordered.last()
    }

    fun resolvedStillCaptureOutputSizeSelection(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>,
        fallbackPreset: StillCaptureResolutionPreset
    ): StillCaptureOutputSize? {
        if (available.isEmpty()) return null
        if (current != null && current in available) return current
        return resolveOutputSizeForPreset(fallbackPreset, available)
    }

    fun nextStillCaptureOutputSize(
        current: StillCaptureOutputSize?,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        val ordered = available.sortedByDescending { it.pixelCount }
            .ifEmpty { error("No still capture output sizes available") }
        val currentIndex = current?.let(ordered::indexOf) ?: -1
        if (currentIndex == -1) return ordered.first()
        return ordered[(currentIndex + 1) % ordered.size]
    }

    fun resolveOutputSizeForPreset(
        preset: StillCaptureResolutionPreset,
        available: List<StillCaptureOutputSize>
    ): StillCaptureOutputSize {
        val desiredPixels = preset.targetWidth.toLong() * preset.targetHeight.toLong()
        val sortedByPixels = available.sortedBy { it.pixelCount }
        return when (preset) {
            StillCaptureResolutionPreset.LARGE_12MP -> sortedByPixels
                .firstOrNull { it.pixelCount >= desiredPixels }
                ?: sortedByPixels.last()

            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP -> sortedByPixels
                .lastOrNull { it.pixelCount <= desiredPixels }
                ?: sortedByPixels.first()
        }
    }

    fun resolutionPresetForOutputSize(
        outputSize: StillCaptureOutputSize
    ): StillCaptureResolutionPreset {
        val outputPixels = outputSize.pixelCount
        return StillCaptureResolutionPreset.entries.minByOrNull { preset ->
            kotlin.math.abs(outputPixels - preset.targetWidth.toLong() * preset.targetHeight.toLong())
        } ?: StillCaptureResolutionPreset.LARGE_12MP
    }

    fun nextStillCaptureResolutionPreset(
        current: StillCaptureResolutionPreset,
        available: Set<StillCaptureResolutionPreset>
    ): StillCaptureResolutionPreset {
        val ordered = listOf(
            StillCaptureResolutionPreset.LARGE_12MP,
            StillCaptureResolutionPreset.MEDIUM_8MP,
            StillCaptureResolutionPreset.SMALL_2MP
        ).filter { it in available }
            .ifEmpty { listOf(clampStillCaptureResolutionPreset(current, available)) }
        val currentIndex = ordered.indexOf(clampStillCaptureResolutionPreset(current, available))
        if (currentIndex == -1) return ordered.first()
        return ordered[(currentIndex + 1) % ordered.size]
    }
}
```

Create `SessionDeviceGraphResolver.kt`:

```kotlin
package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.resolvedZoomRatioSelection
import com.opencamera.core.media.StillCaptureResolutionPreset

internal class SessionDeviceGraphResolver {
    fun resolve(
        baseGraph: DeviceGraphSpec,
        deviceCapabilities: DeviceCapabilities,
        requestedOutputSize: StillCaptureOutputSize? = baseGraph.stillCapture.outputSize,
        requestedZoomRatio: Float? = baseGraph.preview.zoomRatio
    ): DeviceGraphSpec {
        val resolvedOutputSize = SessionSelectionPolicy.resolvedStillCaptureOutputSizeSelection(
            current = requestedOutputSize,
            available = deviceCapabilities.availableStillCaptureOutputSizes,
            fallbackPreset = baseGraph.stillCapture.resolutionPreset
        )
        val resolvedZoomRatio = resolvedZoomRatioSelection(
            current = requestedZoomRatio,
            capability = deviceCapabilities.zoomRatioCapability
        )
        val resolvedPreset = resolvedOutputSize
            ?.let(SessionSelectionPolicy::resolutionPresetForOutputSize)
            ?: SessionSelectionPolicy.clampStillCaptureResolutionPreset(
                current = baseGraph.stillCapture.resolutionPreset,
                available = deviceCapabilities.availableStillCaptureResolutionPresets
            )
        return baseGraph.copy(
            preview = baseGraph.preview.copy(zoomRatio = resolvedZoomRatio),
            stillCapture = baseGraph.stillCapture.copy(
                resolutionPreset = resolvedPreset,
                outputSize = resolvedOutputSize
            )
        )
    }
}
```

In `DefaultCameraSession.kt`:

- Add a private resolver field:

```kotlin
private val sessionDeviceGraphResolver = SessionDeviceGraphResolver()
```

- Replace private helper calls with `SessionSelectionPolicy.*`.
- Replace the existing `resolveActiveDeviceGraph` implementation body with a call to `sessionDeviceGraphResolver.resolve`.
- Delete the moved private helper functions from the bottom of `DefaultCameraSession.kt`.
- Keep `LensFacing.label` in `DefaultCameraSession.kt` for now because it is user-facing wording used by handlers, not pure selection policy.

## Tests To Add

`SessionSelectionPolicyTest` should cover:

- default lens prefers back when back is available;
- default lens uses front when only front is available;
- next lens cycles through available facings;
- preview ratio cycles through all `PreviewRatio.entries`;
- still quality toggles latency/quality;
- still resolution clamps to the next lower supported preset when current is unavailable;
- output-size selection preserves a supported current size;
- output-size selection picks a preset-appropriate fallback when current is missing;
- native output-size cycling goes from largest to next largest and wraps.

`SessionDeviceGraphResolverTest` should cover:

- requested still output size is preserved when supported;
- unsupported requested output size falls back to preset selection;
- resolved preset follows native output size;
- requested zoom ratio is clamped or snapped according to device zoom capability.

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionSelectionPolicyTest --tests com.opencamera.core.session.SessionDeviceGraphResolverTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Acceptance Criteria

- `DefaultCameraSessionTest` passes without behavior expectation changes.
- New pure tests pass.
- No public contract changes.
- No app module changes.
- `DefaultCameraSession.kt` loses the moved helper bodies and remains functionally identical.

## Non-Goals

- Do not introduce processors in this package.
- Do not move mode switching, preview recovery, capture, recording, or settings behavior.
- Do not rename `SessionIntent`, `SessionEffect`, or `CameraSession`.
- Do not update UI or `CameraSessionCoordinator`.
