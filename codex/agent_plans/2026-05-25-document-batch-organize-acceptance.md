# Document Batch Organize And Acceptance

## Goal

Provide a safe way to adjust page order and validate the full Document Mode V2 continuous capture experience after session, capture, and side rail packages land.

## Context

- User request: The current round should support deleting and changing image order. Dragging is acceptable if feasible, but a different interaction is acceptable.
- Verified facts:
  - The side rail should minimize obstruction during shooting.
  - Direct drag-sort in the side rail can conflict with preview gestures and rail movement.
  - Session state must own the batch list and order.
- Relevant files:
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
  - `app/src/main/java/com/opencamera/app/CockpitPanelReducer.kt`
  - New suggested file: `app/src/main/java/com/opencamera/app/DocumentBatchOrganizerRenderModel.kt`
  - New suggested test: `app/src/test/java/com/opencamera/app/DocumentBatchOrganizerRenderModelTest.kt`
- Non-goals:
  - Do not build PDF export in this package.
  - Do not physically delete gallery files.
  - Do not implement complex animated drag-sort unless the simple organizer is already stable.

## Recommended Interaction

Use a safer organizer panel for V2:

- The side rail remains lightweight during shooting.
- Tapping the rail header/count opens `整理本轮` / `Organize`.
- The organizer shows pages in current order with page numbers.
- Each item supports:
  - remove from batch;
  - move up;
  - move down;
  - optional larger preview.
- A future V2.1 can replace up/down controls with drag-sort after visual QA proves it does not conflict with camera gestures.

This meets the user goal without making the camera preview itself a dense editing surface.

## Implementation Scope

- Add a panel route or lightweight overlay for document batch organization.
- Render ordered items from session-owned `DocumentBatchState`.
- Dispatch reorder/remove intents back to session.
- Keep page order changes deterministic and testable.
- Add final acceptance checklist for Codex/user real-device validation.

## Steps

1. Inspect existing panel routing:
   - `CockpitPanelRoute.kt`
   - `CockpitPanelReducer.kt`
   - settings/style/color/dev console panel patterns.
2. Add a `DocumentBatchOrganizer` route if the panel system is suitable. If not, add a small overlay tied to document mode, but keep it app-shell UI only.
3. Add render model:

```kotlin
data class DocumentBatchOrganizerRenderModel(
    val visible: Boolean,
    val title: String,
    val countText: String,
    val items: List<DocumentBatchOrganizerItemRenderModel>
)
```

4. For each item, expose:
   - stable item id;
   - page number;
   - render URI or placeholder;
   - crop/enhancement status label;
   - `canMoveUp`;
   - `canMoveDown`.
5. Bind actions:
   - close organizer;
   - remove from batch;
   - move up/down via session intent;
   - optional tap to preview larger image.
6. Add tests:
   - organizer item order follows session batch order;
   - first item cannot move up;
   - last item cannot move down;
   - remove/move actions dispatch correct item ids;
   - outside document mode organizer is hidden or disabled.
7. Update any user-facing text to avoid claiming physical deletion:
   - prefer `Remove from batch`;
   - avoid `Delete photo` unless the implementation actually deletes the saved media file.
8. Run final verification commands.
9. After implementation lands, Codex/user performs visual QA:
   - rail obstruction on portrait phone viewport;
   - rail movement affordance;
   - thumbnail readability;
   - latest capture highlight;
   - count accuracy during rapid multi-shot;
   - organizer order after moves.

## Acceptance Criteria

- User can capture at least 5 document pages without a blocking review page.
- Side rail count reaches 5 and thumbnails appear in capture order.
- Removing page 3 from the current batch changes count to 4 and renumbers visible order.
- Moving the last page upward changes order in both organizer and side rail.
- Returning to capture view preserves the updated order.
- Shutter, tap-to-focus, mode switching, and bottom gallery thumbnail still behave according to existing tests.
- UI wording does not imply deleted gallery files unless physical deletion exists.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Manual QA

Use a real device if available:

1. Enter `Document`.
2. Capture five different paper pages or high-contrast substitutes.
3. Confirm no full-screen review page interrupts shooting.
4. Confirm the rail shows count and latest-page highlight.
5. Move the rail vertically and confirm it does not block the important document area.
6. Remove the third page from the current batch.
7. Open organizer and move the last page upward.
8. Return to capture view and confirm side rail reflects the new order.
9. Tap the bottom gallery thumbnail and confirm it still opens the latest saved media, not a rail placeholder.

## Risks And Notes

- This package intentionally avoids direct drag-sort in the side rail. If the team later wants drag-sort, build it only after current V2 passes visual QA.
- Multimodal visual judgment is required before calling the side rail polished. Text-only agents should not claim final visual quality.
- The current batch is an in-session workflow aid, not a durable document project store. Persistent document projects and PDF export should be separate future packages.

