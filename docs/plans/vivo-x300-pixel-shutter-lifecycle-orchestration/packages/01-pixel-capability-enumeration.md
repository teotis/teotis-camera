# Package 01 — Pixel Capability Enumeration

## Package ID

`01-pixel-capability-enumeration`

## Purpose

Design a truthful still-output capability contract for devices like vivo X300 where hardware may support 48MP/50MP class capture but the current quick pixel surface only sees about 13MP. This package must determine whether the current limitation comes from Camera2 enumeration, CameraX binding, 4:3 filtering, preset mapping, or device-specific high-pixel modes.

## Current Evidence To Re-read

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
  - `StillCaptureOutputSize`
  - `DeviceCapabilities.availableStillCaptureOutputSizes`
  - `StillCaptureConfig.outputSize`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `detectCameraLensProfiles(...)`
  - `normalizeStillCaptureOutputSizes(...)`
  - `resolveAvailableStillCaptureResolutionPresets(...)`
  - `resolvedStillCaptureOutputSize(...)`
  - `createImageCapture(...)`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterStillCaptureQualityTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` still resolution tests

## Allowed Paths

- Read-only: `app/src/main/java/com/opencamera/app/camera/**`, `core/device/**`, `core/media/**`, `core/session/**`, related tests.
- Writable: `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/01-pixel-capability-enumeration.md` only.

## Forbidden Paths

- Do not edit runtime code or tests.
- Do not edit `INDEX.md`.
- Do not edit another package's status file.
- Do not add network dependencies or vendor-private SDK code.

## Required Analysis

1. Trace how still output sizes flow today:
   - Camera2 characteristics -> lens profile -> device capabilities -> session graph -> CameraX `ImageCapture`.
2. Identify all places where high-pixel options can be lost:
   - only reading standard JPEG output sizes,
   - not reading high-resolution / maximum-resolution Camera2 sizes where available,
   - 4:3-only normalization removing usable device modes,
   - preset target sizes capped at `LARGE_12MP`,
   - CameraX target resolution / resolution selector not entering a high-resolution mode,
   - mode/plugin policy downgrading output size.
3. Produce a proposed capability model:
   - `SUPPORTED`: selectable output sizes are actually available and bindable.
   - `DEGRADED`: sensor/hardware suggests higher pixel class but CameraX path cannot bind it locally.
   - `UNSUPPORTED`: no reliable high-pixel path is exposed.
4. Define the minimum future implementation shape:
   - where to collect standard and high-resolution output sizes,
   - how to preserve native output size labels such as `50MP`,
   - how to record diagnostics without claiming success before real-device proof,
   - which unit tests should be added before code changes.

## Verification Commands

Use read-only inspection first:

```bash
rtk rg -n "availableStillCaptureOutputSizes|normalizeStillCaptureOutputSizes|getOutputSizes|ResolutionSelector|StillCaptureOutputSize" app/src/main/java app/src/test core -g '*.kt'
```

If running focused tests, use:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterStillCaptureQualityTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

In a worktree, replace Gradle invocation with:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.camera.CameraXCaptureAdapterStillCaptureQualityTest
```

## Acceptance Criteria

- [ ] Status file explains the exact current enumeration path and where 48MP/50MP can disappear.
- [ ] Status file includes a proposed supported/degraded/unsupported capability contract.
- [ ] Status file lists concrete future code/test touchpoints, but does not implement them.
- [ ] Status file distinguishes standard JPEG sizes from high-resolution/maximum-resolution candidates.
- [ ] Status file explicitly says whether current evidence is local-code-only or real-device-backed.
- [ ] Verification commands and results are recorded.

## Expected Evidence Pack

Write to `status/01-pixel-capability-enumeration.md`:
- current path diagram in prose,
- capability-loss table,
- recommended future implementation contract,
- focused command results,
- unresolved unknowns for vivo X300 real-device proof.
