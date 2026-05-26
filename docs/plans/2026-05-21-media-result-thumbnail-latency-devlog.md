# Media Result, Thumbnail, Latency, and Dev Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the bottom thumbnail represent the latest saved media, prevent preview frames from overwriting it, and add enough key-path timing to diagnose capture delay.

**Architecture:** Media Pipeline owns saved output and thumbnail source. Preview snapshots may remain diagnostics, but they must not replace saved-media thumbnails after a capture. Session Kernel owns presentation state. Device Adapter may emit timing trace through existing device/session events or diagnostics, but UI must only render state.

**Tech Stack:** Kotlin, CameraX adapter, `DefaultCameraSession`, `MediaPipelineContracts`, `SessionTrace`, app/core unit tests.

---

## Evidence and Scope

User issues covered:

- `2`: thumbnail does not accurately show the previous photo; mode/lens changes can display the previous preview frame instead.
- `3`: photo capture delay is long.
- `4`: dev log should record key internal path timings.
- Related `11`: other unnoticed issues should be made easier to diagnose.

Observed code:

- CameraX emits `DeviceEvent.PreviewSnapshotAvailable(ThumbnailSource.PreviewSnapshot(outputPath))` after first frame whenever `snapshotsEnabled` is true.
- `DefaultCameraSession.handlePreviewSnapshotUpdated()` always sets `latestThumbnailSource = source`.
- `DefaultCameraSession.handleShotCompleted()` updates thumbnail to saved media if `result.thumbnailSource` is not `None`.
- Therefore any later mode/lens rebind that reaches first frame can overwrite the saved-media thumbnail with a preview snapshot.
- Dev log key events currently include `capture.photo`, `capture.saving`, `capture.saved`, `recording.requested`, `recording.started`, and `recording.saved`, but no adapter/postprocess timing or elapsed duration detail.

## Files

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt`

Optional:

- `app/src/main/java/com/opencamera/app/ThumbnailRenderCommand.kt`
- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt`

## Tasks

- [ ] **Step 1: Lock thumbnail precedence in a failing session test**

In `DefaultCameraSessionTest`, add a test:

1. Boot with camera permission.
2. Dispatch `PreviewSnapshotUpdated(ThumbnailSource.PreviewSnapshot("/tmp/preview-a.jpg"))`.
3. Complete a photo with `ThumbnailSource.SavedMedia("/tmp/photo-a.jpg", "file:///tmp/photo-a.jpg")`.
4. Dispatch another `PreviewSnapshotUpdated(ThumbnailSource.PreviewSnapshot("/tmp/preview-b.jpg"))`.
5. Assert `latestThumbnailSource` remains saved media and `previewThumbnailPath` remains `/tmp/photo-a.jpg`.

Expected initial result: fail with current behavior.

- [ ] **Step 2: Separate preview snapshot from saved-media thumbnail semantics**

Preferred minimal implementation:

- Keep `SessionPresentationState.previewThumbnailPath` only as the render path for the visible thumbnail.
- Add `latestPreviewSnapshotSource: ThumbnailSource? = null` only if diagnostics need it. If avoiding a contract expansion, simply do not promote preview snapshots once a saved-media thumbnail exists.
- Change `handlePreviewSnapshotUpdated(source)`:

```kotlin
val hasSavedMediaThumbnail = _state.value.presentation.latestThumbnailSource is ThumbnailSource.SavedMedia
if (hasSavedMediaThumbnail) {
    trace.record("preview.snapshot.ignored", source.outputPathOrNull().orEmpty())
    return
}
updateState(
    previewThumbnailPath = source.outputPathOrNull(),
    latestThumbnailSource = source
)
trace.record("preview.snapshot.updated", source.outputPathOrNull().orEmpty())
```

This preserves startup preview thumbnail before any capture, while preventing mode/lens switches from overriding the saved media.

- [ ] **Step 3: Make live/photo/video saved thumbnails durable**

In `handleShotCompleted(result)`:

