# Package 04 - Integration Verification And Smoke

## Package ID

`04-integration-verification-and-smoke`

## Goal

After packages 01-03 land or provide completed evidence, run the local integration gates and write the final real-device smoke protocol/results placeholder for user/Codex verification.

## Allowed Paths

- `docs/plans/zoom-brightness-rollback-implementation-orchestration/status/04-integration-verification-and-smoke.md`
- Optional: `docs/plans/zoom-brightness-rollback-implementation-orchestration/FINAL_REPORT.md` only if 99-finalize delegates report drafting

## Forbidden Paths

- Runtime source/tests unless an obvious typo in package 01-03 blocks verification and the fix is explicitly within their changed lines.
- `docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
- another package's status file

## Dependencies

- Depends on: `01-zoom-slider-render-latch`, `02-brightness-dispatch-and-latch`, `03-pinch-zoom-basis-repair`

## Verification Requirements

Run focused tests first:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Then build:

```bash
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

Then Stage 7 gate if focused tests/build pass:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Real-Device Smoke Checklist

Record this as pending unless a device is actually available:

- Zoom slider node labels visible at common device widths.
- Slow zoom drag follows finger.
- Fast zoom drag does not jump back to original value before final value.
- Preset dot tap still jumps exactly.
- Pinch zoom after slider drag starts from current zoom basis.
- Quick brightness slow drag follows finger.
- Quick brightness fast drag does not rebound to an older applied value.
- After release, brightness settles to the final requested value.
- Disabled states during countdown/photo saving/record start do not look active.

## Acceptance Criteria

- Focused tests pass or failures are explicitly attributed and actionable.
- `:app:assembleDebug` passes.
- Stage 7 gate is run, or a clear blocker is recorded.
- Real-device smoke checklist is present with `pending`, `pass`, or `fail` per row.
- Status evidence is complete for 99-finalize.

## Expected Evidence

- worktree path
- branch
- base commit
- commit hash
- commands run and summaries
- pass/fail table
- real-device checklist status
- unresolved risks

