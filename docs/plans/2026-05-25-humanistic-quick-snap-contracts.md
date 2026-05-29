# Humanistic Quick Snap Contracts And CameraX Plan

日期：2026-05-25

## Goal

Give OpenCamera a testable quick-capture path that Humanistic mode can request declaratively. The user-visible result is faster shutter response for street/humanistic photos, with honest fallback when ZSL or low-latency capture is unavailable.

## Context

- User request: 重新开放人文模式，并让它具备独特于拍照模式的快速抓拍能力。
- Verified facts:
  - `CaptureStrategy.SingleFrame` and `CaptureProfile` already carry mode-level capture preferences into `ShotExecutor`.
  - `CameraXCaptureAdapter.createImageCapture` already maps `StillCaptureQualityPreference.LATENCY` to CameraX `CAPTURE_MODE_MINIMIZE_LATENCY`.
  - Android CameraX ZSL exists through `CAPTURE_MODE_ZERO_SHOT_LAG`, uses a recent-frame ring buffer, and falls back to minimize latency when unsupported; it is experimental and unavailable with video capture, Camera extensions, or flash ON/AUTO.
  - Current source appears inconsistent: `CameraXCaptureAdapter.kt` expects still-capture quality/resolution fields that are absent from the currently inspected `StillCaptureConfig` / `DeviceShotRequest`.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt`
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
  - `core/mode/src/main/kotlin/com/opencamera/core/mode/StillCaptureGraphHelper.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- Non-goals:
  - Do not introduce a second session kernel.
  - Do not make UI directly call CameraX or choose capture templates.
  - Do not implement high-speed burst selection in this package.
  - Do not claim true zero-shutter-lag unless `isZslSupported()` and adapter diagnostics prove it.

## Implementation Scope

- Add a small quick-capture semantic to the existing capture contracts.
- Reconcile still-capture quality fields so mode/device/app code compile from one source of truth.
- Let Humanistic request quick capture through `CaptureProfile` or `DeviceGraphSpec`, not through app UI code.
- Add CameraX ZSL selection with conservative fallback and diagnostics.

## Recommended Model

Add an enum in `core/media` or `core/device`:

```kotlin
enum class CaptureLatencyPriority {
    DEFAULT,
    QUICK_SNAP,
    ZSL_WHEN_SUPPORTED
}
```

Add it to `CaptureProfile`:

```kotlin
data class CaptureProfile(
    val frameCount: Int = 1,
    val flashMode: FlashMode = FlashMode.OFF,
    val latencyPriority: CaptureLatencyPriority = CaptureLatencyPriority.DEFAULT
)
```

Translate it into `DeviceShotRequest`:

```kotlin
data class DeviceShotRequest(
    val shotId: String,
    val template: CaptureTemplate,
    val shotKind: ShotKind,
    val latencyPriority: CaptureLatencyPriority = CaptureLatencyPriority.DEFAULT,
    val stillCaptureQuality: StillCaptureQualityPreference? = null
)
```

For Humanistic, set:

```kotlin
CaptureProfile(
    latencyPriority = CaptureLatencyPriority.ZSL_WHEN_SUPPORTED,
    stillCaptureQuality = StillCaptureQualityPreference.LATENCY,
    stillCaptureResolutionPreset = runtimeState().stillCaptureResolutionPreset,
    manualCaptureParams = currentManualDraftOrNull()
)
```

If the current codebase prefers `DeviceGraphSpec.stillCapture.qualityPreference`, use that instead, but first make `StillCaptureConfig` and `CameraXCaptureAdapter` agree. Do not add a parallel quality field in a different layer.

## CameraX Behavior

In `CameraXCaptureAdapter.createImageCapture`, resolve capture mode as:

- `ZSL_WHEN_SUPPORTED` + `cameraInfo.isZslSupported == true` + flash OFF + still-only graph -> `ImageCapture.CAPTURE_MODE_ZERO_SHOT_LAG`.
- `QUICK_SNAP` or unsupported ZSL -> `ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY`.
- default quality path -> existing `LATENCY / QUALITY` mapping.

Add adapter diagnostics to every still shot:

- `device:latency-priority=zsl-when-supported`
- `device:capture-mode=zero-shot-lag`
- `device:capture-mode=minimize-latency`
- `device:zsl=unsupported:<reason>`
- `device:zsl=disabled:flash`

Use official CameraX constraints as implementation rules:

- ZSL requires API 23+ and `PRIVATE` reprocessing support.
- ZSL is ImageCapture-only.
- ZSL must be disabled for VideoCapture, Camera extensions, or flash ON/AUTO.
- If ZSL setup fails, fallback to minimize latency and record that fallback.

## Steps

1. Compile or inspect to reconcile `StillCaptureConfig` / `DeviceShotRequest` with `CameraXCaptureAdapter`:
   - If build currently fails because `qualityPreference`, `resolutionPreset`, or `stillCaptureQuality` are missing, fix that first.
   - Keep the fix small and contract-oriented.

2. Add latency contract:
   - Add `CaptureLatencyPriority`.
   - Add fields to `CaptureProfile` and `DeviceShotRequest`.
   - Update `DefaultDeviceShotRequestTranslator` to pass through the latency preference and add diagnostics.

3. Add shot graph diagnostics:
   - Keep `ShotKind.STILL_CAPTURE`; quick snap is a strategy, not a new media type.
   - Add graph or request diagnostics such as `shot:latency-priority=quick-snap`.

4. Update CameraX adapter:
   - Query ZSL support only in app/device adapter code.
   - Select CameraX capture mode using the rules above.
   - Preserve existing preview snapshot feedback path.

5. Update tests:
   - `ShotExecutorTest`: quick snap remains still capture.
   - `DefaultDeviceShotRequestTranslatorTest`: latency priority and diagnostics are preserved.
   - `CameraXCaptureAdapterStillCaptureQualityTest`: ZSL/min-latency/quality mapping and fallback.

6. Run verification.

## Acceptance Criteria

- Humanistic can request quick capture without UI or mode code touching CameraX.
- Photo mode keeps its current behavior unless the user changes global still quality.
- ZSL-capable configuration emits `device:capture-mode=zero-shot-lag`.
- Unsupported or blocked ZSL emits explicit degraded diagnostics and uses minimize latency.
- Flash ON/AUTO never silently claims ZSL.
- Contract tests prove quick snap is a still capture strategy, not a new hidden mode runtime.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterStillCaptureQualityTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

After integrating with Humanistic:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- CameraX ZSL is experimental and device-dependent. Treat it as a capability, not as a product guarantee.
- Rebinding ImageCapture just to switch capture mode can add preview churn. Prefer mode-enter binding, not per-shutter rebinding.
- If quick snap competes with Live Photo, prioritize quick still capture for the first loop; Live can remain an explicit user setting with degraded notes.
- 参考相机应用's `doAnchorFrameAsThumbnail()` maps well to OpenCamera's existing preview feedback snapshot. It should not be copied as an old inheritance-based shot instance.
