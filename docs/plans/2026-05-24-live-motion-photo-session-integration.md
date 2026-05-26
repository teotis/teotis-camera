# Live Motion Photo Session Integration

> For text-only agents: this is the integration plan after the preview-frame source and Google container writer exist. Keep ownership in Mode Plugin, Session Kernel, Device Adapter, and Media Pipeline. Do not add UI or coordinator side state machines.

## Goal

Wire Photo mode Live capture to produce an honest Google Motion Photo output when possible:

```text
CaptureStrategy.LivePhoto
  -> still capture
  -> preview-derived motion segment
  -> Google Motion Photo JPEG container
  -> session-visible complete/degraded/still-only result
```

## Current Baseline

- `PhotoModePlugin` already emits `CaptureStrategy.LivePhoto` when the setting is enabled.
- `CameraXCaptureAdapter.captureLivePhoto()` currently captures still and sidecar metadata only.
- `DefaultCameraSession` and `CaptureRecordingSessionProcessor` already know how to store temporal bundles and suppress still-only fallback bundles.
- `SessionUiRenderModel` currently marks Live as `DEGRADED` whenever still capture is supported.

## Required Behavior

- Live enabled in Photo mode should attempt true motion only when the preview frame source is available.
- The device/app pipeline must create a real motion MP4 segment before claiming `COMPLETE` or `DEGRADED_MOTION`.
- The final saved image should be a Google Motion Photo JPEG when container materialization succeeds.
- If motion or container creation fails after still capture, the shot completes as still-only fallback with visible diagnostics.
- If primary still capture fails, the whole shot fails.
- Existing portrait/humanistic Live declarations should keep working but can remain degraded unless those mode owners explicitly wire true motion later.

## Integration Design

### Mode Plugin

Photo mode should remain declarative:

- Keep `CaptureStrategy.LivePhoto`.
- Keep metadata tags such as `livePhotoDefault=on`.
- Add no CameraX, frame-buffer, or container code to mode plugins.
- Optionally add a metadata tag for intended container:

```text
liveContainer=google-motion-photo-jpeg
```

### Session Kernel

Session owns admission and user-visible outcome:

- Continue using `activeShot` and `ShotCompleted`/`ShotFailed`.
- Store `latestLivePhotoBundle` only when `bundle.isTemporalMedia()`.
- Map pipeline notes into `lastAction`:
  - `Live photo saved`
  - `Live photo saved (degraded motion)`
  - `Live photo saved (still only)`
  - `Photo saved (Live container failed)`
- Add no frame payloads to `SessionPresentationState`.

### Device Adapter

`CameraXCaptureAdapter` owns the platform sequence:

1. Record shutter monotonic timestamp.
2. Ask `LivePreviewFrameSource` for selected frames.
3. Capture primary still through existing `ImageCapture`.
4. Encode selected frames to a short MP4 temp file.
5. Call Google Motion Photo materializer with still + MP4.
6. Build `LivePhotoBundle` with `bundleStatus`, `temporalWindow`, and final output handles.
7. Emit `ShotCompleted` with stable diagnostics.

If step 2, 4, or 5 fails after still success, emit success with fallback/degraded notes rather than hiding the failure.

### Media Pipeline

Media owns artifact semantics:

- `LivePhotoBundle` should describe the final Motion Photo output and temp/sidecar assets.
- `PipelineMetadataPostProcessor` should add:
  - `live:status=complete`
  - `live:source=preview-ring-buffer`
  - `live:frames=<n>`
  - `motion-photo:container=google-jpeg`
  - `motion-photo:item-length=<bytes>`
  - `degraded:live-still-only` when applicable
- Cleanup rules must remove temp frames and temp MP4 on success and failure.

## Files To Inspect Or Modify

Core media:

- `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/LiveTemporalAssemblyPlanner.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/ShotExecutorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/LiveTemporalAssemblyPlannerTest.kt`

Core session:

- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

App camera:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- new files from the preview-ring-buffer and container plans under `app/src/main/java/com/opencamera/app/camera/live/`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`

App UI:

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

Script:

- `scripts/verify_stage_6b7_live_photo.sh`

## Implementation Steps

1. Extend Live capability semantics.
   - Add a small support model if needed, for example `LivePhotoExecutionSupport`.
   - Keep `SUPPORTED`, `DEGRADED`, and `UNSUPPORTED` semantics testable.
   - Update render model so Photo Live shows `SUPPORTED` only when true motion source plus container writer are available; otherwise keep `DEGRADED`.

2. Update `LivePhotoBundle` only if needed.
   - If the combined Google Motion Photo replaces the still, existing `stillPath` can remain the final visible output.
   - If a separate final output is created, add a field such as `containerHandle` and update render models/tests.
   - Do not break existing tests that assert still/motion/sidecar paths unless the plan intentionally migrates them.

3. Add motion MP4 assembly boundary.
   - Create a tiny interface in app layer, for example `LiveMotionSegmentEncoder`.
   - First implementation can encode file-ref or RGBA frames. If real encoding is too much for one loop, land fake encoder tests and keep runtime fallback explicit.
   - Real implementation should use Android APIs in app layer, not `core:media`.

4. Replace hardcoded metadata-only in `captureLivePhoto()`.
   - Current code passes `availableSource = LiveMotionSource.METADATA_ONLY`.
   - Replace with selected source and actual `ringBufferDepthMillis` / `postShutterBudgetMillis`.
   - Include frame count and selected window in `createLivePhotoBundle()`.

5. Materialize Google Motion Photo.
   - After still and motion temp succeed, call `MotionPhotoFileMaterializer`.
   - On success, point result output to the final Motion Photo file or updated still handle.
   - On failure, delete temp motion and emit still-only fallback notes.

6. Update session outcome mapping.
   - Complete temporal media: `Live photo saved`.
   - Degraded temporal media: `Live photo saved (degraded motion)`.
   - Still-only: `Live photo saved (still only)`.
   - Container failed but still saved: keep still-only action and include pipeline note.

7. Update tests.
   - Core media tests for new notes and bundle statuses.
   - App tests with fake frame source, fake encoder, fake container writer.
   - Session tests for latest bundle storage and last-action mapping.
   - UI tests for supported/degraded labels.

8. Update script.
   - Add `FrameRingBufferTest` and `MotionPhotoJpegContainerTest`.
   - Keep existing Live and assemble coverage.

## Focused Test Matrix

Core media:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest --tests com.opencamera.core.media.FrameRingBufferTest --tests com.opencamera.core.media.MotionPhotoJpegContainerTest
```

Core session:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

App:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Stage script:

```bash
rtk ./scripts/verify_stage_6b7_live_photo.sh
```

## Real-Device Smoke Later

1. Enable Live default in Photo settings.
2. Wait for preview to stabilize.
3. Capture a Live photo.
4. Confirm diagnostics show preview-ring-buffer, frame count, and Google container notes.
5. Confirm thumbnail opens the saved image target.
6. Pull the file and verify it contains the appended MP4 bytes.
7. Test failure path by disabling frame source and confirming still-only fallback is visible.

This smoke does not prove flagship-quality motion. It proves the output and state semantics are honest.

## Non-Goals

- Do not move Live orchestration into `CameraSessionCoordinator`.
- Do not require portrait/humanistic Live to become complete in the same pass.
- Do not add audio.
- Do not transcode full-resolution preview.
- Do not make Google Photos playback a local unit-test requirement.
- Do not claim visual quality without saved-output review.
