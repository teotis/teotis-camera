# Media Output, Filter, And Thumbnail Stability Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make real saved photos receive the selected tone/filter effect, complete successfully on current Android MediaStore rules, and prevent thumbnail rollback after save failures or preview snapshots.

**Architecture:** Mode plugins and settings already place filter/watermark intent into `ShotRequest` metadata. `CameraXCaptureAdapter` owns platform output handles and shot success/failure. `MediaPostProcessor` owns final saved-media pixel edits. `DefaultCameraSession` owns thumbnail presentation state and must distinguish official saved media from transient feedback.

**Tech Stack:** Kotlin, CameraX, Android MediaStore, `core:media`, `core:session`, app unit tests.

---

## Evidence

User issues covered:

- `1`: 色彩/滤镜效果依然没有应用到成片上。
- `6`: 缩略图更新会出现回退现象。

Current log evidence from `<HOME>/Downloads/opencamera-debug-1779388460166.log`:

- Shot requests contain the selected filter data:
  - `filterProfile=custom-vivid-1`
  - `filterSpec.version=1`
  - `filterSpec.brightnessShift=-2`
  - `filterSpec.contrast=1.1108352`
  - `filterSpec.saturation=1.1945403`
- The same shots fail before a saved-media result:
  - `ShotFailed(reason=Primary directory Pictures not allowed for content://media/external/file; allowed directories are [Download, Documents])`
- Capture feedback snapshots do arrive:
  - `capture.feedback.snapshot.updated -> shotId=shot-6`
  - followed by `capture.failed`.

Current code evidence:

- `PhotoAlgorithmPostProcessor.decidePhotoAlgorithmWork()` already reads `FilterRenderSpec.fromMetadataTags(result.metadata.customTags)`, so custom filters are not just hard-coded profile ids.
- `CameraXCaptureAdapter.createLivePhotoBundle()` creates a Live sidecar through `MediaStore.Files.getContentUri("external")` with a `RELATIVE_PATH` under `Pictures/OpenCamera`.
- On current Android, generic `MediaStore.Files` rejects primary directory `Pictures`; it usually only allows roots like `Download` or `Documents`. This matches the real-device failure.
- `DefaultCameraSession.handleShotFailed()` clears `pendingCaptureFeedback` but leaves previous official thumbnail state unchanged.
- `DefaultCameraSession.handlePreviewSnapshotUpdated()` ignores preview snapshots after `latestThumbnailSource is ThumbnailSource.SavedMedia`, but it can still accept preview snapshots before the first successful saved media.

## Root-Cause Hypotheses

1. The current real-device shots are Live photos. Still JPEG succeeds far enough to produce feedback, then Live sidecar materialization fails on `MediaStore.Files` because the sidecar is placed under `Pictures`. Because `ShotCompleted` never arrives, the final postprocessed saved media and official thumbnail never become observable.
2. Filter rendering likely works when a `ShotCompleted` path reaches `PhotoAlgorithmPostProcessor` with an editable `contentUri` or `filePath`, but the current failure path prevents validating the final result.
3. Thumbnail rollback is likely a state precedence problem between transient capture feedback, saved media, and preview snapshots during failure/rebind sequences. The session protects saved media from later preview snapshots, but it has no generation/recency guard for all transient states.

## Required Behavior

- A photo shot with selected filter must reach `ShotCompleted` on the real-device path.
- `latestPipelineNotes` for a successful filtered photo must include `algorithm-render:applied:<profile-id>`.
- If watermark is enabled, successful output should also include `watermark:rendered` or an explicit skip/failure note.
- Live sidecar creation must not cause a still-photo save failure merely because `Pictures` is not allowed for `MediaStore.Files`.
- The visible thumbnail must obey this precedence:
  1. pending capture feedback for the active shot only,
  2. latest official saved media,
  3. preview snapshot only before any official saved media exists.
- Capture failure must clear only the active transient feedback and keep the last official saved-media thumbnail.
- Preview snapshots after any successful saved-media thumbnail must never overwrite it.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt` if a thumbnail generation marker is added.
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterLivePhotoTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/test/java/com/opencamera/app/ThumbnailRenderCommandTest.kt` if render command behavior changes.

Do not modify mode plugin ownership unless a test proves metadata is missing before adapter execution.

## Implementation Tasks

- [ ] **Step 1: Reproduce the MediaStore sidecar failure in a unit-level helper test**

Add a pure helper around sidecar handle creation so it can be tested without a real `ContentResolver`.

Target behavior:

