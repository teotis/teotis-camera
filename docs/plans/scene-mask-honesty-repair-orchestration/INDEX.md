# Scene Mask Honesty Repair - Orchestration Index

## Goal

Turn the latest Scene Mask research findings into a small implementation repair loop. The target outcome is not a bigger segmentation roadmap; it is a trustworthy Phase-1 Scene Mask foundation where saved-photo Color Lab subject protection actually writes pixels, preview analysis has an explicit single-owner `ImageProxy` lifecycle and bounded preview segmentation cost, diagnostics do not overclaim, and focused verification can prove the local contract before real-device visual QA.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/scene-mask-honesty-repair/integration`
- Functional package branches: `agent/scene-mask-honesty-repair/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/scene-mask-honesty-repair-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths
- expand into ML Kit Subject, MediaPipe, semantic segmentation, true depth, or product UI copy beyond this repair loop

## Current Evidence Baseline

- The canonical contracts in `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt` are usable and should not be redesigned in this loop.
- `PhotoAlgorithmPostProcessor` routes saved masks to `MaskAwarePhotoAlgorithmEditor.applyWithMask(...)`, but current `AndroidPhotoAlgorithmEditor.applyWithMask(...)` applies mask-aware style to the passed bitmap and returns `PhotoAlgorithmApplied()` without writing encoded bytes back to the `ProcessorTarget`. This can make `scene-mask:saved=applied` overclaim the saved output.
- `PreviewAnalysisFanout` currently owns `ImageProxy.close()` and tests assert close-once behavior. Agents must preserve that single owner instead of moving lifecycle responsibility into `MlKitSelfiePreviewSceneMaskSource` blindly.
- `MlKitSelfiePreviewSceneMaskSource` logs `PreviewSceneMaskConfig.targetWidth/targetHeight` but does not use them, and `maxFps` is not enforced. This makes the preview path more expensive and less bounded than the config claims.
- 2D subject masks remain `PERSON_SUBJECT` only. This loop must not claim true depth, semantic regions, or vendor-equivalent OPPO/vivo portrait pipelines.

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-saved-photo-writeback-honesty | none | code | completed and branch merged to integration before 03 | 1 |
| 02-preview-analysis-budget-contract | none | code | completed and branch merged to integration before 03 | 1 |
| 03-scene-mask-verification-gate | 01-saved-photo-writeback-honesty, 02-preview-analysis-budget-contract | code | upstream merged to integration branch | 2 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-saved-photo-writeback-honesty -> 02-preview-analysis-budget-contract -> 03-scene-mask-verification-gate`
- Code dependency policy: package 03 bases on integration after packages 01 and 02 merge, because it may add a verification script and documentation that references their tests.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- A package tries to make preview mask the saved-photo truth.
- A package moves `ImageProxy.close()` ownership away from `PreviewAnalysisFanout` without explicit code evidence and tests.
- A package claims `SUPPORTED` for true depth, semantic region, or non-person subject segmentation.

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-saved-photo-writeback-honesty.md](packages/01-saved-photo-writeback-honesty.md) | implementation agent | none | safe with 02 | Make mask-aware PhotoAlgorithm saved output persist pixels and keep diagnostics honest |
| [02-preview-analysis-budget-contract.md](packages/02-preview-analysis-budget-contract.md) | implementation agent | none | safe with 01 | Preserve fanout close-once ownership while making preview mask config cost claims real |
| [03-scene-mask-verification-gate.md](packages/03-scene-mask-verification-gate.md) | implementation/docs agent | 01, 02 | no | Add focused Scene Mask honesty gate and update docs/ledger without overclaiming |
| [99-finalize.md](packages/99-finalize.md) | finalize agent | all functional packages | no | Merge, verify, report, and cleanup |

