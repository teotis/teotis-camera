# Zero-Latency Thumbnail Feedback Plan

> **For agentic workers:** Use this as a self-contained implementation handoff. Run shell commands through `rtk`. Keep official saved-media ownership in the Media Pipeline.

**Goal:** Make capture feel immediate by showing a transient preview-frame feedback thumbnail as soon as the user shoots, then replace it with the real saved-media thumbnail when the media pipeline finishes.

**Recommended approach:** Use preview bitmap capture as optimistic UI feedback, but do not treat it as saved media and do not let it overwrite the official gallery thumbnail.

---

## Problem

The visible thumbnail updates too slowly on real device. The user suggested using a preview stream screenshot for zero-delay feedback.

Current code already has preview snapshots:

- `CameraXCaptureAdapter.capturePreviewSnapshot()` saves `PreviewView.bitmap` after the first streaming frame.
- `DefaultCameraSession.handlePreviewSnapshotUpdated()` now ignores preview snapshots once a `ThumbnailSource.SavedMedia` exists.
- Official saved thumbnails come from `ShotResult.thumbnailSource`.

That is the right saved-media precedence, but it does not solve perceived shutter latency. The missing piece is a separate transient capture-feedback thumbnail.

## UX Contract

- On shutter press, the bottom thumbnail should update immediately to a frozen preview-frame image with a subtle saving state.
- The transient image is not gallery media.
- Tapping the thumbnail before save completion should either do nothing or show `保存中`.
- When `ShotCompleted` arrives, replace the transient feedback with `ThumbnailSource.SavedMedia`.
- If capture fails, clear the transient feedback and keep the previous saved-media thumbnail.
- Preview startup snapshots must remain diagnostics/startup fallback only.

## Implementation Scope

Modify:

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/ThumbnailRenderCommand.kt`
- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt`

Do not modify:

- Gallery opening to use preview snapshots.
- MediaStore save semantics.
- Existing saved-media `ThumbnailSource` precedence.

## Design

### 1. Add A Transient Capture Feedback Source

Prefer a separate type instead of overloading official `ThumbnailSource.SavedMedia`.

Option A, minimal:

```kotlin
data class CaptureFeedbackPreview(
    val shotId: String,
    val outputPath: String
)
```

Add to `SessionPresentationState`:

```kotlin
val pendingCaptureFeedback: CaptureFeedbackPreview? = null
```

Option B, if the team wants one sealed thumbnail family:

```kotlin
data class CaptureFeedbackSnapshot(
    val shotId: String,
    val outputPath: String
) : ThumbnailSource
```

Recommendation: Option A. It prevents future agents from accidentally treating feedback frames as real saved media.

### 2. Emit Feedback Snapshot From CameraX Adapter

Add a device event:

```kotlin
data class CaptureFeedbackSnapshotAvailable(
    val shotId: String,
    val outputPath: String
) : DeviceEvent
```

In `CameraXCaptureAdapter.executeShot()` or the earliest point where the `ShotRequest` is known and preview is bound:

- Call a new `capturePreviewFeedbackSnapshot(shotId)` before `takePicture()`.
- Reuse the `PreviewView.bitmap` path, but save under a separate cache directory, for example `cacheDir/capture-feedback`.
- Keep at most 3-5 feedback files.
- If `PreviewView.bitmap` is null, skip silently and record a trace/pipeline note if there is already timing infrastructure.

Do not block actual capture waiting for bitmap compression. Launch it on adapter scope.

### 3. Route Device Event Into Session

In `CameraSessionCoordinator.handleDeviceEvent()`:

- Map `CaptureFeedbackSnapshotAvailable` to a new `SessionIntent.CaptureFeedbackSnapshotUpdated(shotId, outputPath)`.

In `DefaultCameraSession`:

- On `ShotStarted`, clear old pending feedback for unrelated completed shots.
- On `CaptureFeedbackSnapshotUpdated`, only accept it if `activeShot?.shotId == shotId`.
- On `ShotCompleted`, clear `pendingCaptureFeedback` and set official saved thumbnail as today.
- On `ShotFailed`, clear `pendingCaptureFeedback` and preserve the previous `latestThumbnailSource`.

### 4. Render Priority

In `MainActivity.render()`:

```text
renderUri = pendingCaptureFeedback.renderUri if present
else latestThumbnailSource.renderUriOrNull()
```

Keep gallery tap based on `latestThumbnailSource` or saved-media paths only. Do not open feedback paths.

Optional polish:

- Add a small alpha/overlay state to the thumbnail while `pendingCaptureFeedback != null`.
- Keep this in UI only; no new session runtime owner.

### 5. Trace And Dev Log

Add trace events:

- `capture.feedback.snapshot.requested`
- `capture.feedback.snapshot.updated`
- `capture.feedback.snapshot.skipped`
- `capture.feedback.snapshot.cleared`

Add these to dev log core events if useful. This lets real-device testers see whether slow feedback is bitmap capture, saved media, or postprocess.

## Verification

Focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ThumbnailRenderCommandTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

Stage:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Manual real-device smoke:

- Capture a photo in photo mode.
- Thumbnail changes immediately to a frozen preview frame.
- After save completes, thumbnail updates to the final saved photo.
- Tap during saving: no gallery open or a short saving message.
- Tap after save: Android viewer opens saved media.
- Capture failure path: previous saved thumbnail remains.

## Non-Goals

- Do not claim the final saved thumbnail is zero-latency.
- Do not overwrite saved media with preview screenshots.
- Do not introduce UI-local media ownership.
- Do not build video-frame extraction in this loop; video thumbnail can remain a follow-up.
