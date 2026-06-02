# Package 99 - Finalize

## Goal

Verify package evidence, integrate package branches, run final verification, decide the task-level outcome, and write `FINAL_REPORT.md`. This package is a finalize package, not a passive audit.

## Required Inputs

- `INDEX.md`
- `launchers/package-graph.tsv`
- `status/state.tsv`
- `status/events.jsonl`
- all package docs
- all package status files
- all package branches and recorded commits

## Required Steps

1. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh verify-finalize
```

2. Verify every functional package:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack complete;
   - branch, worktree, base commit, and commit hash recorded;
   - verification commands passed or failure explicitly justified;
   - package branch and commit exist;
   - package worktree is clean or dirty state is a blocker.
3. Check projection consistency across INDEX, graph, state, package status, and events.
4. Check Capability Preflight. If real-device QA evidence is absent, the outcome may be `ready-for-external-gate` or `landed` with confidence caveat only if INDEX says the gate is not release-blocking.
5. Decide task-level outcome before merging: `landed`, `landed-with-approved-fallback`, `ready-for-external-gate`, `failed-no-merge`, or `failed-with-candidate-independent-fixes`.
6. Create or update `agent/latest-real-device-vivo-feedback/integration`.
7. Merge package branches in INDEX merge order.
8. Stop and record conflicts without cleanup if any merge fails.
9. Run integration verification:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
rtk ./scripts/run_isolated_gradle.sh ./scripts/verify_stage_7_observability.sh
```

If the stage script invocation through isolated Gradle wrapper is not supported, run the project-approved stage command from the main workspace:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

10. Merge verified integration branch back to mainline only if merging is allowed by the task outcome.
11. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
12. Delete only local branches/worktrees recorded by this orchestration after every prior step succeeds.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files, log summary, and recovery suggestion in `status/99-finalize.md`.
- Update the coordinator ledger with `mark-state 99-finalize blocked --error ...`.
- Preserve branches/worktrees on failure.
- Default to no merge when outcome is `failed-no-merge`.
- For `failed-with-candidate-independent-fixes`, list only predeclared independent candidates that pass standalone verification.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
