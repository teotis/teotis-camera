# Document Mode V2 Repair Handoff Index

## Package Goal

Close the gaps found during validation of [Document Mode V2 Continuous Capture](./2026-05-25-document-mode-v2-continuous-capture-index.md). The target outcome is a user-visible continuous document capture workflow: while shooting document pages, the user sees a low-obstruction side rail with page count, ordered thumbnails, latest-page feedback, remove-from-batch, and a reachable organizer for reordering.

## User Request

The previous handoff was delivered by an external agent, but validation found it only partially implemented. Produce a repair package based on those validation gaps.

## Current Remaining Work After Recheck

- Session-side `DocumentBatchState`, session intents, remove/move helpers, and focused document-batch tests exist.
- Document capture metadata repair is now present: `CaptureRecordingSessionProcessor` parses `profile`, `scanMode`, and `document:auto-crop:*` notes into `DocumentBatchItem`.
- Side rail render model, layout surface, renderer, header click, and tests now exist.
- `CockpitPanelRoute.DocumentBatchOrganizer` and `CockpitPanelCommand.ToggleDocumentBatchOrganizer` exist, and the rail header dispatches the route command.
- The remaining document-mode implementation gap is the organizer UI surface itself: `MainActivityRenderer.renderPanelVisibility()` does not show a document organizer panel, and no `DocumentBatchOrganizerRenderer` / organizer view binding was found.
- The previously reported `DefaultDeviceShotRequestTranslatorTest.kt` still-quality compile blocker is no longer reproducible in the current workspace; focused device translator tests pass.

## Package Documents

| Document | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Document V2 Side Rail Completion](./2026-05-25-document-v2-side-rail-completion.md) | Text/code agent + Codex visual QA | implemented | Side rail render model, layout, renderer, header route trigger, and render-model tests are present; final visual QA remains separate. |
| [Document V2 Organizer UI Wiring](./2026-05-25-document-v2-organizer-ui-wiring.md) | Text/code agent + Codex/user QA | blocked | External-agent delivery still lacks a visible organizer panel/renderer/view binding; route and render model exist only. |
| [Document V2 Capture Metadata And Crop Status Repair](./2026-05-25-document-v2-capture-metadata-status-repair.md) | Text/code agent | validated | Batch item profile, scan mode, and crop status parsing are present and focused processor tests pass. |
| [Stage 7 Device Still Quality Test Repair](./2026-05-25-stage7-device-still-quality-test-repair.md) | Text/code agent | validated | Focused device translator test passes in the current workspace; no longer a remaining document V2 blocker. |

## Recommended Execution Order

1. Implement organizer UI wiring. This is now the only remaining code package in the document-mode repair set.
2. Run the document rail/organizer focused tests and assemble.
3. Run Stage 7 gate if broader release verification is needed; the focused device translator blocker is already cleared locally.
4. Codex/user performs final real-device visual QA after organizer UI lands.

## Related Plans

- Original package: [Document Mode V2 Continuous Capture](./2026-05-25-document-mode-v2-continuous-capture-index.md)
- Side rail scope: [Document Batch Side Rail UI](./2026-05-25-document-batch-side-rail-ui.md)
- Organizer scope: [Document Batch Organize And Acceptance](./2026-05-25-document-batch-organize-acceptance.md)
- Capture integration scope: [Document Batch Capture Integration](./2026-05-25-document-batch-capture-integration.md)

## Non-Goals

- OCR, searchable PDF, automatic perspective correction, shadow removal, handwriting cleanup, signature, translation, and formula recognition.
- Physical deletion of saved gallery files when removing a page from the current batch.
- Direct drag-sort in the side rail. V2 repair should prefer organizer move up/down; drag-sort can be future polish after visual QA.
- Rewriting the camera UI architecture or introducing a new hidden session/UI state owner.

## Final Acceptance Gate

The repair is not complete until all of these are true:

- `DocumentBatchRailRenderModelTest` exists and passes.
- `DocumentBatchOrganizerRenderModelTest` passes and visible organizer actions are wired.
- A document `ShotCompleted` with metadata and notes produces a batch item with non-null `profileId`, `scanMode`, and correct `DocumentBatchCropStatus`.
- Side rail is visible only in `DOCUMENT` mode with an active batch.
- Side rail count and item order match `SessionPresentationState.documentBatch.items`.
- Remove and move actions dispatch session intents, not UI-local mutations.
- Focused device translator tests remain passing; Stage 7 is not blocked by `stillCaptureQuality` compile errors in this workspace.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*document batch*"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.DocumentAutoCropPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Codex-Retained Work

- Visual judgment of rail obstruction and thumbnail readability.
- Real-device smoke: capture 5 pages, remove page 3, reorder last page upward, confirm side rail and organizer reflect order.
- Final acceptance after implementation agents report completion.

## Status

- `blocked` on 2026-05-25 after validating the external-agent delivery.
- Side rail and capture metadata repair are implemented enough to pass focused tests; focused device translator test passes.
- Organizer UI remains unaccepted: no `DocumentBatchOrganizerRenderer`, no organizer container/view binding in `activity_main.xml` / `MainActivityViews`, and no `MainActivityRenderer.renderPanelVisibility()` branch that makes a document organizer panel visible.

## Validation After External-Agent Delivery

Static evidence:

- `CockpitPanelRoute.DocumentBatchOrganizer` exists.
- `CockpitPanelCommand.ToggleDocumentBatchOrganizer` exists.
- `MainActivityActionBinder` wires the document rail header to `ToggleDocumentBatchOrganizer`.
- `SessionUiRenderModel.kt` contains `DocumentBatchOrganizerRenderModel`.
- `MainActivityRenderer.renderPanelVisibility()` still only toggles settings, filter/style/color, quick bubble, scrim, and related route alphas; it does not toggle a document organizer surface.
- `activity_main.xml` contains the document side rail, but no organizer panel/container id was found.
- `MainActivityViews` binds `DocumentBatchRailViews`, but no organizer views were found.
- No `DocumentBatchOrganizerRenderer` file was found.

Verification run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*document batch*"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Result: all four commands passed.

Global verification:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

Result: not completed. The script progressed through earlier modules, then stayed in `:core:session:test` without further output until Gradle daemons were stopped with `rtk ./gradlew --stop`. This is not counted as a document-mode pass or fail; it is recorded as an unfinished global gate.
