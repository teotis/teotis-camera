# Agent Prompts

Each package prompt is self-contained. Copy the relevant section into an agent, or run the script entrypoint and let `orchestrate.sh` launch ready packages.

## Package: 01-cockpit-bottom-layout - Cockpit Bottom Layout

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/01-cockpit-bottom-layout.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/01-cockpit-bottom-layout.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 01-cockpit-bottom-layout`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Read the INDEX and package doc. You may edit only allowed paths. Use your assigned worktree/branch, run shell commands through `rtk`, and use `rtk ./scripts/run_isolated_gradle.sh ...` for Gradle in a worktree. Do not edit INDEX or another package status. Respect projection ownership: scheduler state only through `mark-state`; human evidence in your coordinator status; scratch is non-authoritative.

Before calling `advance`, fill the coordinator status with worktree, branch, base commit, commit hash, changed files, verification commands/results, and risks. If blocked, classify the failure as `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 01-cockpit-bottom-layout completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 01-cockpit-bottom-layout
```

For a blocker, use `mark-state 01-cockpit-bottom-layout blocked --error "<specific blocker>" --failed-command "<failed command>" --log-summary "<summary>" --recovery-hint "<next action>"`, then call `advance --from 01-cockpit-bottom-layout`.

---

## Package: 02-zoom-preview-window-frame-contract - Zoom Preview Contract

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/02-zoom-preview-window-frame-contract.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/02-zoom-preview-window-frame-contract.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 02-zoom-preview-window-frame-contract`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Implement the package doc only. Do not directly drive CameraX from UI, and do not hard-code vivo-only camera IDs without supported/degraded/unsupported semantics. Record zoom mapping, diagnostics, tests, and residual device QA.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 02-zoom-preview-window-frame-contract completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 02-zoom-preview-window-frame-contract
```

For a blocker, mark `blocked` with recovery context, then call the same `advance`.

---

## Package: 03-dev-log-device-probe - Dev Logs And Probe

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/03-dev-log-device-probe.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/03-dev-log-device-probe.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 03-dev-log-device-probe`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Implement Dev log/tab and device probe diagnostics only. Keep logs privacy-safe and avoid raw media payloads.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 03-dev-log-device-probe completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 03-dev-log-device-probe
```

For a blocker, mark `blocked` with recovery context, then call `advance`.

---

## Package: 06-watermark-vivo-reference-polish - Watermark Vivo Polish

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/06-watermark-vivo-reference-polish.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/06-watermark-vivo-reference-polish.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 06-watermark-vivo-reference-polish`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Repair blur-four-border and preview affordances without copying vivo branding or adding fixed fake blur frames.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 06-watermark-vivo-reference-polish completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 06-watermark-vivo-reference-polish
```

For a blocker, mark `blocked` with recovery context, then call `advance`.

---

## Package: 04-quick-panel-behavior-defaults - Quick Behavior

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/04-quick-panel-behavior-defaults.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/04-quick-panel-behavior-defaults.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 04-quick-panel-behavior-defaults`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Start only after dependency `01-cockpit-bottom-layout` is complete unless the user intentionally overrides automation. Implement Live default off, Quick watermark localization, and outside dismiss behavior.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 04-quick-panel-behavior-defaults completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 04-quick-panel-behavior-defaults
```

For a blocker, mark `blocked` with recovery context, then call `advance`.

---

## Package: 05-style-settings-i18n-cleanup - Style Settings i18n

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/05-style-settings-i18n-cleanup.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/05-style-settings-i18n-cleanup.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 05-style-settings-i18n-cleanup`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Start only after dependency `04-quick-panel-behavior-defaults` is complete unless the user intentionally overrides automation. Remove Style English residue and expose Settings > Common language switch.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 05-style-settings-i18n-cleanup completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 05-style-settings-i18n-cleanup
```

For a blocker, mark `blocked` with recovery context, then call `advance`.

---

## Package: 07-real-device-acceptance-protocol - Acceptance Protocol

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/07-real-device-acceptance-protocol.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/07-real-device-acceptance-protocol.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 07-real-device-acceptance-protocol`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Start only after packages 01-06 are complete. Produce substitute evidence and a physical-device QA checklist; do not claim real-device pass.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 07-real-device-acceptance-protocol completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh advance --from 07-real-device-acceptance-protocol
```

For a blocker, mark `blocked` with recovery context, then call `advance`.

---

## Package: 99-finalize - Finalize

---

**Mode**: finalize executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh`

Run only after all functional packages are complete. Verify projection consistency, merge package branches according to INDEX, run integration verification, write `FINAL_REPORT.md`, mark `99-finalize` finalized on success, and preserve worktrees/branches on failure.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/latest-real-device-vivo-feedback-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "<integration verification: result>"
```

For a blocker, mark `99-finalize blocked` with full recovery context.

---
