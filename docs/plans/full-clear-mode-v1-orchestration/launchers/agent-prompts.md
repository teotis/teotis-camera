# Agent Prompts

Copy the prompt for a package into an agent, or run the script entrypoint from the repository root.

## Package: 01-full-clear-product-definition - Product Definition And Mode Surface

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/packages/01-full-clear-product-definition.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/01-full-clear-product-definition.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh scratch-path 01-full-clear-product-definition`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Use `rtk` for shell commands. In package worktrees, use `rtk ./scripts/run_isolated_gradle.sh ...` for Gradle.

Do not edit INDEX.md or another package status file. Do not attempt external-assist work. If you discover a physical-device, account, secret, or human-only gate, mark the package `blocked` with a recovery hint.

Before calling `advance`, fill coordinator status with worktree, branch, base commit, commit hash, changed files, verification results, risks, and then run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 01-full-clear-product-definition completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 01-full-clear-product-definition blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh advance --from 01-full-clear-product-definition
```

If your platform cannot run local shell commands, report "completed but advance not run" and tell the user to run the same advance command.

---

## Package: 02-focus-bracket-capture-contract - Focus Bracket Capture Contract

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/packages/02-focus-bracket-capture-contract.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/02-focus-bracket-capture-contract.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh scratch-path 02-focus-bracket-capture-contract`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh`

Follow the package doc exactly. Use `rtk` and isolated Gradle. Do not edit another package status file or direct CameraX from mode/UI. Record completed or blocked state through the orchestrator only.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 02-focus-bracket-capture-contract completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh advance --from 02-focus-bracket-capture-contract
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 02-focus-bracket-capture-contract blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 03-camerax-focus-bracket-execution - CameraX Focus Bracket Execution

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/packages/03-camerax-focus-bracket-execution.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/03-camerax-focus-bracket-execution.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh scratch-path 03-camerax-focus-bracket-execution`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh`

Only start after package 02 is completed or automation launches you. Implement adapter execution with honest diagnostics; do not claim physical lens movement was verified without real-device evidence.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 03-camerax-focus-bracket-execution completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh advance --from 03-camerax-focus-bracket-execution
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 03-camerax-focus-bracket-execution blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 04-focus-stack-v1-honest-rendering - Focus Stack V1 Honest Rendering

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/packages/04-focus-stack-v1-honest-rendering.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/04-focus-stack-v1-honest-rendering.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh scratch-path 04-focus-stack-v1-honest-rendering`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh`

Only start after packages 01-03 are completed or automation launches you. Implement synthetic-testable V1 fusion or best-frame fallback with pipeline notes; do not overclaim real-scene product acceptance.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 04-focus-stack-v1-honest-rendering completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh advance --from 04-focus-stack-v1-honest-rendering
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 04-focus-stack-v1-honest-rendering blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 05-full-clear-qa-and-product-docs - QA And Product Docs

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/packages/05-full-clear-qa-and-product-docs.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/05-full-clear-qa-and-product-docs.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh scratch-path 05-full-clear-qa-and-product-docs`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh`

Only start after package 04 is completed or automation launches you. Produce APK/install/checklist/docs evidence. Real-device QA remains external-assist; do not claim it passed.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 05-full-clear-qa-and-product-docs completed --commit <commit-sha> --verification "<command: result>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh advance --from 05-full-clear-qa-and-product-docs
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 05-full-clear-qa-and-product-docs blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

## Package: 99-finalize - Finalize

---

**Mode**: finalizer
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh`

Run only after all functional packages are completed or automation launches you. Follow `packages/99-finalize.md`; verify evidence before merging. If any external-assist gate is missing, report it honestly as product confidence pending unless the user explicitly made it release-blocking.

Completion:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "<integration verification summary>" --integration "<integration/mainline merge summary>" --cleanup "<cleanup summary>"
```

Blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked --error "<specific blocker>" --failed-command "<failed command, if any>" --conflict-files "<comma-separated files, if any>" --log-summary "<short log summary>" --recovery-hint "<specific next action>"
```

---