- For still images stored under `Pictures/OpenCamera`, Live sidecar must not be inserted into `MediaStore.Files` under `Pictures`.
- Either write sidecar to app-private external/files storage with a `filePath`, or place generic sidecar under an allowed collection such as `Documents/OpenCamera/Live`.
- Keep `LivePhotoBundle.sidecarPath` and `sidecarHandle` accurate.

Preferred approach:

- Keep the JPEG in `MediaStore.Images`.
- Store JSON sidecar in an app-private file path when Android scoped storage rejects generic files under `Pictures`.
- Add a diagnostic note such as `device:live-sidecar=app-private` or `device:live-sidecar=documents`.

- [ ] **Step 2: Make Live sidecar materialization non-fatal to still photo save where possible**

In `captureLivePhoto()`:

- If the still image is already saved and sidecar creation fails due to MediaStore directory restrictions, prefer returning `PhotoCaptureOutcome.Success` with:
  - `livePhotoBundle = null` if the bundle is unusable, or
  - a bundle using the fallback sidecar handle if fallback materialization succeeds.
- Add a diagnostic note:
  - `device:live-sidecar=skipped:<reason>` for non-fatal skip.
  - `device:live-photo=still-only-fallback` when still photo is preserved.

Do not delete a successfully saved still image merely because optional Live sidecar failed. Only fail the whole shot if the still image itself failed.

- [ ] **Step 3: Add a filter-applied success regression**

In `PhotoAlgorithmPostProcessorTest`, keep the existing custom `FilterRenderSpec` test and add a test for a real log-like profile:

```kotlin
@Test
fun `custom vivid metadata from capture log is rendered`() = runTest {
    val editor = FakePhotoAlgorithmEditor(PhotoAlgorithmEditorResult.Applied())
    val processor = PhotoAlgorithmPostProcessor(editor)
    val result = processor.process(
        photoResult(
            algorithmProfile = "custom-vivid-1",
            saveRequest = SaveRequest.photoLibrary(
                metadata = MediaMetadata(
                    algorithmProfile = "custom-vivid-1",
                    customTags = FilterRenderSpec(
                        brightnessShift = -2,
                        contrast = 1.1108352f,
                        saturation = 1.1945403f,
                        warmthShift = 2
                    ).toMetadataTags() + mapOf("filterProfile" to "custom-vivid-1")
                )
            )
        )
    )

    assertEquals(1, editor.invocations.size)
    assertEquals("custom-vivid-1", editor.invocations.single().spec.profile)
    assertTrue(result.pipelineNotes.contains("algorithm-render:applied:custom-vivid-1"))
}
```

- [ ] **Step 4: Add session thumbnail precedence tests**

In `DefaultCameraSessionTest`, cover:

1. saved-media thumbnail survives later preview snapshot.
2. pending capture feedback is cleared on `ShotFailed`.
3. after `ShotFailed`, previous saved-media thumbnail remains.
4. if no saved-media thumbnail has ever existed, preview snapshot may still populate the placeholder.
5. stale `CaptureFeedbackSnapshotUpdated(shotId=old)` is ignored after a newer active shot starts.

Expected assertion shape:

```kotlin
assertTrue(session.state.value.latestThumbnailSource is ThumbnailSource.SavedMedia)
assertNull(session.state.value.presentation.pendingCaptureFeedback)
assertEquals("Pictures/OpenCamera/photo-a.jpg", session.state.value.previewThumbnailPath)
```

- [ ] **Step 5: Strengthen visible thumbnail rendering guard if needed**

If tests show UI can reload older URIs:

- Add a monotonically increasing thumbnail generation or `shotId` to presentation state.
- Make `MainActivity` only render a new thumbnail when the source identity is newer than the last rendered saved-media identity.
- Do not make `previewThumbnail` tap open capture-feedback or preview-snapshot files.

- [ ] **Step 6: Verify with focused commands**

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterLivePhotoTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Manual Real-Device Smoke

After implementation:

1. Enable a visibly strong custom tone/filter.
2. Capture a photo with Live enabled.
3. Confirm no `Primary directory Pictures not allowed for content://media/external/file` failure appears.
4. Confirm the status/output shows a saved photo path.
5. Confirm debug output includes `algorithm-render:applied:<selected-profile>`.
6. Capture twice quickly and switch mode/lens after save; thumbnail must end on latest saved media, not preview feedback.

## Non-Goals

- Do not implement high-fidelity film color science in this loop.
- Do not require pixel-perfect visual comparison in the non-multimodal agent pass.
- Do not move thumbnail ownership out of Session Kernel.
- Do not treat transient feedback as gallery media.
