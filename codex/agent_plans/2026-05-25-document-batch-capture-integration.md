# Document Batch Capture Integration

## Goal

Append each completed document capture to the current document batch so the side rail can show count, content, order, and processing status while the user continues shooting.

## Context

- User request: Users should be able to continuously shoot document pages while confirming captured thumbnails and order.
- Verified facts:
  - `DocumentModePlugin` writes `mode=document`, `profile`, `scanMode`, `outputClass=scan`, and effect bridge tags into shot metadata.
  - `DocumentAutoCropPostProcessor` writes pipeline notes for auto-crop applied/skipped/failed.
  - `CaptureRecordingSessionProcessor.handleShotCompleted(result)` already updates `latestThumbnailSource`, paths, saved media type, and pipeline notes.
  - Existing capture-feedback work distinguishes pending preview feedback from official saved media.
- Relevant files:
  - `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/camera/DocumentAutoCropPostProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
  - `app/src/test/java/com/opencamera/app/camera/DocumentAutoCropPostProcessorTest.kt`
- Non-goals:
  - Do not change document image algorithms in this package.
  - Do not add a blocking post-shot review page.
  - Do not save a PDF or multi-page container.

## Implementation Scope

- Detect completed document shots from `ShotResult.metadata.customTags["mode"] == "document"`.
- Convert the saved `ShotResult` into a `DocumentBatchItem`.
- Append only after `ShotCompleted`, not on shutter press or pending feedback.
- Use official saved-media thumbnail/output, not raw preview feedback, as the final batch item render source.
- Derive item badges/status from pipeline notes:
  - crop applied
  - crop skipped
  - crop failed
  - enhanced/basic profile
- Keep capture flowing: no modal UI or session pause after append.

## Steps

1. Inspect the session contract added by [Document Batch Session Contracts](./2026-05-25-document-batch-session-contracts.md).
2. In `CaptureRecordingSessionProcessor.handleShotCompleted(result)`, after existing saved-media state is computed, branch on:

```kotlin
val isDocumentPhoto = result.mediaType == MediaType.PHOTO &&
    result.metadata.customTags["mode"] == "document"
```

3. If `isDocumentPhoto`, append a `DocumentBatchItem`:
   - `itemId`: prefer `result.shotId` if unique enough; otherwise combine `batchId + shotId`.
   - `shotId`: `result.shotId`.
   - `orderIndex`: append at end.
   - `outputPath`: `result.outputPath`.
   - `renderUri`: `result.thumbnailSource.renderUriOrNull()`.
   - `thumbnailSource`: `result.thumbnailSource` if not `None`; otherwise preserve a safe pending/placeholder state.
   - `profileId`: metadata `profile`.
   - `scanMode`: metadata `scanMode`.
   - `cropStatus`: parse from `result.pipelineNotes`.
   - `pipelineNotes`: `result.pipelineNotes`.
4. Add a helper for parsing crop status. Keep it pure and unit tested:

```kotlin
internal fun documentCropStatusFrom(notes: List<String>): DocumentBatchCropStatus
```

5. Update `lastMessage` for user-visible feedback:
   - `Page added`
   - `Page added • auto-cropped`
   - `Page added • original kept`
   - `Page added • processing degraded`
6. Ensure non-document photos and videos do not append.
7. Add tests:
   - document `ShotCompleted` appends one item;
   - two document shots append in order with count 2;
   - non-document `ShotCompleted` does not append;
   - crop notes map to expected crop status;
   - `ThumbnailSource.None` does not crash side rail state;
   - append clears/does not misuse `pendingCaptureFeedback` according to existing saved-media behavior.

## Acceptance Criteria

- A completed document photo appears in `presentation.documentBatch.items`.
- The batch count equals the number of appended, non-removed document items.
- Batch order follows capture order by default.
- The latest item id points to the newest appended page.
- Crop/enhancement status is derived from existing metadata and pipeline notes, not from UI guesses.
- Existing bottom thumbnail and gallery opening semantics do not regress.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.DocumentAutoCropPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Do not append on `ShotStarted`; that would show a page that might never save.
- Do not append from `pendingCaptureFeedback`; the user wants confirmation that the document was captured, not just that shutter was pressed.
- If post-processing fails but the primary JPEG saves, append the item with a degraded/original-kept status. This matches the conservative document-mode direction.

