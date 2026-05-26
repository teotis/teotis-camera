# Package 99 - Finalize (Integration, Merge, Verification)

## Package ID

`99-finalize`

## Goal

Validate that both functional packages satisfy the user's real-device findings, merge their branches into an integration branch, verify the merged result, merge back to mainline, and clean up recorded resources.

## Scope

This is an active orchestration package, not a passive audit. It performs verification, merge, and cleanup operations.

## Steps

1. Read `INDEX.md`, `launchers/package-graph.tsv`, all package docs, all status files, and `status/state.tsv`.
2. Verify every functional package:
   - acceptance criteria addressed
   - changed files are within allowed paths
   - evidence pack complete (worktree, branch, base commit, commit hash, changed files, verification commands/results)
   - verification commands passed or failure is explicitly justified
3. Decide whether merging is allowed.
4. Create or update the integration branch `agent/real-device-preview-frame-watermark-polish/integration`.
5. Merge functional package branches in Merge Strategy order: `01-preview-frame-contract`, then `02-natural-blur-border-rendering`.
6. Stop and record conflicts without cleaning anything.
7. Run integration verification.
8. Merge integration branch back to mainline (`main`) only after verification passes.
9. Write `FINAL_REPORT.md` and `status/99-finalize.md` for both success and failure.
10. Delete only local package branches/worktrees recorded by this orchestration after every prior step succeeds.

## Acceptance Criteria

- [ ] Package `01-preview-frame-contract` meets every acceptance criterion or lists exact unmet criteria.
- [ ] Package `02-natural-blur-border-rendering` meets every acceptance criterion or lists exact unmet criteria.
- [ ] No package edited forbidden paths or another package's status file.
- [ ] No implementation introduced a hidden session/camera owner outside the established architecture.
- [ ] No implementation relies on fixed whitening to hide blur-frame visual defects.
- [ ] Final verification commands pass, or failures are classified as product blockers, environment blockers, or unrelated existing failures with evidence.
- [ ] Integration branch merges cleanly into mainline.
- [ ] `FINAL_REPORT.md` is written.
- [ ] Final result is reported as PASS, PARTIAL, or FAIL.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

Run the stage gate only if the focused verification is clean and the machine is not already running parallel Gradle work:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Failure Rules

- Any failure sets `99-finalize` to `blocked`.
- Record failure stage, command, branch, conflict files if any, and recovery suggestion.
- Preserve branches/worktrees on failure.
- Never force-push, hard reset, delete remote branches, or delete unrecorded local resources.

## Success Rules

- Mark `99-finalize` as `finalized`.
- Record integration branch, mainline merge commit, verification summary, and cleanup results.
- Re-running finalize after success must be idempotent and report `already finalized`.

## File Ownership

Allowed paths:
- `docs/plans/real-device-preview-frame-watermark-polish-orchestration/status/99-finalize.md`
- `docs/plans/real-device-preview-frame-watermark-polish-orchestration/FINAL_REPORT.md`
- Integration branch management (merge, commit)
- Recorded local worktree/branch cleanup

Forbidden paths:
- Functional package source files (read-only inspection only)
- INDEX.md
- Other packages' status files (read-only inspection only)

## Coordinator Status And State

Write to `status/99-finalize.md` and update `status/state.tsv`.

State transitions: `pending` -> `finalizing` -> `finalized` or `blocked`.

Evidence must include:
- Per-package acceptance criteria status: met / unmet / unverifiable.
- Integration branch name and merge commits.
- Integration test results.
- Cross-package conflict report.
- Mainline merge commit hash.
- Cleanup results (branches/worktrees deleted).
- Final recommendation: PASS / PARTIAL / FAIL.

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```
