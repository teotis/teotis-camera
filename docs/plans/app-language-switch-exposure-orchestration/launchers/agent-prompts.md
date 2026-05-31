# Agent Prompts

## Package: 01-language-persistence-contract - Language Persistence Contract

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/packages/01-language-persistence-contract.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/01-language-persistence-contract.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh scratch-path 01-language-persistence-contract`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 01-language-persistence-contract completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 01-language-persistence-contract blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh advance --from 01-language-persistence-contract
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-settings-language-entry - Settings Language Entry

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/packages/02-settings-language-entry.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/02-settings-language-entry.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh scratch-path 02-settings-language-entry`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 02-settings-language-entry completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 02-settings-language-entry blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh advance --from 02-settings-language-entry
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-language-switch-verification - Language Switch Verification

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/packages/03-language-switch-verification.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/03-language-switch-verification.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh scratch-path 03-language-switch-verification`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Use scratch only for temporary shared notes, inventories, command transcripts, draft diffs, or intermediate artifacts that help another package or finalizer inspect the work. Do not put credentials, tokens, private keys, `.env` files, hidden prompts, proprietary raw data, or authoritative completion evidence in scratch. Anything required for scheduling, completion, or final acceptance must be summarized into coordinator status through `mark-state` and the package status file.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Do not invent an unapproved fallback. If the primary package path cannot land, classify why, identify whether an INDEX-declared fallback applies, and record whether the situation should retry, investigate, ask the user, switch to a named fallback, or abort the orchestration. If your package might be an independent merge candidate, state why it is independent and provide standalone verification; otherwise say it should be discarded if the main plan fails.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- For blockers, classify the failure as one of: `capability-gap`, `invalid-requirement`, `external-dependency`, `verification-failure`, `merge-conflict`, `design-invalid`, `cost-out-of-scope`, or `unknown-needs-investigation`.
- If this package was retried or previously blocked, inspect `state.tsv`, the package status file, and `status/events.jsonl` before editing. Carry forward the recorded `last_error`, `failed_command`, `conflict_files`, `log_summary`, and `recovery_hint` into your diagnosis.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 03-language-switch-verification completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 03-language-switch-verification blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh advance --from 03-language-switch-verification
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - App Language Switch Integration

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/status/state.tsv`
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before finalizing, run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh verify-finalize
```

Then follow the merge and verification steps in the package doc. Do not merge if any functional package is not completed, if evidence is incomplete, if forbidden paths changed, or if the Landing Strategy outcome is `failed-no-merge`.

For success:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --commit <mainline-merge-sha> --verification "<integration verification summary>" --integration "<integration branch/merge summary>" --cleanup "<cleanup summary>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/app-language-switch-exposure-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

---
