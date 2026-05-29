# Package 01 - CameraX/Camera2 Signal Feasibility

## Objective

Determine the earliest reliable milestone this project can receive from CameraX/Camera2 for ordinary still capture, and decide whether it is safe to move user readiness and shutter sound earlier than the current `OnImageSavedCallback -> DataReceived` boundary.

## Allowed Paths

- `docs/plans/capture-readiness-sound-timing-orchestration/outputs/01-camerax-camera2-signal-feasibility.md`
- `docs/plans/capture-readiness-sound-timing-orchestration/status/01-camerax-camera2-signal-feasibility.md`
- Package scratch path printed by `scratch-path 01-camerax-camera2-signal-feasibility`

## Forbidden Paths

- Runtime Kotlin source files
- Tests outside this plan
- Other package status files
- `docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`

## Required Investigation

Inspect local code and locally available dependency/API sources where possible. Do not rely on memory or marketing claims.

Questions to answer:

- With the current `ImageCapture.takePicture(OutputFileOptions, executor, OnImageSavedCallback)` path, what exact callback currently maps to `DataReceived`?
- Can CameraX expose a callback earlier than `onImageSaved` while still letting CameraX save the JPEG to the requested destination?
- Would `ImageCapture.OnImageCapturedCallback` require switching to an in-memory `ImageProxy` save path, and if so what ownership/risk does that create?
- Can `Camera2Interop` attach a Camera2 capture/session callback to ImageCapture in this project without destabilizing preview binding?
- Which Camera2 callback milestone best approximates "frame exposure/readout done" versus "JPEG encoded/saved"?
- What failure modes would make an earlier callback unsafe for enabling the next shutter press?
- What should be named `FrameAcquired`, `CaptureReadiness`, `CaptureCommitted`, or similar in local contracts?

## Acceptance Criteria

- Produce `outputs/01-camerax-camera2-signal-feasibility.md` with:
  - current callback chain in repo terms,
  - feasible earlier milestone options,
  - rejected options and why,
  - recommended V1 implementation path,
  - fallback if CameraX cannot provide a trustworthy earlier signal locally,
  - exact files/functions the implementation packages should touch.
- Status file records evidence, commands, and the recommendation.
- If the answer is "not safely earlier than current DataReceived", state that plainly and recommend moving sound to explicit `DataReceived/readiness` rather than pretending earlier hardware readiness exists.

## Verification Commands

```bash
rtk rg -n "takePicture|OnImageSavedCallback|OnImageCapturedCallback|Camera2Interop|CaptureCallback|DataReceived|maybePlayShutterSound" app/src/main/java core -S
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

In a worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:compileDebugKotlin
```

## Completion

Commit the research output if changed, update coordinator status, then:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 01-camerax-camera2-signal-feasibility completed --commit <commit-sha> --verification "<commands/results>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 01-camerax-camera2-signal-feasibility
```
