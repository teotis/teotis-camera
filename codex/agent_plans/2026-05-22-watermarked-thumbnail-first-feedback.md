# Watermarked Thumbnail First Feedback Plan

> **For agentic workers:** This is a self-contained implementation handoff for a non-multimodal agent. Do not rely on screenshots. Use code facts, tests, and trace/pipeline notes. Run shell commands through `rtk`.

**Goal:** After a photo capture, the visible thumbnail must not first show a no-watermark preview and then jump to the watermarked saved image. When watermark is enabled, the first visible capture thumbnail should either already include the watermark treatment or wait for the official watermarked saved-media result.

**Architecture:** `CameraXCaptureAdapter` owns preview bitmap capture. `DefaultCameraSession` owns thumbnail precedence. `MediaPostProcessor` owns final saved-media edits. UI only renders `SessionPresentationState`; it must not infer watermark state locally.

**Tech Stack:** Kotlin, Android CameraX `PreviewView.bitmap`, `core:session`, app media postprocessors, unit tests.

---

## Evidence

Current behavior is expected from the existing code:

- `CameraXCaptureAdapter.captureCaptureFeedbackSnapshot()` captures `PreviewView.bitmap` near `ShotStarted`.
- The captured feedback image is saved under app cache and emitted as `DeviceEvent.CaptureFeedbackSnapshotAvailable`.
- `DefaultCameraSession.handleCaptureFeedbackSnapshotUpdated()` sets `presentation.pendingCaptureFeedback`.
- `MainActivity.render()` chooses `pendingCaptureFeedback` before `latestThumbnailSource`.
- `PhotoWatermarkPostProcessor` edits the saved JPEG later, after the official `ShotCompleted` result.

Therefore, if watermark is enabled, `pendingCaptureFeedback` is a pre-postprocess preview image. It cannot be assumed to match the final output.

## Required Behavior

- If watermark is disabled, transient capture feedback may still appear immediately.
- If watermark is enabled and the chosen template is an overlay template that can be preview-rendered cheaply, the transient thumbnail may be generated with the same visible watermark treatment.
- If watermark is enabled and the chosen template expands the frame or otherwise cannot be preview-rendered accurately, suppress transient feedback and keep the previous official thumbnail until `ShotCompleted`.
- `ShotCompleted` must still replace the visible thumbnail with `ThumbnailSource.SavedMedia`.
- `ShotFailed` must clear only the active pending feedback and keep the previous saved-media thumbnail.
- Thumbnail tap should continue to target saved media only, never a cache feedback file.

## Non-Multimodal Scope

This pass verifies state, routing, and pipeline semantics. It should not try to judge visual quality of the thumbnail watermark by screenshot. The multimodal QA plan owns visual comparison.

## Files

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` only to expose a pure lightweight resolver/helper if needed.
- `app/src/main/java/com/opencamera/app/ThumbnailRenderCommand.kt` only if UI command identity needs to distinguish feedback from saved media.
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt`

Do not move thumbnail ownership into `MainActivity`.

## Implementation Tasks

- [ ] **Step 1: Add a feedback eligibility model**

Add a small pure model in session or media contracts:

```kotlin
enum class CaptureFeedbackPolicy {
    ALLOW_PREVIEW_BITMAP,
    SUPPRESS_UNTIL_SAVED_MEDIA
}
```

Derive the policy from the active `ShotRequest` metadata, not from UI state. A conservative helper can live near session code:

```kotlin
internal fun captureFeedbackPolicyFor(shot: ShotRequest): CaptureFeedbackPolicy {
    val watermarkText = shot.saveRequest.metadata.watermarkText?.trim().orEmpty()
    val template = shot.saveRequest.metadata.customTags["watermarkTemplate"].orEmpty()
    return when {
        watermarkText.isEmpty() -> CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP
        template == "classic-overlay" -> CaptureFeedbackPolicy.ALLOW_PREVIEW_BITMAP
        else -> CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA
    }
}
```

