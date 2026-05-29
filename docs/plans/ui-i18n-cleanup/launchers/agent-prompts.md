# Agent Prompts

## Package: 01-strings-xml - strings.xml 资源英文清理

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/01-strings-xml.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/01-strings-xml.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 01-strings-xml`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file. If you create/use an implementation worktree, do not rely on status files inside that worktree; write the coordinator status path above.

Do not attempt external-assist work inside a Claude package. If you discover a package requires a physical device, user-owned account, secret, external approval, or human-only judgment that was not declared, mark the package `blocked` with a precise recovery hint instead of improvising or claiming completion.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 01-strings-xml completed --commit <commit-sha> --verification "<command: result>"
```

For a blocker:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 01-strings-xml blocked \
  --error "<specific blocker>" \
  --failed-command "<failed command, if any>" \
  --conflict-files "<comma-separated files, if any>" \
  --log-summary "<short log summary>" \
  --recovery-hint "<specific next action>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 01-strings-xml
```

---

## Package: 02-layout-xml - 布局 XML 硬编码英文清理

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/02-layout-xml.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/02-layout-xml.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 02-layout-xml`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 02-layout-xml completed --commit <commit-sha> --verification "<command: result>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 02-layout-xml
```

---

## Package: 03-kotlin-hardcoded - Kotlin 源码硬编码英文清理

Copy this prompt into an agent, or let `orchestrate.sh start/advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/03-kotlin-hardcoded.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/03-kotlin-hardcoded.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 03-kotlin-hardcoded`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

You may edit only the allowed paths in the package doc. Do not edit INDEX.md or another package status file.

Before calling `advance`, you must:
- Set coordinator status to `completed` or `blocked`.
- Fill evidence: worktree, branch, base commit, commit hash, changed files, verification commands/results, risks.
- Update the machine-readable state row only through the orchestrator:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 03-kotlin-hardcoded completed --commit <commit-sha> --verification "<command: result>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 03-kotlin-hardcoded
```

---

## Package: 99-finalize - 集成验证与合并

Copy this prompt into an agent, or let `orchestrate.sh advance` launch it for Claude Code.

---

**Mode**: package executor
**INDEX**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/INDEX.md
**Package doc**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/packages/99-finalize.md
**Coordinator status**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/99-finalize.md
**Coordinator state**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/status/state.tsv
**Scratch path**: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh scratch-path 99-finalize`
**Orchestrator**: /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh

This is the finalize package. It runs only after all functional packages are completed. It verifies, integrates, and merges.

Before calling `advance`, you must:
- Verify all functional packages completed.
- Run `bash launchers/orchestrate.sh verify-finalize`.
- Merge all package branches into integration branch.
- Run integration verification.
- Merge integration branch back to mainline.
- Write FINAL_REPORT.md.
- Delete recorded worktrees/branches.
- Mark 99-finalize as finalized.

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh mark-state 99-finalize finalized --verification "<command: result>"
```

Tail step:
```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/ui-i18n-cleanup/launchers/orchestrate.sh advance --from 99-finalize
```

---
