# 01-camerax-camera2-signal-feasibility Status

## State

`completed`

## Summary

Research confirmed that CameraX 1.3.4 can provide an earlier signal via Camera2Interop session capture callback (`onCaptureCompleted`), firing before JPEG encode+save. Recommended two-tier V1 strategy: (1) move shutter sound from `activeShot`-appearance to `DataReceived` (zero risk), (2) add `CaptureFrameAcquired` observability event via Camera2Interop (future readiness boundary).

## Evidence

- **Output**: `docs/plans/capture-readiness-sound-timing-orchestration/outputs/01-camerax-camera2-signal-feasibility.md` — full research report (10 sections, 406 lines)
- **Commit**: `9f337188` — `feat: CameraX/Camera2 signal feasibility research`
- **Verification**: `rg` search + `javap` API inspection + `./gradlew :app:compileDebugKotlin` passed
- **Key findings**:
  - CameraX `takePicture(OutputFileOptions, ...)` maps `onImageSaved` -> `DataReceived`
  - Camera2Interop `setSessionCaptureCallback()` provides `onCaptureCompleted` ~50-200ms earlier
  - Private `takePictureInternal` supports dual callbacks but inaccessible
  - Rejected: OnImageCapturedCallback w/ manual save (risky for V1), raw Camera2 pipeline (overkill), reflection (fragile)
  - Recommended naming: `CaptureFrameAcquired` for new Camera2-level event
  - Safety rule: `DataReceived` stays as re-arm gate; `CaptureFrameAcquired` is observability-only in V1
- **Integration**: Merged into `agent/capture-readiness-sound/integration` at `b3f26c3f`, then to `main` via finalize

## Artifacts

- `docs/plans/capture-readiness-sound-timing-orchestration/outputs/01-camerax-camera2-signal-feasibility.md`
