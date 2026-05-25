# Stage 7 Device Still Quality Test Repair

## Goal

Unblock Stage 7 verification by updating `DefaultDeviceShotRequestTranslatorTest.kt` to match the current device request contract for still capture quality.

## Context

- Earlier validation command `rtk ./scripts/verify_stage_7_observability.sh` failed in `:core:device:compileTestKotlin`.
- Current recheck: the focused device translator test now passes in this workspace, so the compile blocker is no longer reproducible locally.
- Error examples:
  - `Unresolved reference: stillCaptureQuality`
  - `Cannot find a parameter with this name: stillCaptureQuality`
- Current production references found during validation:
  - `CaptureProfile.stillCaptureQuality` still exists in `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt`.
  - `DeviceContracts.kt` now appears to expose `qualityPreference` rather than `stillCaptureQuality` on the device-facing request type.
- Relevant files:
  - `core/device/src/test/kotlin/com/opencamera/core/device/DefaultDeviceShotRequestTranslatorTest.kt`
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt`
  - `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt`
- Non-goals:
  - Do not change document V2 behavior in this package.
  - Do not remove still-quality behavior from production unless investigation proves the product contract changed.

## Implementation Scope

- Inspect the current device request data class and translator output.
- Update tests to assert the current field name/shape.
- If production translator no longer forwards `CaptureProfile.stillCaptureQuality`, decide whether that is intended:
  - If intended, update tests to assert `qualityPreference` and diagnostics.
  - If unintended, restore translator mapping from capture profile to device request, then update tests.
- Keep diagnostics assertions such as `device:capture-mode=minimize-latency` / `device:capture-mode=max-quality` if they remain part of the contract.

## Steps

1. Open `DeviceContracts.kt` around the device shot request data class.
2. Open `DeviceShotRequestTranslator.kt` and find how `CaptureProfile.stillCaptureQuality` maps into the device request.
3. Update `DefaultDeviceShotRequestTranslatorTest.kt`:
   - replace `request.stillCaptureQuality` with the current field if renamed;
   - replace named constructor arguments if the request type changed;
   - keep tests proving latency vs quality behavior.
4. Run the focused device test.
5. Run Stage 7 script.

## Acceptance Criteria

- `DefaultDeviceShotRequestTranslatorTest` compiles.
- Tests still verify quality-vs-latency behavior instead of deleting coverage.
- `rtk ./scripts/verify_stage_7_observability.sh` gets past `:core:device:compileTestKotlin`.

## Current Verification Evidence

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
```

Result on 2026-05-25: `BUILD SUCCESSFUL`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test --tests com.opencamera.core.device.DefaultDeviceShotRequestTranslatorTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- This was a global verification blocker discovered during document V2 validation, but it was not caused by the document side rail work.
- Do not mark Document V2 as fully accepted only because this blocker is cleared; organizer UI still needs its own acceptance.
