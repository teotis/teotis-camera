# Package 07 - Real Device Acceptance Protocol

## Goal

Produce the substitute evidence bundle for final real-device validation. This package must not claim that physical QA passed; it prepares APK path, install commands, Dev log export steps, screenshots/video checklist, and pass/fail criteria for the user/device owner.

## Dependencies

Depends on packages 01 through 06.

## Allowed Paths

- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/07-real-device-acceptance-protocol.md`
- `docs/plans/latest-real-device-vivo-feedback-orchestration/scratch/07-real-device-acceptance-protocol/**`
- Optional package-local checklist file under `docs/plans/latest-real-device-vivo-feedback-orchestration/packages/` if needed

## Forbidden Paths

- Runtime/application source files.
- Other package status files.
- Claiming device pass without external screenshots/video/log evidence.

## Tasks

1. Read package statuses and collect final APK path from assemble output.
2. Write install/run commands using the local APK path.
3. Produce a device QA checklist covering all user findings:
   - bottom cockpit spacing;
   - zoom drag across `0.7/1/3/5/10x` and preview lag;
   - Dev `链路` logs and capability probe details;
   - Live default off;
   - Quick watermark localized label;
   - Quick outside tap dismissal;
   - Style no English residue;
   - Settings > Common language switch;
   - blur-four-border saved output and preview hint;
   - vivo-inspired selected-state/preview clarity.
4. Define expected evidence: screenshots, short zoom video, exported Dev LINK/ERROR logs, device model/Android version, APK path/commit.
5. Run final local smoke commands that are reasonable for this package.

## Acceptance Criteria

- Status file contains a complete, copy-pasteable QA protocol.
- APK path/install command is recorded or the package is blocked with the failed assemble command.
- The protocol separates local verification from external real-device confidence.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
```

## Evidence Required

- APK path and install command.
- QA checklist and expected external artifacts.
- Local smoke verification results.

## Unlock Condition

Mark `completed` only after the protocol and substitute verification evidence are complete.
