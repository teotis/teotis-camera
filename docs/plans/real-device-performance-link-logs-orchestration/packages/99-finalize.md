# Package 99 - Finalize

## Package ID

`99-finalize`

## Goal

Integrate the functional package branches, verify the combined result, merge back to mainline after verification, write the final report, and clean up only recorded resources after success.

## Branch And Worktree

- Branch: `agent/performance-link-logs/99-finalize`
- Integration branch: `agent/performance-link-logs/integration`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/99-finalize`

## Authorization

`99-finalize` may:

- Inspect `INDEX.md`, package docs, status files, `state.tsv`, events, branches, commits, and diffs.
- Run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh verify-finalize`.
- Create/update the integration branch.
- Merge functional branches in the merge order from `INDEX.md`.
- Run integration verification.
- Merge the verified integration branch back to `main`.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only package worktrees/branches recorded in this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:

- force-push
- hard reset
- delete remote branches
- delete local branches/worktrees not recorded by this plan
- clean unrelated files
- commit coordinator ledger/final report together with unrelated source changes
- claim real-device performance acceptance without exported device logs

## Required Finalize Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh verify-finalize
```

3. For every functional package, verify:
   - acceptance criteria addressed;
   - changed files are within allowed paths;
   - evidence pack is complete;
   - branch exists;
   - commit hash exists;
   - package worktree is clean or dirty state is recorded as a blocker;
   - verification commands passed or failure is explicitly justified.
4. Stop if any package is incomplete, stale, invalid, blocked, or outside allowed paths.
5. Create or update `agent/performance-link-logs/integration`.
6. Merge functional package branches in this order:
   - `01-performance-timing-contract`
   - `02-core-flow-instrumentation`
   - `03-dev-link-log-export`
   - `04-real-device-log-analysis-protocol`
7. On conflict, stop, preserve worktrees/branches, mark `99-finalize` blocked, and record conflict files.
8. Run integration verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.DevLogRenderModelTest --tests com.opencamera.app.DevLogExporterTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

9. If focused verification passes and the machine is available, run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

If Stage 7 does not complete, report focused tests/build separately from the unfinished full gate.
10. Check the Capability Preflight section. If real-device performance QA evidence is absent, report `implementation verified locally; real-device performance confidence pending` rather than overall product PASS.
11. Merge the verified integration branch back to `main` only after local verification passes and any release-blocking external gate has either passed or been explicitly deferred by the user.
12. Write `FINAL_REPORT.md` and `status/99-finalize.md`.
13. Mark `99-finalize` as `finalized` through the orchestrator.
14. Clean up only recorded package worktrees/branches after all previous steps succeed.

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, log summary, and recovery suggestion in `status/99-finalize.md`.
- Also update the coordinator ledger with:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<specific failure>" \
  --failed-command "<failed command>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<decisive log lines>" \
  --recovery-hint "<next action>"
```

- Preserve branches/worktrees on failure.
- Check `status/events.jsonl` before retrying repeated failure fingerprints.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, APK path, real-device evidence status, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.
