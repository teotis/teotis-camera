# Document Batch Session Contracts

## Goal

Add a session-owned model for the current document capture round so UI can render page count, thumbnails, deletion, and ordering without keeping a hidden document batch in `MainActivity` or a view controller.

## Context

- User request: In document mode, continuous capture should let the user confirm page count, page content, and page order while still shooting.
- Verified facts:
  - `SessionPresentationState` already owns thumbnail and saved-media presentation state.
  - `DocumentModePlugin` tags document shots with `mode=document`.
  - Existing architecture forbids UI-local runtime ownership for camera/session facts.
  - Existing thumbnail plans require official saved-media state to remain session-owned.
- Relevant files:
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt` or the current file that defines `ThumbnailSource` and `ShotResult`
- Non-goals:
  - Do not implement OCR, PDF, perspective correction, or a full document editor.
  - Do not physically delete MediaStore files in this package.
  - Do not add UI-local storage for captured document pages.

## Implementation Scope

- Add a small document batch contract to core/session or core/media, whichever matches the current contract split.
- Add the batch state to `SessionPresentationState`.
- Add session intents for batch operations:
  - `DocumentBatchClear`
  - `DocumentBatchRemoveItem(itemId)`
  - `DocumentBatchMoveItem(itemId, direction)` or `DocumentBatchReorder(itemIdsInOrder)`
  - Optional: `DocumentBatchFinish`, if the app needs an explicit "Done" action.
- Ensure the batch is active only for the current camera session and does not claim persistence across app restart.
- Ensure the session can derive a renderable ordered list without UI mutating it.

## Suggested Contract Shape

Use names that fit the local style, but preserve these semantics:

```kotlin
data class DocumentBatchState(
    val batchId: String,
    val status: DocumentBatchStatus,
    val items: List<DocumentBatchItem> = emptyList(),
    val latestItemId: String? = null,
    val lastMessage: String? = null
)

enum class DocumentBatchStatus {
    INACTIVE,
    ACTIVE,
    FINISHED
}

data class DocumentBatchItem(
    val itemId: String,
    val shotId: String,
    val orderIndex: Int,
    val outputPath: String?,
    val renderUri: String?,
    val thumbnailSource: ThumbnailSource,
    val profileId: String?,
    val scanMode: String?,
    val cropStatus: DocumentBatchCropStatus,
    val pipelineNotes: List<String>
)

enum class DocumentBatchCropStatus {
    NOT_REQUESTED,
    APPLIED,
    SKIPPED,
    FAILED
}
```

Keep file paths and URIs nullable because some successful `ShotResult` paths can be content-uri-first. Keep `ThumbnailSource` as the official render source instead of inventing a second thumbnail source.

## Lifecycle Rules

1. When the active mode becomes `ModeId.DOCUMENT`, ensure `presentation.documentBatch.status == ACTIVE`.
2. If there is no active batch, create one with a new stable `batchId`.
3. Leaving document mode must not delete saved media.
4. For V2, leaving document mode may keep the current in-memory batch until the user clears/finishes it or the process/session resets. If this is too much for current UI, record the behavior in tests and keep it simple: active in document mode, hidden outside document mode.
5. `DocumentBatchClear` clears only the current batch state. It must not delete saved files.
6. `DocumentBatchRemoveItem` removes the item from the current batch list and renumbers visible order.
7. `DocumentBatchMoveItem` or reorder intent changes order only in the batch state; it must not rewrite saved JPEG timestamps or gallery order.

## Steps

1. Inspect `SessionContracts.kt` for the current `SessionIntent` and `SessionPresentationState` style.
2. Add the document batch data classes in the smallest appropriate core file. If `SessionContracts.kt` is already too large, prefer a new `DocumentBatchContracts.kt` in the same package and import it.
3. Add `val documentBatch: DocumentBatchState = DocumentBatchState.inactive()` to `SessionPresentationState`.
4. Add intents for clear/remove/move/reorder.
5. Route the new intents in `DefaultCameraSession` or the existing processor split. Keep logic in a small processor/helper if current session processors are already split.
6. Add tests in `DefaultCameraSessionTest` or a focused processor test:
   - entering document mode creates or activates an empty batch;
   - clear removes all batch items but leaves `latestThumbnailSource` unchanged;
   - remove item renumbers order;
   - move up/down changes order deterministically;
   - reorder ignores unknown item ids or rejects invalid reorder without corrupting state.

## Acceptance Criteria

- `DocumentBatchState` is session-owned and available from `SessionState.presentation`.
- UI has no independent list of document pages.
- Removing from batch is clearly separate from deleting saved files.
- Reordering changes only batch item order.
- Non-document modes do not append new items to the batch.
- Existing thumbnail and saved-media behavior remains unchanged.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Avoid placing batch ownership in `DocumentModePlugin`; the plugin can describe capture behavior, but `ShotCompleted` and saved-media state are session/media facts.
- Avoid naming the remove action "Delete" if the underlying file remains in gallery. Use "Remove from batch" or equivalent localized wording.
- If a later package implements physical deletion, it must be a separate app/MediaStore package with explicit permission/error semantics.

