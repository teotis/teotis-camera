# Agent Prompts

## Package: 01-analyze-preview-zoom-strategy - Analyze Preview Zoom Strategy

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/packages/01-analyze-preview-zoom-strategy.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/01-analyze-preview-zoom-strategy.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh scratch-path 01-analyze-preview-zoom-strategy`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 01-analyze-preview-zoom-strategy completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 01-analyze-preview-zoom-strategy blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance --from 01-analyze-preview-zoom-strategy
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance
```

---

## Package: 02-implement-discrete-preview-zoom - Implement Discrete Preview Zoom (Session Layer)

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/packages/02-implement-discrete-preview-zoom.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/02-implement-discrete-preview-zoom.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh scratch-path 02-implement-discrete-preview-zoom`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before starting implementation, read the analysis document from Package 01 at:
```bash
cat /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/scratch/01-analyze-preview-zoom-strategy/analysis.md
```

If the analysis doc does not exist yet, read the design notes in INDEX.md's "Key Design Decisions" section and the Package 02 doc's "Design Notes" section as fallback.

Key implementation steps:
1. Add `previewZoomRatio: Float = 1f` to `PreviewConfig` in `DeviceContracts.kt`
2. Implement `computePreviewZoomRatio()` function
3. Update `handleApplyZoomRatio()` to compute and store both zoom ratios
4. Update `resolveActiveDeviceGraph()` to set `previewZoomRatio`
5. Write unit tests

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 02-implement-discrete-preview-zoom completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 02-implement-discrete-preview-zoom blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance --from 02-implement-discrete-preview-zoom
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance
```

---

## Package: 03-fix-overlay-frame-geometry - Fix Overlay Frame Geometry

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/packages/03-fix-overlay-frame-geometry.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/03-fix-overlay-frame-geometry.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh scratch-path 03-fix-overlay-frame-geometry`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before starting implementation, read:
1. The analysis document from Package 01:
```bash
cat /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/scratch/01-analyze-preview-zoom-strategy/analysis.md
```
2. Check if Package 02 has been merged and the `previewZoomRatio` field is available. If not yet merged, design the `PreviewFrameRenderModel` changes to reference `previewZoomRatio` but use a fallback value (= `zoomRatio`) for compilation until 99-finalize integrates.

Key implementation steps:
1. Add `previewZoomRatio` field to `PreviewFrameRenderModel` in `SessionPreviewRenderModel.kt`
2. Wire `previewZoomRatio` from `state.activeDeviceGraph.preview.previewZoomRatio` (or fallback to `zoomRatio`)
3. Fix `previewContentGeometry()` to use actual sensor aspect (4:3 default) when `previewContentAspect` is null
4. Update `activeContentGeometry()` to use `previewZoomRatio` for frame scaling
5. Add boundary clamping to ensure frame rect ≤ content rect
6. Update geometry tests

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch.

Do not attempt external-assist work inside a Claude package.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 03-fix-overlay-frame-geometry completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 03-fix-overlay-frame-geometry blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance --from 03-fix-overlay-frame-geometry
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent, or let `orchestrate.sh advance` auto-launch it when all functional packages are completed.

---

**Mode**: package executor (finalize)
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh

You are the finalize package. Your job is to integrate all functional packages, verify the combined result, and merge to mainline.

Steps:
1. Read INDEX, graph, all package docs, all status files, and `state.tsv`
2. Run `bash launchers/orchestrate.sh verify-finalize`
3. Verify every functional package: acceptance criteria, changed files in allowed paths, evidence complete, branch/commit recorded, verification passed
4. Check Capability Preflight: note `real-device-zoom-preview-qa` is external and deferred
5. Create or update integration branch `agent/preview-zoom-discrete-stepping/integration`
6. Merge in order: `01-analyze-preview-zoom-strategy` → `02-implement-discrete-preview-zoom` → `03-fix-overlay-frame-geometry`
7. Stop on conflicts; record details
8. Run integration verification: `./gradlew :core:device:compileDebugKotlin :core:session:compileDebugKotlin :app:compileDebugKotlin`
9. Run tests: `./gradlew :core:session:testDebugUnitTest :app:testDebugUnitTest`
10. On success: merge integration to mainline (local only), write FINAL_REPORT.md, clean up
11. On failure: mark blocked with precise details

Before calling `advance`, mark your status:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 99-finalize finalized --commit <merge-commit-sha> --verification "<summary>" --integration "<integration-branch>" --cleanup "<cleaned resources>"
```

Or on failure:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<failure description>" \
  --failed-command "<failed command>" \
  --conflict-files "<conflicting files>" \
  --log-summary "<log summary>" \
  --recovery-hint "<recovery suggestion>"
```
