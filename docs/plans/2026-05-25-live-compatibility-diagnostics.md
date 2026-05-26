# Live Compatibility Diagnostics

## Goal

Make Live Photo product compatibility measurable: every Live capture should say what format was requested, what artifacts were actually written, whether the Google Motion Photo container was produced, and what evidence still requires real-device validation.

## Context

- User request: add product-level compatibility verification and diagnostics for Live Photo 2.0.
- Verified facts:
  - Local code already has Google Motion Photo JPEG writer tests and app Live tests.
  - `codex/documentation.md` says product pass still needs real-device smoke: confirm appended MP4/XMP and gallery recognition.
  - `CameraXCaptureAdapter` already emits notes such as `motion-photo:container=google-jpeg` and `motion-photo:container=failed:<reason>`.
- Relevant files:
  - `core/media/src/main/kotlin/com/opencamera/core/media/MotionPhotoJpegContainer.kt`
  - `core/media/src/test/kotlin/com/opencamera/core/media/MotionPhotoJpegContainerTest.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/LivePhotoContracts.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `codex/documentation.md`
- Non-goals:
  - Do not require Google Photos playback inside a unit test.
  - Do not add platform-specific hidden APIs.
  - Do not claim compatibility with every Android gallery app.

## Implementation Scope

- Add a compact compatibility diagnostics model, for example:
  - intended format: `google-motion-photo-jpeg`, `motion-mp4-sidecar`, `still-jpeg`
  - actual format: the artifact really produced
  - container status: `written`, `failed`, `not-requested`
  - motion status: `encoded`, `missing`, `failed`, `degraded`
  - gallery recognition: `untested`, `recognized`, `not-recognized`
- Keep gallery recognition as a manually supplied or QA-recorded fact, not a local unit-test assertion.
- Surface diagnostics in existing output/dev-log/status text rather than adding a new hidden state owner.
- Add byte-level tests that validate the local file has Motion Photo XMP and appended MP4 at the end.

## Steps

1. Inspect current `MotionPhotoJpegContainerTest` for XMP and appended MP4 assertions.
2. Strengthen tests to confirm no bytes follow the appended motion segment and that `Item:Length` matches the actual MP4 byte count.
3. Add or normalize diagnostics notes in `CameraXCaptureAdapter.captureLivePhoto()`:
   - `live-format:intended=<format>`
   - `live-format:actual=<format>`
   - `live-motion:status=<encoded|missing|failed|degraded>`
   - `motion-photo:xmp=present`
   - `motion-photo:appended-mp4-bytes=<n>`
   - `gallery-recognition=untested`
4. Update render-model tests so recent output text exposes enough diagnostics for QA without dumping huge metadata blobs.
5. Add a short real-device smoke checklist to the package index or `codex/documentation.md` after implementation lands.

## Acceptance Criteria

- Complete Google Motion Photo output has diagnostics proving XMP and appended MP4 length were produced.
- Motion/container failure produces a still photo with visible degraded diagnostics.
- Product QA can record gallery recognition separately without mutating core capture state.
- Unit tests cover success, container failure, missing motion, and still-only fallback.
- No diagnostic note says `google-jpeg` unless the materializer succeeded.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.MotionPhotoJpegContainerTest --tests com.opencamera.core.media.LiveTemporalAssemblyPlannerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/verify_stage_6b7_live_photo.sh
```

## Real-Device Acceptance Reserved For Codex/User

1. Build and install the APK.
2. Enable Live Photo.
3. Capture one bright-scene and one indoor-scene Live photo.
4. Confirm diagnostics include intended format, actual format, frame count, XMP/container notes, and `gallery-recognition=untested`.
5. Open the saved result in target gallery apps and record whether the item plays as motion media.
6. Pull the saved file and verify appended MP4 bytes are present.

## Risks And Notes

- Gallery recognition can vary by OEM gallery, Google Photos version, file naming, MediaStore MIME handling, and XMP strictness.
- Treat real-device recognition as product evidence, not a deterministic JVM/unit-test fact.
