# Document V2 Side Rail Completion

## Goal

Implement the missing side rail so document-mode users can keep shooting while seeing page count, captured-page thumbnails, current order, latest-page feedback, and a remove-from-batch affordance.

## Context

- User request: continuous document capture must let users confirm content, count, and order without a blocking review page.
- Validation finding: no `DocumentBatchRailRenderModel`, no `DocumentBatchRailRenderModelTest`, no side rail layout or renderer was found.
- Verified facts:
  - `SessionPresentationState.documentBatch` exists.
  - `DocumentBatchItem` has `itemId`, `orderIndex`, `outputPath`, `renderUri`, `thumbnailSource`, `cropStatus`, and notes.
  - `MainActivityViews.kt`, `MainActivity.kt`, and `activity_main.xml` are the app shell surfaces for current camera UI.
  - `MainActivityActionBinder` already dispatches session intents for UI actions.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionCallbacks.kt`
  - New or extracted file: `app/src/main/java/com/opencamera/app/DocumentBatchRailRenderModel.kt`
  - New or extracted file: `app/src/main/java/com/opencamera/app/DocumentBatchRailRenderer.kt`
  - New test: `app/src/test/java/com/opencamera/app/DocumentBatchRailRenderModelTest.kt`
- Non-goals:
  - Do not implement direct drag-sort in the rail.
  - Do not physically delete saved media.
  - Do not decode full-size JPEGs on the UI thread.

## Implementation Scope

- Add a pure render model for the side rail.
- Add a visible side rail view in `activity_main.xml`.
- Bind the rail in `MainActivityViews`.
- Render count, thumbnails/placeholders, latest item highlight, crop/status label or badge, and remove action.
- Add an organizer entry point, preferably the rail header/count.
- Keep rail position offset UI-ephemeral only; batch content and order remain session-owned.
- Add tests for render model visibility, count, ordering, latest item, status labels, and placeholder fallback.

## Suggested Render Model

```kotlin
internal data class DocumentBatchRailRenderModel(
    val visible: Boolean,
    val countText: String,
    val organizeEnabled: Boolean,
    val latestItemId: String?,
    val items: List<DocumentBatchRailItemRenderModel>
)

internal data class DocumentBatchRailItemRenderModel(
    val itemId: String,
    val pageNumber: Int,
    val renderUri: String?,
    val isLatest: Boolean,
    val statusLabel: String?,
    val removeContentDescription: String
)
```

`visible` should be true only when:

- `state.activeMode == ModeId.DOCUMENT`
- `state.presentation.documentBatch.status == DocumentBatchStatus.ACTIVE`
- `documentBatch.items` is not empty, unless product wants an empty count rail. Prefer hidden when empty for V2.

## Steps

1. Create `DocumentBatchRailRenderModel` and a pure builder function.
2. Add `DocumentBatchRailRenderModelTest`:
   - hidden outside document mode;
   - hidden for inactive/empty batch;
   - visible for active document batch with items;
   - count text equals item count;
   - item order follows `documentBatch.items`;
   - latest item is marked;
   - crop status labels match existing text resolver;
   - item without render URI gets a placeholder-safe model.
3. Add rail views to `activity_main.xml`.
   - Constrain to the right side of preview content.
   - Keep it above the bottom cockpit.
   - Use compact width and translucent background.
   - Include a count/header and vertical item list.
4. Bind views in `MainActivityViews`.
5. Implement `DocumentBatchRailRenderer`.
   - Use small thumbnail `ImageView`s.
   - Use `setImageURI` for `renderUri` only if URI/path is present.
   - Clear stale child views on every render before rebuilding the rail list.
6. Update `MainActivity.render(state)` to build and render the rail model.
7. Update `MainActivityActionBinder`:
   - rail header/count click -> `CockpitPanelCommand.ToggleDocumentBatchOrganizer`;
   - remove item click -> `SessionIntent.DocumentBatchRemoveItem(itemId)`;
   - rail drag handle -> local `translationY` or stored UI-only offset.
8. Ensure preview tap-to-focus still works outside rail bounds.
9. Add localized strings for content descriptions if missing.

## Acceptance Criteria

- After two completed document shots, the rail shows count `2` and two ordered items.
- The newest page is visually distinguishable via model state and a simple UI treatment.
- Remove action dispatches `DocumentBatchRemoveItem` and does not modify UI-local lists directly.
- Header/count opens the organizer route.
- The rail is hidden outside document mode.
- No code stores a separate document batch list in `MainActivity` or renderer.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*document batch*"
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- This package requires Codex/user visual QA after implementation. Text-only tests can prove visibility/order/action wiring, but not whether the rail obstructs the document too much.
- Keep rail UI compact. Do not add cards inside cards or a full review panel in the shooting view.
- If image loading becomes slow, use placeholders first and defer better thumbnail materialization to a separate app-shell image-loader package.

