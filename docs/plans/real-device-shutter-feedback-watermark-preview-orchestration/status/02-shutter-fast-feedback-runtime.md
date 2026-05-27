# 02-shutter-fast-feedback-runtime Status

- **Status**: completed
- **Agent**: shutter-feedback-watermark-02-shutter-fast-feedback-runtime
- **Branch**: `agent/shutter-feedback-watermark/02-shutter-fast-feedback-runtime`
- **Worktree**: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-02-shutter-fast-feedback-runtime`
- **Base commit**: `a8d621fb80e808254e9c301429571278309434ee`
- **Commit hash**: `fbb2c55`
- **Changed files**: `SessionContracts.kt`, `DefaultCameraSession.kt`, `CaptureRecordingSessionProcessor.kt`, `SessionCockpitRenderModel.kt`, `CameraXCaptureAdapter.kt`, `SessionCockpitRenderModelTest.kt`, `CaptureRecordingSessionProcessorTest.kt`
- **Verification**: CaptureRecordingSessionProcessorTest PASS, CameraSessionCoordinatorTest PASS, SessionCockpitRenderModelTest PASS, SessionUiRenderModelTest PASS, SessionDiagnosticsTest PASS, assembleDebug PASS
- **Evidence**: see below
- **Risks / follow-up**: see below

---

## Changes Summary

### P1 Fix: Immediate Press Feedback

**Root cause**: `shutterVisualState()` never returned `PHOTO_PRESSED`. The `ShutterVisualDrawable` had a complete 80ms scale-down animation ready but it was dead code.

**Fix**: Added `PHOTO_PRESSED` return path in `shutterVisualState()` when `captureStatus == REQUESTED && activeShot?.mediaType == PHOTO`. This state occurs between `submitCaptureStrategy` (sets `activeShot` + `captureStatus=REQUESTED`) and `handleShotStarted` (sets `captureStatus=SAVING`), giving immediate visual feedback before CameraX dispatch completes.

**Timing instrumentation**: Added `shutterPressedAtElapsedMillis` field to `SessionState` (set via `System.nanoTime()` in `DefaultCameraSession` when `ShutterPressed` is dispatched, cleared in `CaptureRecordingSessionProcessor.handleShotStarted`). Added `capture.shutter.pressed` trace event. Package 05 can compute P1 = `ShotStarted.timestamp - shutterPressedAtElapsedMillis`.

### P6 Fix: Postprocess Off Main Thread

**Root cause**: `emitShotCompleted` for `STILL_CAPTURE` ran on `adapterScope` (`Dispatchers.Main.immediate`), competing with UI during capture feedback.

**Fix**: Changed the still-capture postprocess launch from `adapterScope.launch` to `CoroutineScope(SupervisorJob() + Dispatchers.Default).launch`, moving `guardedPostProcess` off the main thread. Video and multi-frame paths remain unchanged (conservative).

### Capture Safety Boundary

- Ordinary still capture: re-arms at `DATA_RECEIVED` (unchanged from V1)
- Multi-frame/live photo: keeps `activeShot` until `ShotCompleted` (unchanged)
- Recording: stop-recording semantics unchanged
- Preview recovering: blocked (unchanged)
- `PHOTO_PRESSED` only fires during the brief `REQUESTED` window before `ShotStarted`, so it cannot cause unsafe overlapping captures

## Test Evidence

| Test Suite | Result |
|---|---|
| `CaptureRecordingSessionProcessorTest` | PASS (all 30 tests) |
| `CameraSessionCoordinatorTest` | PASS |
| `SessionCockpitRenderModelTest` | PASS (including new PHOTO_PRESSED tests) |
| `SessionUiRenderModelTest` | PASS |
| `SessionDiagnosticsTest` | PASS |
| `app:assembleDebug` | PASS |

Note: 19 pre-existing failures in `DefaultCameraSessionTest` are unrelated to this package (confirmed on base branch before changes).

## Residual Real-Device Risks

1. **P1 actual latency on device**: The `PHOTO_PRESSED` animation (80ms scale-down) is wired but real-device touch-to-render latency depends on device frame scheduling. Needs real-device profiling.
2. **Postprocess thread safety**: Moving `emitShotCompleted` to `Dispatchers.Default` means `guardedPostProcess` runs concurrently with UI on a different thread. The existing `guardedPostProcess` uses `runCatching` and the result is emitted via `MutableSharedFlow`, which is thread-safe. No additional synchronization needed.
3. **P3 (hardware capture latency)**: Cannot be improved from app code. The UX target is P1+P2 < 100ms total perceived latency.
