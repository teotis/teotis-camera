# Video Quick Combined Spec Preset Plan

> For text-only agents: this task changes video quick quality from resolution-only cycling to capability-filtered `(resolution, fps)` presets such as `4K30`, `1080p60`, and `1080p30`. Use `rtk` for every command.

## Goal

Make video-mode `快捷` quality behave like a product-facing recording spec selector:

- One visible quick choice combines resolution and fps.
- Changing quick quality updates both resolution and fps together before recording.
- Unsupported combinations are not offered.
- Degraded current requests remain visible and honest.

## Required Semantics

- `VideoSpec` remains the data model. Do not add a new persisted preset id for this pass.
- Dynamic fps policy and audio profile are not part of the quick quality label. Preserve their current values when cycling quality.
- The quick label should be compact: `8K30`, `4K60`, `4K30`, `1080p60`, `1080p30`, `720p30`.
- Options are derived from `DeviceCapabilities.videoSpecConstraints`.
- The active runtime owner is `VideoModePlugin`, because it already owns `requestedVideoSpec` for video mode.
- During recording, changing quality must be blocked by existing session/mode behavior.
- UI must dispatch a session intent or settings action only; it must not call CameraX.

## Current Gap

`VideoModePlugin.cycleQuality()` currently does:

```kotlin
requestedVideoSpec = requestedVideoSpec.copy(resolution = nextResolution)
```

That changes resolution while keeping old fps. If the current spec is `1080p60`, cycling to `4K` can produce `4K60` even when the product wanted `4K30`, or produce a degraded CameraX graph if `4K60` is unsupported.

## Files To Inspect Or Modify

- `core/device/src/main/kotlin/com/opencamera/core/device/VideoSpecSelection.kt`
- `core/device/src/test/kotlin/com/opencamera/core/device/VideoSpecSelectionTest.kt`
- `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRecordingQualityTest.kt`

## Contract Helper

Add a helper in `VideoSpecSelection.kt`:

```kotlin
data class VideoQuickSpecOption(
    val resolution: VideoResolution,
    val frameRate: VideoFrameRate
) {
    val spec: VideoSpec
        get() = VideoSpec(resolution = resolution, frameRate = frameRate)

    val label: String
        get() = "${resolution.quickLabel}${frameRate.fps}"
}

val VideoResolution.quickLabel: String
    get() = when (this) {
        VideoResolution.UHD_8K -> "8K"
        VideoResolution.UHD_4K -> "4K"
        VideoResolution.FHD_1080P -> "1080p"
        VideoResolution.HD_720P -> "720p"
        VideoResolution.SD_480P -> "480p"
    }
```

Add a deterministic option builder:

```kotlin
fun VideoSpecConstraints.quickVideoSpecOptions(): List<VideoQuickSpecOption> {
    val preferred = listOf(
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.UHD_8K, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.UHD_8K, VideoFrameRate.FPS_25),
        VideoQuickSpecOption(VideoResolution.UHD_8K, VideoFrameRate.FPS_60),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_120),
        VideoQuickSpecOption(VideoResolution.UHD_4K, VideoFrameRate.FPS_100),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_120),
        VideoQuickSpecOption(VideoResolution.FHD_1080P, VideoFrameRate.FPS_100),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_120),
        VideoQuickSpecOption(VideoResolution.HD_720P, VideoFrameRate.FPS_100),
        VideoQuickSpecOption(VideoResolution.SD_480P, VideoFrameRate.FPS_30),
        VideoQuickSpecOption(VideoResolution.SD_480P, VideoFrameRate.FPS_25)
    )
    val supported = preferred.filter { option ->
        option.frameRate in frameRatesFor(option.resolution)
    }
    val extra = supportedFrameRatesByResolution
        .flatMap { (resolution, rates) ->
            rates.map { rate -> VideoQuickSpecOption(resolution, rate) }
        }
        .filterNot { option -> preferred.any { it == option } }
        .sortedWith(
            compareBy<VideoQuickSpecOption> { it.resolution.ordinal }
                .thenBy { it.frameRate.ordinal }
        )
    return (supported + extra).distinct()
}
```

Add selection helper:

```kotlin
fun VideoSpecConstraints.nextQuickVideoSpec(
    current: VideoSpec,
    preserve: VideoSpec = current
): VideoSpec? {
    val options = quickVideoSpecOptions()
    if (options.isEmpty()) return null
    val currentIndex = options.indexOfFirst {
        it.resolution == current.resolution && it.frameRate == current.frameRate
    }
    val next = options[(currentIndex + 1).mod(options.size)]
    return preserve.copy(
        resolution = next.resolution,
        frameRate = next.frameRate
    )
}
```

`preserve` keeps `dynamicFpsPolicy` and `audioProfile` untouched.

## Video Mode Changes

In `VideoModePlugin.cycleQuality()`:

1. Keep existing recording guard.
2. Get `constraints = runtimeState().deviceCapabilities.videoSpecConstraints`.
3. Resolve `nextSpec = constraints.nextQuickVideoSpec(current = requestedVideoSpec, preserve = requestedVideoSpec)`.
4. If null, return a hint that video quality is unavailable.
5. Assign `requestedVideoSpec = nextSpec`.
6. Record an event key with both fields:

```kotlin
context.eventSink("video.quality.selected.${nextSpec.resolution.storageKey}.${nextSpec.frameRate.storageKey}")
```

7. Build hint:

