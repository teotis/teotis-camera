# Document Mode V2 Continuous Capture Handoff Index

## Package Goal

Upgrade `DOCUMENT` mode from a single-shot scan style into a continuous document capture workflow. The user-visible goal is: while taking a group of document photos, the user can keep shooting without a blocking review page, and can always confirm the captured page count, page content, and page order through a low-obstruction side rail.

## User Request

- Avoid algorithm-heavy document scanning as the primary value proposition.
- Do not force a post-shot review page after every capture.
- Prioritize continuous capture confirmation: users need to know pages were captured, count is correct, and order is correct.
- Add a semi-transparent movable side rail that shows thumbnails for the current document capture round.
- Support removing an item from the current round.
- Support reordering pages; direct drag is optional, and a different interaction is acceptable if safer.

## Verified Current Facts

- Planning home is `codex/agent_plans/`; keep all handoff docs discoverable from `codex/agent_plans/INDEX.md`.
- `DocumentModePlugin` already defines `ModeId.DOCUMENT`, scan-style cycling, document metadata tags, `Pictures/OpenCamera/Documents`, and `OpenCamera_DOC` output prefix.
- `DocumentAutoCropPostProcessor` already produces pipeline notes such as `document:auto-crop:applied`, `document:auto-crop:skipped:*`, and `document:auto-crop:failed:*`.
- `SessionPresentationState` already owns saved-media thumbnail state and `pendingCaptureFeedback`; existing plans explicitly forbid UI-local media ownership.
- `MainActivityActionBinder` already routes the bottom thumbnail click to gallery opening, and `MainActivity.render()` already prioritizes pending feedback over official saved media for the bottom thumbnail.
- Existing related plans establish saved-media thumbnail precedence and gallery/open-target boundaries:
  - [Media Result, Thumbnail, Latency, and Dev Log](./2026-05-21-media-result-thumbnail-latency-devlog.md)
  - [Zero-Latency Thumbnail Feedback](./2026-05-22-zero-latency-thumbnail-feedback.md)
  - [Interaction Routing and Hit Targets](./2026-05-21-interaction-routing-and-hit-targets.md)
  - [Video Saved-Media Thumbnail And Gallery Preload](./2026-05-24-video-thumbnail-and-gallery-preload.md)

## Package Documents

| Document | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Document Batch Session Contracts](./2026-05-25-document-batch-session-contracts.md) | Text/code agent | implemented | Session-owned current document batch state and remove/reorder intents exist; focused document-batch session tests pass. |
| [Document Batch Capture Integration](./2026-05-25-document-batch-capture-integration.md) | Text/code agent | implemented | Completed document shots append to the batch with profileId, scanMode, and cropStatus parsed from metadata/pipeline notes. |
| [Document Batch Side Rail UI](./2026-05-25-document-batch-side-rail-ui.md) | Text/code agent + Codex visual QA | implemented | Rail render model, tests, layout, view binding, renderer, and action wiring all exist. Codex visual QA pending. |
| [Document Batch Organize And Acceptance](./2026-05-25-document-batch-organize-acceptance.md) | Text/code agent + Codex/user QA | blocked | Organizer render model exists, but there is no visible organizer UI/action wiring that satisfies user-facing reorder acceptance. |

## Recommended Execution Order

1. Implement session contracts first. No UI should own the batch list.
2. Implement capture integration second. The side rail needs real completed document entries.
3. Implement side rail UI third. It should only render session state and dispatch intents.
4. Implement organize/reorder fourth. Direct drag in the rail is optional; use a safer organizer page or up/down commands first.
5. Run final QA after all packages land.

## Dependency Notes

- Do not start side rail UI before the session-owned batch contract exists.
- Do not start organizer reorder before batch item identity and ordering are stable.
- Capture integration and side rail UI touch overlapping app/session files; if parallelized, assign one integrator for `MainActivity.kt`, `MainActivityViews.kt`, and `activity_main.xml`.
- This package does not require OCR, PDF export, perspective correction, shadow removal, handwritten enhancement, signature, translation, or formula recognition.

