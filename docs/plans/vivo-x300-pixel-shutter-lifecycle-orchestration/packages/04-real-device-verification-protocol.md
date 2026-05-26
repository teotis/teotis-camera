# Package 04 — Real Device Verification Protocol

## Package ID

`04-real-device-verification-protocol`

## Purpose

Define the vivo X300 real-device evidence protocol for both pixel capability and shutter recovery. This package should convert packages 01-03 into concrete local gates, manual steps, adb/log evidence, screenshots or screen recordings, and PASS/PARTIAL/FAIL criteria. It must prevent desktop tests from being mistaken for final device acceptance.

## Dependencies

- Wait for `status/01-pixel-capability-enumeration.md`.
- Wait for `status/02-quick-pixel-surface-design.md`.
- Wait for `status/03-shutter-lifecycle-contract.md`.

## Allowed Paths

- Read-only: all relevant code/tests/docs.
- Writable: `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/04-real-device-verification-protocol.md` only.

## Forbidden Paths

- Do not edit runtime code or tests.
- Do not edit `INDEX.md`.
- Do not edit another package's status file.
- Do not claim device PASS unless actual vivo X300 evidence exists.

## Required Protocol

1. Pixel capability proof:
   - record device model, Android version, app build, lens facing,
   - capture capability diagnostics/logs for standard and high-resolution output candidates,
   - verify quick pixel row max label and switching behavior,
   - capture saved JPEG dimensions/metadata for each selected pixel mode,
   - mark `SUPPORTED`, `DEGRADED`, or `UNSUPPORTED` with evidence.
2. Shutter recovery proof:
   - record screen with audio if possible so shutter sound/frame acquisition and button re-enable can be compared,
   - capture diagnostics for requested, device started, device completed/data received, postprocess completed, and saved media visible,
   - verify normal still capture re-arms before heavy postprocess/save completes if the final design allows it,
   - verify night/high-pixel/multi-frame modes keep conservative blocking where required.
3. Local gates before device test:
   - focused session lifecycle tests,
   - focused app render model tests,
   - focused CameraX capability tests,
   - `:app:assembleDebug`,
   - relevant Stage 7 script only after focused gates pass.

## Verification Commands

Suggested local gate after future implementation, not for this research-only package:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest --tests com.opencamera.app.SessionStateRenderTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

In a worktree, use `rtk ./scripts/run_isolated_gradle.sh` for Gradle commands.

## Acceptance Criteria

- [ ] Status file defines a step-by-step vivo X300 QA script.
- [ ] Status file names exact evidence artifacts to collect: logs, screen recording, saved JPEG dimensions, diagnostics notes, app build, device info.
- [ ] Status file defines PASS/PARTIAL/FAIL outcomes for pixel capability.
- [ ] Status file defines PASS/PARTIAL/FAIL outcomes for shutter recovery timing.
- [ ] Status file separates local verification from final real-device acceptance.

## Expected Evidence Pack

Write to `status/04-real-device-verification-protocol.md`:
- local gate checklist,
- real-device checklist,
- artifact naming convention,
- failure examples,
- final acceptance rubric.
