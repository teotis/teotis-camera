# Agent Prompts

Copy the prompt for a package into an agent, or run the script entrypoint from the repository root.

## Package: 01-vendor-and-literature-research - Vendor And Literature Research

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/packages/01-vendor-and-literature-research.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/01-vendor-and-literature-research.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh scratch-path 01-vendor-and-literature-research`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Use `rtk` for shell commands. Do not edit INDEX.md or another package status file. Do not attempt external-assist work. If web access is unavailable, use the existing source list and say refresh was not performed.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 01-vendor-and-literature-research completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh advance --from 01-vendor-and-literature-research
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 01-vendor-and-literature-research blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 02-v2-product-capability-model - V2 Product Capability Model

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/packages/02-v2-product-capability-model.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/02-v2-product-capability-model.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh scratch-path 02-v2-product-capability-model`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh`

Only start after package 01 is completed or automation launches you. Edit only allowed docs and status. Keep V2 promise honest and capability-gated.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 02-v2-product-capability-model completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh advance --from 02-v2-product-capability-model
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 02-v2-product-capability-model blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 03-v2-android-architecture-design - V2 Android Architecture Design

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/packages/03-v2-android-architecture-design.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/03-v2-android-architecture-design.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh scratch-path 03-v2-android-architecture-design`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh`

Only start after packages 01 and 02 are completed or automation launches you. Keep ownership inside Mode Plugin / Session Kernel / Device Adapter / Media Pipeline.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 03-v2-android-architecture-design completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh advance --from 03-v2-android-architecture-design
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 03-v2-android-architecture-design blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 04-v2-algorithm-and-media-pipeline-design - V2 Algorithm And Media Pipeline Design

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/packages/04-v2-algorithm-and-media-pipeline-design.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/04-v2-algorithm-and-media-pipeline-design.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh scratch-path 04-v2-algorithm-and-media-pipeline-design`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh`

Only start after packages 01 and 02 are completed or automation launches you. Design confidence and fallback first; do not promise vendor-grade fusion.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 04-v2-algorithm-and-media-pipeline-design completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh advance --from 04-v2-algorithm-and-media-pipeline-design
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 04-v2-algorithm-and-media-pipeline-design blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 05-v2-roadmap-validation-and-handoffs - Roadmap Validation And Handoffs

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/packages/05-v2-roadmap-validation-and-handoffs.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/05-v2-roadmap-validation-and-handoffs.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh scratch-path 05-v2-roadmap-validation-and-handoffs`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh`

Only start after packages 03 and 04 are completed or automation launches you. Produce implementation waves and real-device gates without editing runtime code.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 05-v2-roadmap-validation-and-handoffs completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh advance --from 05-v2-roadmap-validation-and-handoffs
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 05-v2-roadmap-validation-and-handoffs blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 99-finalize - Finalize

---

**Mode**: finalizer
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh`

Run only after all functional packages are completed or automation launches you. Follow `packages/99-finalize.md`; verify docs evidence before merging.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "<docs verification summary>" --integration "<integration/mainline merge summary>" --cleanup "<cleanup summary>"
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