If the implementation later adds a true preview-watermark bitmap renderer, keep this helper but change the classic-overlay branch to emit the rendered cache file instead of a raw preview file.

- [ ] **Step 2: Suppress raw preview feedback when it would misrepresent the final photo**

In `DefaultCameraSession.handleCaptureFeedbackSnapshotUpdated()`:

```kotlin
val activeShot = _state.value.activeShot
if (activeShot == null || activeShot.shotId != shotId) {
    trace.record("capture.feedback.snapshot.skipped", "shotId=$shotId,active=${activeShot?.shotId}")
    return
}
if (captureFeedbackPolicyFor(activeShot) == CaptureFeedbackPolicy.SUPPRESS_UNTIL_SAVED_MEDIA) {
    trace.record("capture.feedback.snapshot.suppressed", "shotId=$shotId,reason=watermark-final-output")
    return
}
```

This is the safest first fix. It removes the visible wrong no-watermark first frame. It may show the previous thumbnail a bit longer, which is better than showing a false result.

- [ ] **Step 3: Optional lightweight overlay feedback for classic overlay**

If a bounded implementation is feasible, add an adapter-side helper to render only `classic-overlay` onto the preview feedback bitmap before saving the cache file.

Rules:

- Do not attempt expanded-frame templates in feedback cache.
- Reuse metadata keys already consumed by `resolvePhotoWatermarkTemplate()`.
- Add a note/trace marker such as `capture.feedback.watermark.preview-rendered`.
- If render fails, suppress the feedback instead of showing raw preview.

If this step grows beyond a small helper, skip it and keep suppression as the implementation for this plan.

- [ ] **Step 4: Add session tests for watermark feedback precedence**

Add tests in `DefaultCameraSessionTest`:

```kotlin
@Test
fun `watermark expanded frame suppresses raw capture feedback`() = runTest {
    val trace = InMemorySessionTrace()
    val session = createSession(trace, this)
    session.dispatch(SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true))
    session.dispatch(SessionIntent.Boot)
    session.dispatch(SessionIntent.ShutterPressed)
    runCurrent()
    val shot = assertNotNull(session.state.value.activeShot)
    val watermarkedShot = shot.copy(
        saveRequest = shot.saveRequest.copy(
            metadata = shot.saveRequest.metadata.copy(
                watermarkText = "OpenCamera",
                customTags = shot.saveRequest.metadata.customTags + ("watermarkTemplate" to "travel-polaroid")
            )
        )
    )
    session.dispatch(SessionIntent.ShotStarted(watermarkedShot))
    session.dispatch(SessionIntent.CaptureFeedbackSnapshotUpdated(watermarkedShot.shotId, "/tmp/raw-feedback.jpg"))
    advanceUntilIdle()

    assertNull(session.state.value.presentation.pendingCaptureFeedback)
    assertTrue(trace.snapshot().any { it.name == "capture.feedback.snapshot.suppressed" })
}
```

Also cover:

- no watermark accepts feedback,
- classic overlay accepts feedback or emits preview-rendered feedback if Step 3 is implemented,
- `ShotCompleted` clears feedback and sets saved media,
- `ShotFailed` keeps previous saved media.

- [ ] **Step 5: Keep UI rendering simple**

`MainActivity` should continue using:

```kotlin
pendingCaptureFeedback ?: latestThumbnailSource
```

The correctness should come from session deciding whether pending feedback exists. Do not add watermark conditionals to UI rendering.

- [ ] **Step 6: Verify**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.ThumbnailRenderCommandTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Manual Smoke

1. Enable `travel-polaroid` or another expanded-frame watermark.
2. Capture a photo.
3. The thumbnail must not show a raw no-watermark capture-feedback image before save completion.
4. After save completion, thumbnail must show latest saved media.
5. Disable watermark and capture again; transient feedback may appear.

## Non-Goals

- Do not implement high-fidelity preview watermark rendering for every template.
- Do not modify saved-image watermark rendering quality in this plan.
- Do not make UI inspect `watermarkTemplate` metadata.
- Do not use screenshot judgment as a pass/fail condition in this text-only plan.

