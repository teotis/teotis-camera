# Agent Prompts

## Package: 01-watermark-template-preview-expectation - Watermark Template Preview Expectation

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/packages/01-watermark-template-preview-expectation.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/01-watermark-template-preview-expectation.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh scratch-path 01-watermark-template-preview-expectation`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh`

You are implementing Package `01-watermark-template-preview-expectation`.

Read the INDEX and package doc first. You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Important project rules:
- Run shell commands through `rtk`.
- In the package worktree, run Gradle through `rtk ./scripts/run_isolated_gradle.sh ...`.
- Preserve Session Kernel / Device Adapter / Media Pipeline boundaries.
- Do not claim real-device visual acceptance from local tests.

Before editing, inspect current watermark preview code: `PreviewEffectModel.kt`, `PreviewEffectAdapter.kt`, `SessionPreviewRenderModel.kt`, `PreviewOverlayView.kt`, `SessionUiRenderModel.kt`, and `SettingsPanelRenderer.kt`.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 01-watermark-template-preview-expectation completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 01-watermark-template-preview-expectation blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance --from 01-watermark-template-preview-expectation
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-zoom-threshold-live-preview-switch - Zoom Threshold Live Preview Switch

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/packages/02-zoom-threshold-live-preview-switch.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/02-zoom-threshold-live-preview-switch.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh scratch-path 02-zoom-threshold-live-preview-switch`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh`

You are implementing Package `02-zoom-threshold-live-preview-switch`.

Read the INDEX and package doc first. You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Important project rules:
- Run shell commands through `rtk`.
- In the package worktree, run Gradle through `rtk ./scripts/run_isolated_gradle.sh ...`.
- UI may dispatch intents but must not directly drive CameraX/Camera2 runtime.
- Do not claim physical-lens visual acceptance from local tests.

Before editing, inspect current zoom code: `FocalLengthSliderView.kt`, `DefaultCameraSession.handleApplyZoomRatio(...)`, `CameraSessionCoordinator.kt`, and `CameraXCaptureAdapter.switchLensNode/updateZoomRatio`.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 02-zoom-threshold-live-preview-switch completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 02-zoom-threshold-live-preview-switch blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance --from 02-zoom-threshold-live-preview-switch
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-integration-real-device-smoke-protocol - Integration Real-Device Smoke Protocol

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it after dependencies complete.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/packages/03-integration-real-device-smoke-protocol.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/03-integration-real-device-smoke-protocol.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh scratch-path 03-integration-real-device-smoke-protocol`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh`

You are implementing Package `03-integration-real-device-smoke-protocol`.

Read the INDEX, package doc, package 01/02 docs, package 01/02 status files, `state.tsv`, and `events.jsonl` before doing anything. You may edit only the allowed paths in the package doc.

Run the required focused verification through `rtk ./scripts/run_isolated_gradle.sh ...` from the assigned worktree. Produce the real-device smoke checklist in the coordinator status file, including APK path and residual risks.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash if any, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 03-integration-real-device-smoke-protocol completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 03-integration-real-device-smoke-protocol blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance --from 03-integration-real-device-smoke-protocol
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Finalize

Copy this prompt into an agent only after all functional packages are completed, or let `orchestrate.sh advance/finalize` launch it.

---

**Mode**: finalizer
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh`

You are the finalizer for this orchestration. Read all plan/package/status files and `state.tsv`. Run `verify-finalize` before merging. Verify package evidence, allowed paths, commits, and clean package worktrees.

Merge functional branches in the order specified by INDEX. Stop and record conflicts or verification failures; do not clean anything on failure.

Run integration verification from the main checkout using `rtk ./gradlew ...` as listed in the package doc. If focused verification passes and the machine is available, run the Stage 7 script. Merge back to `main` only after verification passes.

Before finishing:
- Write `FINAL_REPORT.md`.
- Write `status/99-finalize.md`.
- Mark the ledger with `mark-state 99-finalize finalized`.
- Clean up only recorded package branches/worktrees after success.

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<specific failure>" \
  --failed-command "<failed command>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<decisive log lines>" \
  --recovery-hint "<next action>"
```

Tail step is not required after successful finalization. If blocked, report the exact recovery command:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh status
```

---
