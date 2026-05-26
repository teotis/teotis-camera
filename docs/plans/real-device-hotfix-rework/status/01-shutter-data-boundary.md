# Package Status: 01-shutter-data-boundary

- **Agent**: Claude Code (mimo-v2.5-pro)
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree
- Path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-data-boundary-v1`
- Branch: `fix/shutter-data-boundary-v1`

## Changes
- git status: clean
- git diff --stat: 5 files changed, 131 insertions(+)
- Changed files:
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`

## Data Boundary Decision

**Chosen boundary**: CameraX `OnImageSavedCallback.onImageSaved()` (file-saved boundary)

**Why**: CameraX 1.3.4 `ImageCapture.takePicture()` exposes two separate overloads:
1. `takePicture(Executor, OnImageCapturedCallback)` — fires at capture time, memory only, no file save
2. `takePicture(OutputFileOptions, Executor, OnImageSavedCallback)` — fires after file write, includes saved URI

There is NO combined overload (`OutputFileOptions` + `OnImageCapturedCallback`). Using the in-memory callback would require manual YUV→JPEG conversion and file I/O, losing the `OutputFileResults.savedUri` needed by `resolveOutputHandle()`. This would constitute a media pipeline redesign, which is out of scope.

`DataReceived` fires after file save but before `ShotCompleted` (which includes post-processing). The event ordering is: `onImageSaved` → `DataReceived` → post-processing → `ShotCompleted`.

## Verification
- Commands run:
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest` → BUILD SUCCESSFUL
  - `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest` → 42 tests, 4 failures (all pre-existing mode directory/track tests)
  - `rtk ./gradlew --no-daemon :app:assembleDebug` → BUILD SUCCESSFUL
- Test results:
  - CaptureRecordingSessionProcessorTest: all pass (including 5 new data boundary tests)
  - SessionCockpitRenderModelTest: 38 pass / 4 fail (pre-existing), including 6 new shutter visual tests

## Delivery
- Commit hash: `1275f00`
- PR link: none (local branch, not pushed)

## Self-Certification
- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks
- CameraX limitation: `DataReceived` fires after file save, not at raw data receipt. Documented in `CameraXCaptureAdapter.kt` with comment explaining the API constraint.
- `ShutterVisualDrawable` exists but is not wired into `CockpitSurfaceRenderer.renderShutter()` (which uses XML backgrounds). Loading visual is implemented via alpha dimming instead. Full drawable integration would require broader renderer refactoring.