## Product Definition For V2

V2 is a continuous capture confirmation workflow:

- Entering document mode starts or resumes the current in-memory document batch for this camera session.
- Each successful document photo append appears in the side rail.
- The rail shows count, thumbnails, and order.
- The latest captured page briefly highlights.
- Removing an item removes it from the current batch. Physical MediaStore deletion is not required in this V2 unless the app already owns a safe deletion path; label the UI as "Remove from batch" if the saved file remains in gallery.
- Reordering updates the current batch order only. It does not rewrite saved JPEG timestamps or gallery order.
- Leaving document mode may keep the batch available until the user finishes, clears it, or the process/session ends; do not silently delete saved media.

## Codex-Retained Work

- Visual judgment of whether the side rail obstructs document framing too much.
- Real-device screenshot/recording review for thumbnail readability, drag affordance, and one-handed use.
- Final product acceptance after implementation agents report completion.

## Non-Goals

- A blocking review page after every capture.
- OCR or searchable PDF.
- Automatic perspective correction.
- Auto shadow removal or handwriting cleanup.
- Full document editor, signature, translation, formula recognition, or format conversion.
- A second session kernel inside UI, coordinator, or mode plugin.

## Package-Level Verification

Run focused verification as each package lands, then run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.DocumentAutoCropPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

If any named test class does not exist yet, the implementation package that introduces the behavior must create it or adjust the command to the actual test class name and record the reason in this index.

## Completion Notes

- Status: `partially implemented` after work on 2026-05-25.
- Validation summary:
  - Session-side batch contracts landed in `core/session/src/main/kotlin/com/opencamera/core/session/DocumentBatchContracts.kt`.
  - `SessionPresentationState.documentBatch` and `SessionIntent.DocumentBatch*` landed.
  - Focused document-batch session tests pass.
  - Capture integration now parses `profileId`, `scanMode`, and `cropStatus` from `ShotResult.metadata.customTags` and `pipelineNotes` via `documentCropStatusFrom()`.
  - `DocumentBatchRailRenderModel` and `DocumentBatchRailRenderModelTest` (11 tests) exist and pass.
  - Side rail XML layout (`documentBatchRail`, `documentBatchRailHeader`, `documentBatchRailList`) exists in `activity_main.xml`.
  - `DocumentBatchRailViews` data class and binding exist in `MainActivityViews.kt`.
  - `DocumentBatchRailRenderer` renders the rail from the render model, dispatches `DocumentBatchRemoveItem` on item click.
  - Header click dispatches `ToggleDocumentBatchOrganizer` to open the organizer panel.
  - Organizer render-model tests pass, but the organizer is not connected to an actual visible UI flow/action surface in the inspected app code.
  - `:app:assembleDebug` passes.
  - `rtk ./scripts/verify_stage_7_observability.sh` is blocked by a non-document-side `:core:device:compileTestKotlin` failure in `DefaultDeviceShotRequestTranslatorTest.kt` (`stillCaptureQuality` unresolved).
- Verification evidence:
  - Passed: `./gradlew :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.*document*"`
  - Passed: `./gradlew :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest`
  - Passed: `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchRailRenderModelTest`
  - Passed: `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest`
  - Passed: `./gradlew :app:testDebugUnitTest --tests com.opencamera.app.camera.DocumentAutoCropPostProcessorTest`
  - Passed: `./gradlew :app:assembleDebug`

## Follow-Up Required

1. Complete organizer UI/action wiring for move up/down and remove from batch (organizer panel route exists but no visible UI).
2. Codex visual QA: rail obstruction, thumbnail readability, one-handed use, latest-capture highlight.
3. Fix or isolate the unrelated `DefaultDeviceShotRequestTranslatorTest.kt` `stillCaptureQuality` compile failure before claiming Stage 7 validation is green.
