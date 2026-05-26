# Batch Instruction

`/batch` is not recommended for this work.

Reason: UI Animation V2 is not a mechanical repository-wide transform. The packages touch shared Android UI files, have ordered dependencies, and require per-package evidence plus final Codex visual/product audit.

Use the background dispatch script instead:

```bash
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh g0
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh g1
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh g2
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh g3
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh g4
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh audit
```

The dispatch script omits `--permission-mode` by default, so user-level Claude Code settings apply. If the user has configured `permissions.defaultMode: bypassPermissions` in `~/.claude/settings.json`, background sessions inherit that without the project hard-coding bypass mode.

To explicitly use auto mode, run the interactive opt-in once first:

```bash
bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh opt-in-auto
CLAUDE_PERMISSION_MODE=auto bash docs/plans/ui-animation-v2-fix-orchestration/launchers/dispatch-claude-agents.sh g0
```
