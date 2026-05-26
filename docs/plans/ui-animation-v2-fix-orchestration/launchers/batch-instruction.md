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