- Preserve current behavior for `ThumbnailSource.SavedMedia`.
- For `LivePhotoBundle`, ensure the thumbnail source uses the still content URI/path if available.
- For video, accept that `ImageView.setImageURI(videoUri)` may not display a frame on all devices. Still store the saved media URI for gallery opening; a later UI plan can add video-frame extraction if needed.

Add or update tests for photo, video, and preview-after-saved behavior.

- [ ] **Step 4: Add timing fields to ShotResult without breaking existing callers**

In `MediaPipelineContracts.kt`, add optional timing metadata:

```kotlin
data class ShotTiming(
    val requestedAtElapsedMillis: Long? = null,
    val deviceCaptureStartedAtElapsedMillis: Long? = null,
    val deviceCaptureCompletedAtElapsedMillis: Long? = null,
    val postProcessCompletedAtElapsedMillis: Long? = null
)
```

Add `val timing: ShotTiming = ShotTiming()` to `ShotResult`.

If adding elapsed clock to core contracts feels too platform-flavored, use generic `Long?` elapsed values and only fill them in the app adapter. Keep defaults so existing tests compile.

- [ ] **Step 5: Record capture timing in CameraX adapter**

In `CameraXCaptureAdapter.executeShot` and `emitShotCompleted()`:

- Capture `requestedAt = SystemClock.elapsedRealtime()` just before the adapter starts executing a plan.
- Capture `deviceStartAt` immediately before `takePicture()` or recording start.
- Capture `deviceCompletedAt` in `onImageSaved` or recording finalize.
- Capture `postProcessCompletedAt` after `mediaPostProcessor.process(rawResult)`.

Add pipeline notes or result timing fields:

```kotlin
"timing:device=${deviceCompletedAt - deviceStartAt}ms"
"timing:postprocess=${postProcessCompletedAt - deviceCompletedAt}ms"
"timing:total=${postProcessCompletedAt - requestedAt}ms"
```

Do not block capture for timing. All timing must be passive.

- [ ] **Step 6: Promote timing to session trace and dev log**

In `DefaultCameraSession.handleShotCompleted(result)`:

- Keep existing `capture.saved` / `recording.saved`.
- Add a key/core trace event such as:

```kotlin
trace.record(
    if (result.mediaType == MediaType.PHOTO) "capture.timing" else "recording.timing",
    "shot=${result.shotId},device=${...}ms,postprocess=${...}ms,total=${...}ms"
)
```

In `SessionUiRenderModel.kt`:

- Add `capture.timing`, `recording.timing`, `postprocess.completed` if used, and `preview.snapshot.ignored` to suitable `KEY_EVENT_NAMES` or `CORE_EVENT_NAMES`.
- Update `DevLogRenderModelTest` to assert exported logs include timing events when present.

- [ ] **Step 7: Review capture latency knobs conservatively**

Inspect current capture defaults:

- `StillCaptureQualityPreference.LATENCY` is already used in the logged shot metadata (`stillQuality=latency`), so do not blindly lower quality further.
- `ImageCapture` uses `CAPTURE_MODE_MINIMIZE_LATENCY` for latency mode.
- Potential delay sources are CameraX still capture, file save, watermark/filter/portrait postprocess, MediaStore write, and rebinds caused by manual/resolution changes.

Only implement a latency reduction if timing proves it:

- If postprocess dominates, gate expensive filters/watermark work or move it after UI saved-state update in a follow-up plan.
- If device capture dominates, keep as diagnostic unless CameraX config is clearly wrong.
- If rebind happens before every capture due to unchanged still settings, fix `ensureStillCaptureRequest()` equality logic.

- [ ] **Step 8: Verify focused behavior**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual smoke:

- Capture a photo, then switch mode and lens. Thumbnail remains the saved photo, not the new preview frame.
- Export dev log after a photo. It includes total/device/postprocess timing.
- Capture again with filter/watermark enabled. Timing identifies whether delay is device capture or postprocess.

## Non-Goals

- Do not remove preview snapshot support entirely; it is useful before the first capture and for diagnostics.
- Do not add UI-local thumbnail state.
- Do not fake saved-media thumbnails from preview frames.
- Do not claim capture latency is fixed unless dev log timing shows the improved path.
