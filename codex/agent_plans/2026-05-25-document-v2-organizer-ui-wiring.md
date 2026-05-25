# Document V2 Organizer UI Wiring

## Goal

Turn the existing `DocumentBatchOrganizerRenderModel` into a reachable user-facing organizer so users can remove pages and adjust order with safe up/down controls.

## Context

- Current validation finding: `DocumentBatchOrganizerRenderModel` and tests exist, `CockpitPanelRoute.DocumentBatchOrganizer` exists, `CockpitPanelCommand.ToggleDocumentBatchOrganizer` exists, and the document side rail header dispatches that command.
- Remaining gap: `MainActivityRenderer.renderPanelVisibility()` has no document-organizer panel visibility branch, and no `DocumentBatchOrganizerRenderer` / organizer view binding was found. The route can be reached, but the user still does not get a visible organizer surface with move/remove controls.
- The original V2 plan recommended a safer organizer instead of direct drag-sort in the shooting rail.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/DocumentBatchOrganizerRenderModelTest.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - Suggested new file: `app/src/main/java/com/opencamera/app/DocumentBatchOrganizerRenderer.kt`
- Non-goals:
  - Do not add drag-sort in this repair pass.
  - Do not physically delete saved images.
  - Do not create a persistent document project store.

## Implementation Scope

- Add an organizer panel/overlay in the existing panel system.
- Render the existing organizer model when `activePanelRoute is CockpitPanelRoute.DocumentBatchOrganizer`.
- Add item buttons:
  - remove from batch;
  - move up;
  - move down.
- Wire buttons to session intents:
  - `DocumentBatchRemoveItem(itemId)`
  - `DocumentBatchMoveItem(itemId, UP)`
  - `DocumentBatchMoveItem(itemId, DOWN)`
- Close organizer via existing panel dismiss/back behavior.
- Preserve the side rail header route trigger through `ToggleDocumentBatchOrganizer`.

## Steps

1. Inspect current panel renderer patterns:
   - `SettingsPanelRenderer`
   - `FilterLabPanelRenderer`
   - `DevConsoleRenderer`
   - `MainActivityRenderer`
2. Add a document organizer container to `activity_main.xml` or reuse a panel container if the architecture supports dynamic panels.
3. Bind organizer views in `MainActivityViews`.
4. Create `DocumentBatchOrganizerRenderer`.
   - Render title and count.
   - Render each item with page number, thumbnail/placeholder, status label, remove, up, down.
   - Disable or hide up/down based on `canMoveUp/canMoveDown`.
5. Update `MainActivity.render(state)` or `MainActivityRenderer` to:
   - compute `documentBatchOrganizerRenderModel(state, text)`;
   - render it only for the organizer route;
   - hide it otherwise.
6. Update `MainActivityActionBinder` or renderer callbacks:
   - remove -> dispatch `SessionIntent.DocumentBatchRemoveItem(itemId)`;
   - move up/down -> dispatch `SessionIntent.DocumentBatchMoveItem(itemId, direction)`.
7. Add action wiring tests if current architecture supports callback-level tests. If not, add render-model tests that prove button availability and a small binder/renderer test around dispatch callbacks.
8. Ensure `AndroidBack` continues to close organizer through existing `CockpitPanelRouter` behavior.

## Acceptance Criteria

- User can open organizer from the document side rail header/count.
- Opening the organizer route makes a visible organizer panel/overlay appear.
- Organizer displays current pages in batch order.
- First page cannot move up; last page cannot move down.
- Tapping move up/down dispatches the correct session intent with the correct item id.
- Tapping remove dispatches `DocumentBatchRemoveItem`, not physical MediaStore deletion.
- Closing organizer returns to shooting view and preserves updated batch order.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*document batch*"
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Do not make the organizer the default post-shot experience. It should be opened only when the user asks for it.
- If a full panel is too large for phone shooting, use an overlay only after preserving existing panel layering and dismiss behavior.
- The wording should be "Remove from batch" / `移出本轮`, not "Delete photo", unless a separate MediaStore delete package exists.

## Validation After External-Agent Delivery

Status: `blocked`.

What is present:

- `DocumentBatchOrganizerRenderModel` and `DocumentBatchOrganizerRenderModelTest`.
- `CockpitPanelRoute.DocumentBatchOrganizer`.
- `CockpitPanelCommand.ToggleDocumentBatchOrganizer`.
- Rail header click dispatches `ToggleDocumentBatchOrganizer`.
- Session intents and session-side handlers for remove and move already exist.

What is still missing:

- No `DocumentBatchOrganizerRenderer` was found.
- No organizer panel/container was found in `activity_main.xml`.
- No organizer view binding was found in `MainActivityViews`.
- `MainActivityRenderer.renderPanelVisibility()` does not show/hide an organizer panel when the active route is `DocumentBatchOrganizer`.
- No visible organizer move/remove button dispatch wiring was found.

Validation commands run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Result: both passed, but they do not prove the organizer UI is visible. The acceptance item "Opening the organizer route makes a visible organizer panel/overlay appear" is still unmet.
