# Agent Prompts

## Package: 01-public-exposure-inventory - Public Exposure Inventory

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/packages/01-public-exposure-inventory.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/01-public-exposure-inventory.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash if any, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator; do not edit `state.tsv` manually:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 01-public-exposure-inventory completed --commit <commit-sha-or-none> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 01-public-exposure-inventory blocked --error "<specific blocker>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from 01-public-exposure-inventory
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 02-public-rules-export-gate - Public Rules And Export Gate

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/packages/02-public-rules-export-gate.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/02-public-rules-export-gate.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 02-public-rules-export-gate completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 02-public-rules-export-gate blocked --error "<specific blocker>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from 02-public-rules-export-gate
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 03-brand-reference-content-scrub - Brand Reference Content Scrub

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/packages/03-brand-reference-content-scrub.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/03-brand-reference-content-scrub.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above. Public repo edits must be done on a recorded local public-repo branch/worktree and must not be pushed.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash if any, public repo branch/worktree if used, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 03-brand-reference-content-scrub completed --commit <commit-sha-or-none> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 03-brand-reference-content-scrub blocked --error "<specific blocker>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from 03-brand-reference-content-scrub
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 04-public-history-remediation-plan - Public History Remediation Plan

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/packages/04-public-history-remediation-plan.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/04-public-history-remediation-plan.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Do not rewrite live public history and do not push. Temporary dry-runs may only happen under `/private/tmp/teotis-camera-history-safety-dryrun-*`.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash if any, changed files, verification commands/results, dry-run path if used, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 04-public-history-remediation-plan completed --commit <commit-sha-or-none> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 04-public-history-remediation-plan blocked --error "<specific blocker>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from 04-public-history-remediation-plan
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 05-export-diff-release-verification - Export Diff Release Verification

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code after dependencies complete.

---

**Mode**: package executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/packages/05-export-diff-release-verification.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/05-export-diff-release-verification.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. Do not push or rewrite history.

Before calling `advance`, you must:

- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash if any, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 05-export-diff-release-verification completed --commit <commit-sha-or-none> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 05-export-diff-release-verification blocked --error "<specific blocker>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from 05-export-diff-release-verification
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance
```

---

## Package: 99-finalize - Final Integration And Report

Copy this prompt into an agent, or let `orchestrate.sh advance/finalize` launch it after all functional packages complete.

---

**Mode**: finalize executor
**INDEX**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/INDEX.md`
**Package doc**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/packages/99-finalize.md`
**Coordinator status**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/99-finalize.md`
**Coordinator state**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/status/state.tsv`
**Orchestrator**: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh`

You are authorized to finalize only after every functional package is completed. Do not push to public remote. Do not force-push. Do not rewrite public history.

Before calling final `advance`, you must:

- Set coordinator status to `finalized` or `blocked`.
- Fill evidence: integration branch, mainline merge commit if created, verification summary, cleanup results, remaining public-release blockers.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 99-finalize finalized --integration "<integration result>" --verification "<command: result>" --cleanup "<cleanup result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state 99-finalize blocked --error "<specific blocker>"
```

Tail step:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from 99-finalize
```

If your agent platform cannot run local shell commands, report: "completed but advance not run", and tell the user to run:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance
```
