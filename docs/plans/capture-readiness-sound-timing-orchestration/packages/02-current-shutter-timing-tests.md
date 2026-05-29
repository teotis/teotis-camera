# Package 02 - Current Shutter Timing Tests

## Objective

Characterize current behavior with focused tests so later packages can change shutter sound and re-arm timing without losing recording-stop semantics or special-capture safety.

## Allowed Paths

- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `docs/plans/capture-readiness-sound-timing-orchestration/outputs/02-current-shutter-timing-tests.md`
- `docs/plans/capture-readiness-sound-timing-orchestration/status/02-current-shutter-timing-tests.md`
- Package scratch path printed by `scratch-path 02-current-shutter-timing-tests`

## Forbidden Paths

- Runtime implementation files, unless an existing test helper cannot compile without a tiny test-only fixture adjustment
- Other package status files
- `docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`

## Required Work

- Add or confirm focused tests for ordinary still capture state after `DataReceived`:
  - `activeShot == null`,
  - shutter clickability is restored,
  - background saving visual state remains truthful if save/postprocess continues.
- Add or confirm tests that special capture kinds do not re-arm early:
  - Live photo,
  - multi-frame/night-like paths when represented in current contracts.
- Add or confirm tests that recording keeps shutter enabled as stop-recording control and is not governed by generic `captureDisabledReason()`.
- Document the current sound timing gap: app sound is tied to `activeShot` appearance, not readiness.

## Acceptance Criteria

- Focused tests pass.
- No runtime behavior changes are hidden in this package.
- Output document summarizes current expected baseline and the test names that protect it.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

In a worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

## Completion

Commit local changes, update coordinator status, then:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 02-current-shutter-timing-tests completed --commit <commit-sha> --verification "<commands/results>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 02-current-shutter-timing-tests
```