```kotlin
val activeVideoSpec = resolvedVideoSpec()
val requestedLabel = nextSpec.quickLabel()
val suffix = if (activeVideoSpec.resolution != nextSpec.resolution || activeVideoSpec.frameRate != nextSpec.frameRate) {
    " (active ${activeVideoSpec.quickLabel()})"
} else {
    ""
}
return ModeSignal.ShowHint("Video quality: $requestedLabel$suffix")
```

Add:

```kotlin
private fun VideoSpec.quickLabel(): String = "${resolution.quickLabel}${frameRate.fps}"
```

Use the same label in `defaultDetail()` where concise quality text is needed.

## Quick Panel Rendering

In `quickPanelSheetRenderModel()`:

- If `state.activeMode == ModeId.VIDEO`, make `qualityRow.value` the active or requested video quick label.
- If `state.activeDeviceGraph.recording.requestedVideoSpec` degrades to `recording.videoSpec`, show the applied value and keep the disabled reason/support text honest.
- Keep photo behavior from the photo plan for still modes.

Suggested helper:

```kotlin
private fun videoQualityQuickLabel(state: SessionState): String {
    val requested = state.activeDeviceGraph.recording.requestedVideoSpec
    val applied = state.activeDeviceGraph.recording.videoSpec
    val appliedLabel = applied.quickLabel()
    return if (requested.resolution == applied.resolution && requested.frameRate == applied.frameRate) {
        appliedLabel
    } else {
        "$appliedLabel*"
    }
}
```

The `*` is intentionally compact. If the implementing agent prefers words, keep it short enough for the 260dp quick sheet.

## Quick Panel Binding

Change `buttonQuickFlash` behavior in `MainActivityActionBinder`:

```kotlin
views.quickPanel.flash.setOnClickListener {
    val mode = snapshot().sessionState?.activeMode
    if (mode == ModeId.VIDEO) {
        callbacks.dispatch(SessionIntent.TertiaryActionPressed)
    } else {
        callbacks.dispatch(SessionIntent.StillCaptureQualityToggled)
    }
}
```

Import `ModeId`.

This reuses existing session blocking for active recordings:

- Recording active + tertiary action -> `Stop recording before changing video quality`.
- Recording request pending -> blocked.

## Tests

Add to `VideoSpecSelectionTest`:

```kotlin
@Test
fun `quick video specs expose only supported combined options`() {
    val constraints = VideoSpecConstraints(
        supportedFrameRatesByResolution = linkedMapOf(
            VideoResolution.UHD_4K to setOf(VideoFrameRate.FPS_30),
            VideoResolution.FHD_1080P to setOf(VideoFrameRate.FPS_30, VideoFrameRate.FPS_60)
        )
    )

    assertEquals(
        listOf("4K30", "1080p60", "1080p30"),
        constraints.quickVideoSpecOptions().map { it.label }
    )
}
```

Add next-selection test:

```kotlin
@Test
fun `next quick video spec changes resolution and fps together`() {
    val constraints = VideoSpecConstraints(
        supportedFrameRatesByResolution = linkedMapOf(
            VideoResolution.UHD_4K to setOf(VideoFrameRate.FPS_30),
            VideoResolution.FHD_1080P to setOf(VideoFrameRate.FPS_60)
        )
    )

    val next = constraints.nextQuickVideoSpec(
        current = VideoSpec(
            resolution = VideoResolution.UHD_4K,
            frameRate = VideoFrameRate.FPS_30,
            dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
            audioProfile = AudioProfile.CONCERT
        )
    )

    assertEquals(VideoResolution.FHD_1080P, next?.resolution)
    assertEquals(VideoFrameRate.FPS_60, next?.frameRate)
    assertEquals(DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS, next?.dynamicFpsPolicy)
    assertEquals(AudioProfile.CONCERT, next?.audioProfile)
}
```

Add `VideoModePlugin` behavior to existing mode tests if present, or cover through `DefaultCameraSessionTest`:

- Start in video mode with constraints `{4K30, 1080p60}`.
- Dispatch `SessionIntent.TertiaryActionPressed`.
- Assert `state.activeDeviceGraph.recording.requestedVideoSpec` changed from `4K30` to `1080p60`.
- Assert `state.activeDeviceGraph.recording.videoSpec` matches when supported.

Add to `SessionCockpitRenderModelTest`:

```kotlin
@Test
fun `quick quality row shows combined video spec in video mode`() {
    val state = defaultSessionState(
        activeMode = ModeId.VIDEO,
        activeDeviceGraph = DeviceGraphSpec.videoRecording(
            requestedVideoSpec = VideoSpec(
                resolution = VideoResolution.FHD_1080P,
                frameRate = VideoFrameRate.FPS_60
            ),
            resolvedVideoSpec = VideoSpec(
                resolution = VideoResolution.FHD_1080P,
                frameRate = VideoFrameRate.FPS_60
            )
        )
    )

    val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

    assertEquals("1080p60", sheet.qualityRow.value)
}
```

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.VideoSpecSelectionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRecordingQualityTest
```

Then run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Acceptance

- Video quick quality cycles only supported `(resolution, fps)` combinations.
- A quick cycle changes fps together with resolution.
- Existing low-light dynamic fps and audio profile values are preserved.
- Active recording blocks quality changes.
- CameraX receives the resolved `VideoSpec` through existing graph binding.
- The settings page can remain split by resolution/fps for defaults in this pass.
