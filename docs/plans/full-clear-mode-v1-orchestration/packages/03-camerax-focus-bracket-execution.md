# Package 03 - CameraX Focus Bracket Execution

## Goal

Implement the app/device adapter execution path for focus-bracket still capture using the package 02 contracts.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/**`
- `app/src/test/java/com/opencamera/app/camera/**`
- `core/device/src/main/kotlin/com/opencamera/core/device/**` only for small contract adjustments needed by adapter execution
- `core/device/src/test/kotlin/com/opencamera/core/device/**`
- `docs/plans/full-clear-mode-v1-orchestration/v1-implementation-design.md`

## Forbidden Paths

- Mode plugin product definition except compile fixes.
- UI render model changes except compile fixes.
- Coordinator files outside `status/03-camerax-focus-bracket-execution.md`.

## Required Work

1. Teach CameraX capture execution to honor a per-frame focus bracket plan when the device request provides one.
2. Apply near/far focus through existing Camera2 manual focus interop or a clearly diagnosed degraded rebind path.
3. Preserve temporary frame artifacts for downstream focus-stack processing.
4. Record diagnostics for bracket frame count, frame roles, focus distances, execution mode, and unsupported/degraded outcomes.
5. Keep ordinary Photo, Night, Live, and Video behavior unchanged.

## Acceptance Criteria

- Adapter tests prove bracket execution uses distinct frame roles and diagnostics.
- If per-frame focus cannot be applied, the result is marked degraded/unsupported with a recovery hint.
- Temporary outputs are cleaned or passed through according to the media pipeline contract.
- No UI or mode layer owns device execution.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Execution summary: supported path, degraded path, unsupported path.
- Changed files.
- Verification command results.
- Remaining device-only uncertainties.

