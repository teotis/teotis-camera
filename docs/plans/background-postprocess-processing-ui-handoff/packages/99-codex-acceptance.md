# Codex Acceptance - Background Postprocess Processing UI

## Goal

After the implementation package lands, validate that the app has a truthful and user-visible processing state for slow background postprocessing, without overclaiming real-device behavior.

## Context

- Original request: slow postprocessing should show a processing UI state and remind users not to exit while the background task is unfinished.
- Contract source: `docs/plans/background-postprocess-processing-ui-handoff/INDEX.md` and `packages/01-background-postprocess-processing-ui.md`.
- Codex-retained work:
  - Compare delivered code against every acceptance criterion.
  - Inspect render-model behavior and UI copy quality.
  - Run or review focused verification.
  - Perform real-device UX acceptance only if a device is available.

## Steps

1. Re-open the index and package doc.
2. Check `git status --short` and changed file scope.
3. Inspect session lifecycle changes:
   - pending processing state is created when finalization is pending;
   - pending processing state clears on success/failure;
   - shutter re-arm ownership remains in session policy.
4. Inspect app UI changes:
   - copy explicitly warns not to exit;
   - warning is visible during pending postprocess;
   - warning is not modal or visually disruptive;
   - settings/config locks remain truthful while finalization is pending.
5. Run focused verification when feasible.
6. If a real device is available, install the debug APK and smoke-test a slow postprocess case such as watermark, Color Lab, portrait/document, or Live.

## Acceptance Criteria

- Every acceptance criterion from package `01` is met or explicitly marked unmet.
- Verification output includes focused session tests and focused app render-model tests.
- If `:app:assembleDebug` is not run, the reason is recorded.
- Real-device UX is not marked PASS unless device evidence exists.
- Any stale-warning, no-warning, or shutter/config regression is reported as a gap before any summary.

## Verification Commands

Run from the implementation branch/worktree or integration checkout:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

For main workspace verification, use direct Gradle only if running from `/Volumes/Extreme_SSD/project/open_camera`:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- `SessionCockpitRenderModelTest` may not exist before implementation. If the agent keeps tests in `SessionUiRenderModelTest`, validate the same behavior there.
- Real-device testing is especially valuable because the warning must be readable on the actual camera preview, but local tests are still required for lifecycle correctness.
