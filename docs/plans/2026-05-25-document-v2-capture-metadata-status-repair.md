# Document V2 Capture Metadata And Crop Status Repair

## Goal

Make document batch items truthful by deriving profile, scan mode, and crop status from completed document `ShotResult` metadata and pipeline notes.

## Context

- Validation finding: `CaptureRecordingSessionProcessor` appends document batch items, but currently sets `profileId = null`, `scanMode = null`, and `cropStatus = NOT_REQUESTED`.
- `DocumentModePlugin` already writes document metadata tags such as `mode=document`, `profile`, and `scanMode`.
- `DocumentAutoCropPostProcessor` writes notes such as:
  - `document:auto-crop:applied`
  - `document:auto-crop:skipped:<reason>`
  - `document:auto-crop:failed:<reason>`
- Relevant files:
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/DocumentBatchContracts.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
  - `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/camera/DocumentAutoCropPostProcessor.kt`
- Non-goals:
  - Do not change document image processing algorithms.
  - Do not require auto-crop success for a page to appear in the batch.
  - Do not append pending capture feedback as a batch item.

## Implementation Scope

- Add a pure helper to map pipeline notes to `DocumentBatchCropStatus`.
- Add a helper to build `DocumentBatchItem` from `ShotResult`.
- Append only if the result is a completed document photo:

```kotlin
result.mediaType == MediaType.PHOTO &&
result.metadata.customTags["mode"] == "document"
```

- Fill:
  - `profileId = result.metadata.customTags["profile"]`
  - `scanMode = result.metadata.customTags["scanMode"]`
  - `cropStatus = documentCropStatusFrom(result.pipelineNotes)`
- Preserve append order and latest item id.

## Crop Status Rules

- Any note starting with `document:auto-crop:applied` -> `APPLIED`
- Any note starting with `document:auto-crop:failed` -> `FAILED`
- Any note starting with `document:auto-crop:skipped` -> `SKIPPED`
- No document auto-crop note -> `NOT_REQUESTED`

If both failed and skipped somehow appear, prefer `FAILED` because it is the strongest degraded signal. If applied appears, prefer `APPLIED`.

## Steps

1. Add `internal fun documentCropStatusFrom(notes: List<String>): DocumentBatchCropStatus` in a testable location.
2. Add `internal fun documentBatchItemFrom(result: ShotResult, orderIndex: Int): DocumentBatchItem?`.
3. Update `CaptureRecordingSessionProcessor.handleShotCompleted(result)` to use the helper.
4. Ensure non-document photos completed while not in `ModeId.DOCUMENT` do not append.
5. Add tests:
   - document result with `profile=receipt` and `scanMode=enhanced` fills those fields;
   - `document:auto-crop:applied` maps to `APPLIED`;
   - `document:auto-crop:skipped:bounds-not-found` maps to `SKIPPED`;
   - `document:auto-crop:failed:crop-exception` maps to `FAILED`;
   - no auto-crop note maps to `NOT_REQUESTED`;
   - non-document photo does not append even if active mode is document, unless the product explicitly accepts active-mode-only append. Prefer metadata gate to avoid accidental append.

## Acceptance Criteria

- Batch item metadata is not `null` when document metadata exists.
- Crop status shown by rail/organizer can be trusted.
- A degraded/skipped crop still appends a page, preserving the "always save at least the JPEG" product direction.
- Existing document auto-crop processor tests continue to pass.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*document batch*"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.DocumentAutoCropPostProcessorTest
```

## Risks And Notes

- The current validation found append depends on active mode. Using metadata as an additional guard is safer because it avoids appending a non-document photo if a stale active mode or test harness dispatches a result unexpectedly.
- If tests currently construct document-like results without metadata, update the tests to reflect real `DocumentModePlugin` output rather than weakening the production guard.

