# 01-zoom-threshold-lens-switch Status

**Status**: completed

## Worktree And Branch

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-regression-20260527/01-zoom-threshold-lens-switch`
- Branch: `agent/real-device-ux-regression-20260527/01-zoom-threshold-lens-switch`
- Base commit: `36abefa`
- Original package commit: `60b2f1c`
- Mainline repair commit: `2e6a94e`

## Verification

- `rtk ./gradlew --no-daemon --no-parallel -Dorg.gradle.workers.max=1 -Pkotlin.incremental=false --rerun-tasks :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest`: PASSED, `BUILD SUCCESSFUL in 22s`, 43 tasks executed.
- `rtk ./gradlew --no-daemon --no-parallel -Dorg.gradle.workers.max=1 -Pkotlin.incremental=false --rerun-tasks :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest :app:assembleDebug`: PASSED, `BUILD SUCCESSFUL in 39s`, 87 tasks executed.

## Evidence

- The stale blocked status was valid for clean commit `60b2f1c`: it reproduced 19 `DefaultCameraSessionTest` failures.
- Current `main` had already advanced past `60b2f1c`, but the same 19 failures still reproduced before the repair.
- Commit `2e6a94e` restores the session still-capture contract required by the current tests: still quality/resolution state, output-size metadata propagation, low-light prompt availability, metering feedback lifetime, video elapsed display, and document-batch unknown-item ordering.
- The original 2x/5x zoom package remains present in history as `60b2f1c`; downstream packages should base on current `main` so they include `2e6a94e`.

## Risks / Residual Device QA

- Real-device verification for physical 2x/5x lens switching remains pending and belongs to `05-integration-visual-smoke-protocol`.
- Desktop verification proves the stale session blocker is resolved; it does not prove real-device visual lens switching behavior.
