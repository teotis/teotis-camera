# 04-document-organizer-compact-close

## Goal

Repair Document mode organizer UX so it no longer occupies nearly the whole preview and a single tap on close actually closes it. The organizer should support the user need: quick review/reorder/remove while staying visually subordinate to the live preview.

## User Symptoms Covered

- Issue 9: Document mode adjustment/organizer panel occupies nearly the whole preview and is not an appropriate interaction design.
- Issue 9: single tap on close settings button is ineffective.

## Dependencies

- Depends on `02-cockpit-layout-mode-entry-density` because organizer placement must respect the final top/preview/bottom cockpit constraints.

## Allowed Paths

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/java/com/opencamera/app/DocumentBatchOrganizerRenderer.kt`
- `app/src/main/java/com/opencamera/app/DocumentBatchRailRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/DocumentBatchOrganizerRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CockpitPanelRouterTest.kt`
- `app/src/test/java/com/opencamera/app/DocumentBatchRailRenderModelTest.kt`

## Forbidden Paths

- Do not restart the old algorithm-heavy scanner direction; keep the conservative continuous capture / review flow.
- Do not remove move/remove actions.
- Do not make close a toggle if an explicit dismiss command is available or can be added safely.
- Do not cover the shutter, mode track, zoom slider, or top actions.
- Do not edit coordinator files except `status/04-document-organizer-compact-close.md` and the matching `state.tsv` row.

## Required Investigation

1. Inspect the actual current organizer wiring. Current evidence suggests `DocumentBatchOrganizerRenderer`, route visibility, and move/remove binding exist, but the visual surface is too large.
2. Replace the giant centered panel with a compact organizer surface:
   - acceptable directions include a bottom/side compact sheet, constrained-height list, or thumbnail rail expansion;
   - show only the controls needed for page count, thumbnails, move up/down, remove, and close;
   - avoid filling empty preview area.
3. Change close behavior to explicit dismiss:
   - preferably use `CockpitPanelCommand.DismissAll` or an explicit `CloseDocumentBatchOrganizer`;
   - avoid `ToggleDocumentBatchOrganizer` for the close button.
4. Add or update router tests so one close command from organizer reaches `None`.

## Acceptance Criteria

- Organizer panel height/width is constrained and does not cover almost the full preview.
- Empty organizer state (`0 页`) is compact and useful, not a huge modal.
- Existing page rows remain usable when there are multiple pages.
- One tap on close/dismiss hides the organizer.
- Document rail header can still open the organizer when a batch exists.
- Tests cover route close behavior and render-model visibility.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Test output summaries.
- Short before/after note describing occupied region and close route.
- Residual real-device screenshot checks required.

## Unlock Condition

Mark completed only after close behavior has focused test evidence and assemble passes.
