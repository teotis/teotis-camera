# Package 05 - Shutter Sound And Visible Rearm

## Objective

Move shutter sound to the explicit capture-readiness boundary and make the button's visual state match the user's mental model: loading until readiness, sound at readiness, subtle background saving after readiness.

## Dependencies

- `03-capture-readiness-contract`
- `04-adapter-earliest-ready-signal`

Read their status files before editing.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- New focused app tests under `app/src/test/java/com/opencamera/app/`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` only for readiness marker regressions
- `docs/plans/capture-readiness-sound-timing-orchestration/status/05-shutter-sound-and-visible-rearm.md`
- Package scratch path printed by `scratch-path 05-shutter-sound-and-visible-rearm`

## Forbidden Paths

- CameraX platform callback implementation
- Device contract changes except tiny compile-followup from Package 03/04
- Other package status files
- `docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`

## Required Work

- Remove sound timing dependence on `state.activeShot` appearance.
- Play shutter sound once per ordinary photo readiness milestone when shutter sound setting is enabled.
- Do not play readiness sound for failed captures that never reach the readiness milestone.
- Keep immediate visual press feedback separate from readiness sound. If any press animation exists, it must not be described as "frame acquired".
- Ensure button disabled/enabled state and visual state are consistent:
  - loading/capture state before readiness,
  - ready or background-saving state after readiness,
  - disabled only for true unsafe states,
  - recording shutter remains stop-recording capable.
- Add tests for sound marker de-duplication and render state transitions where feasible.

## Acceptance Criteria

- Focused app tests pass.
- Manual status includes the exact state transition when sound now plays.
- No real-device PASS claim is made.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

In a worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Completion

Commit local changes, update coordinator status, then:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 05-shutter-sound-and-visible-rearm completed --commit <commit-sha> --verification "<commands/results>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 05-shutter-sound-and-visible-rearm
```
