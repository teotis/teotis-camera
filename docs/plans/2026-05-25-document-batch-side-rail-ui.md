# Document Batch Side Rail UI

## Goal

Render a low-obstruction side rail in document mode that lets the user confirm captured page count, thumbnails, and current order while continuing to shoot.

## Context

- User request: A semi-transparent side column should show this round's captured document thumbnails, support deletion/removal, and ideally support ordering without blocking capture.
- Verified facts:
  - Current layout uses `activity_main.xml` with a `PreviewView`, `PreviewOverlayView`, top controls, mode track, and bottom cockpit.
  - `MainActivityViews` binds named view groups from XML.
  - `MainActivity.render()` already renders thumbnail state from `SessionPresentationState`.
  - `MainActivityActionBinder` already dispatches UI actions through callbacks/session intents.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionCallbacks.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - New suggested file: `app/src/main/java/com/opencamera/app/DocumentBatchRailRenderModel.kt`
  - New suggested test: `app/src/test/java/com/opencamera/app/DocumentBatchRailRenderModelTest.kt`
- Non-goals:
  - Do not implement direct thumbnail drag-sort in the rail in this package.
  - Do not open a blocking review page after each shot.
  - Do not store the batch list in UI.

## Implementation Scope

- Add a document batch rail view to the existing camera screen.
- Show it only when active mode is `ModeId.DOCUMENT` and there is an active/current batch.
- Render:
  - page count;
  - ordered thumbnails;
  - newest-page highlight;
  - per-item processing badge if cheap: cropped/original/degraded;
  - remove-from-batch action.
- Allow the rail to be vertically movable so it can avoid blocking the document area.
- Keep movement offset UI-ephemeral only; the batch content and order remain session-owned.
- Provide tap-to-preview for a thumbnail if there is already a safe image preview component. If not, use a simple non-blocking enlarged overlay or defer full preview to the organize package.

## Suggested UX Contract

- Default position: right side, vertically centered above the bottom cockpit.
- Default width: compact enough to avoid stealing the framing area; roughly thumbnail width plus padding.
- Appearance: translucent dark/neutral background; thumbnails remain readable; count visible at top.
- Auto behavior:
  - When a new page is appended, rail briefly expands or highlights the new thumbnail.
  - After a short idle period, rail may return to compact width.
- User actions:
  - Tap item: preview that page or select it.
  - Tap remove icon: dispatch `DocumentBatchRemoveItem(itemId)`.
  - Tap organize/count/header: open the organizer interaction from the next package.
  - Drag rail handle vertically: changes only the rail's on-screen offset.

## Steps

1. Add a pure render model:

```kotlin
data class DocumentBatchRailRenderModel(
    val visible: Boolean,
    val countText: String,
    val items: List<DocumentBatchRailItemRenderModel>,
    val latestItemId: String?,
    val organizeEnabled: Boolean
)
```

2. Map from `SessionState.presentation.documentBatch` to the render model. Do not read files or decode bitmaps in the render-model function.
3. Add unit tests:
   - hidden outside document mode;
   - visible in document mode with active batch;
   - count text matches item count;
   - order follows `DocumentBatchState.items`;
   - latest item is marked.
4. Add XML views:
   - A rail container constrained to the right side of preview content.
   - A small count/header view.
   - A thumbnail list container. If using RecyclerView adds dependencies/churn, use a simple vertical `LinearLayout` and programmatic child creation for V2.
   - A small drag handle.
5. Bind views in `MainActivityViews`.
6. Render rail from `MainActivity.render(state)` or a small `DocumentBatchRailRenderer`.
7. Bind actions in `MainActivityActionBinder`:
   - remove item -> dispatch session remove intent;
   - header/organize -> open organizer panel/route;
   - rail drag -> update UI-only offset and re-render rail position.
8. Add localized strings:
   - page count, for example `%d`
   - remove from batch
   - organize pages
   - cropped/original/degraded if badges are visible.
9. Ensure side rail does not intercept preview tap-to-focus outside its bounds.

## Acceptance Criteria

- In document mode, after completed document captures, the side rail shows the correct count and ordered thumbnails.
- The rail does not appear in photo/video/pro/portrait modes.
- Removing an item from the rail dispatches a session intent and the item disappears from the batch without affecting bottom saved-media thumbnail state.
- Dragging the rail changes only the rail position and does not mutate document batch state.
- Preview tap-to-focus and shutter controls still work outside the rail.
- The rail is visually translucent and compact enough for Codex/user visual QA to judge obstruction.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Direct drag-sort inside a camera preview is easy to conflict with rail dragging, preview tap-to-focus, and one-handed shooting. Keep sort out of this package unless the organizer package is already complete.
- If the rail uses real saved-media URIs, image loading should be bounded and thumbnail-sized. Do not decode full-resolution JPEGs on the UI thread.
- If no thumbnail image is available, render a deterministic placeholder with page number rather than leaving a blank gap.

