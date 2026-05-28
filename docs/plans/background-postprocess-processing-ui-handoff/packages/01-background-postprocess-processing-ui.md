# Background Postprocess Processing UI

## Goal

When photo postprocessing continues after the frame has been received, show a clear processing state in the camera UI and warn the user not to exit before completion. The user-visible result should be: the shutter may be ready again for ordinary still capture, but the previous photo is explicitly marked as still being processed until final `ShotCompleted` or failure/degraded completion arrives.

## Context

- User request: the project's postprocessing is slow; if a background task has not finished, show a processing UI state and remind the user not to exit.
- Verified facts:
  - `CaptureStatus.DATA_RECEIVED` and `CaptureStatus.SAVING` already exist.
  - `SessionCockpitRenderModel.shutterDisabledReason(...)` already allows ordinary re-armed shutter behavior when `activeShot == null` and finalization is still pending.
  - `shutterVisualState(...)` can render `BACKGROUND_SAVING`.
  - `primaryStatusRenderModel(...)` currently renders generic enum-like status text; it does not communicate "processing, do not exit."
  - Slow postprocess paths include Color Lab, watermark, portrait/document/scene-mask rendering, frame ratio/selfie mirror, and Live/sidecar related finalization.
- Relevant files:
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh/strings.xml` if present
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` if present or appropriate
- Non-goals:
  - Do not change camera runtime ownership.
  - Do not make special captures re-arm earlier.
  - Do not add notification/background-service behavior in this package unless existing app architecture already has a narrow hook.
  - Do not claim real-device UX pass without device evidence.

## Implementation Scope

- Add a small session-owned representation for pending finalization, or reuse existing session state only if tests prove it is unambiguous.
- Render a visible UI hint while pending finalization is active. Suggested copy:
  - English: `Processing photo. Please keep OpenCamera open.`
  - Chinese: `照片处理中，请勿退出 OpenCamera。`
- Clear the hint on final `ShotCompleted` or failure/degraded completion.
- Keep shutter behavior honest:
  - Ordinary still capture may stay clickable if the session policy has released `activeShot`.
  - Capture configuration/settings that would alter the in-flight postprocess may remain blocked.
  - Conservative capture kinds remain blocked until final completion.
- Add focused tests for session state and render model behavior.

## Suggested Design

Prefer a small presentation contract over inferring from raw enum strings everywhere:

```kotlin
data class PendingPostprocessUiState(
    val shotId: String,
    val mediaType: MediaType,
    val message: String,
    val warnBeforeExit: Boolean = true
)
```

This can live in `SessionPresentationState` or as a focused render model derived from session state. The important invariant is testability:

- Created when `DataReceived` means final media postprocess is still pending.
- Remains visible during `SAVING` / `DATA_RECEIVED` while final result is not complete.
- Removed when final result is completed or failed.
- Does not itself decide shutter re-arm; session capture policy still owns that.

If the existing `SessionState` already carries enough information after recent merges, it is acceptable to implement this as a pure render-model helper first, but tests must prove it cannot show stale warnings after completion.

## Steps

1. Inspect current `CaptureRecordingSessionProcessor.handleDataReceived(...)`, `handleShotCompleted(...)`, and failure paths.
2. Decide whether the pending processing state belongs in `SessionPresentationState` or a focused app render model derived from `captureStatus` plus `activeShot`.
3. Add or update tests that first fail for the missing UI warning.
4. Implement the minimal session/render plumbing.
5. Add localized strings through `AppTextResolver` and Android resources.
6. Render the hint in the existing cockpit/status surface. Keep it compact and non-modal; it should be noticeable but should not block the camera preview.
7. Preserve existing disabled reason behavior for settings/config controls.
8. Run focused verification.

## Acceptance Criteria

- A test proves the processing warning appears after `DataReceived` while final `ShotCompleted` has not arrived.
- A test proves the warning clears after `ShotCompleted`.
- A test proves failure/degraded completion clears the pending warning and still surfaces the existing failure/degraded result path.
- A render-model test proves the visible copy includes an explicit do-not-exit warning, not only `Saving` or `Data received`.
- A test proves the shutter can remain enabled for ordinary re-armed still capture while the processing warning is visible.
- A test proves conservative active photo capture still blocks shutter/config controls before re-arm.
- Existing recording start/stop/save status tests continue to pass.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

If `SessionCockpitRenderModelTest` does not exist, either add it if it keeps tests cleaner, or place focused tests in `SessionUiRenderModelTest` and record that choice in the evidence.

## Package ID

`01-background-postprocess-processing-ui`

## File Ownership

- `01-background-postprocess-processing-ui` owns:
  - `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
  - `app/src/main/res/values*/strings.xml`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/**`
- `core/session/src/test/kotlin/com/opencamera/core/session/**`
- `app/src/main/java/com/opencamera/app/**`
- `app/src/main/res/values*/strings.xml`
- `app/src/test/java/com/opencamera/app/**`
- `codex/documentation.md` only if the implementation is meaningful and verified

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` unless a compile-time contract mismatch proves the adapter must send an already-defined event.
- `core/device/**` unless a compile-time contract mismatch proves the device event contract must change.
- `feature/**` mode plugins.
- `docs/plans/shutter-data-boundary-v1-orchestration/**`
- Broad UI redesign or new top-level navigation.

## Dependencies

- Depends on: none.

## Parallel Safety

- caution
- Reason: this touches shared session and cockpit render-model files. Do not run in parallel with another package editing shutter lifecycle, session capture state, or cockpit status UI.

## Expected Evidence Pack

- [ ] working directory recorded
- [ ] branch name recorded if changed
- [ ] git status clean or unrelated dirty files explained
- [ ] git diff --stat captured
- [ ] changed files listed
- [ ] verification commands run
- [ ] test results summarized
- [ ] commit hash / PR link if applicable
- [ ] unresolved risks noted
- [ ] only allowed paths touched verified

## Risks And Notes

- Avoid stale warnings. The user must not see "please keep app open" after final success/failure is already handled.
- Avoid making the warning a modal blocker; it is a safety/status affordance, not a new capture gate.
- If Android process-death protection is desired later, that is a separate product/architecture package involving WorkManager/foreground service policy, notification UX, and scoped storage guarantees.
