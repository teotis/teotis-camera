# Status Files

Each package executor writes to its own status file. Do NOT edit INDEX.md.

## How to use

1. Copy `package-status-template.md` to `<package-id>.md`.
2. Fill in each section as you complete the package.
3. Do not edit other packages' status files.

## Background Session Note

If `dispatch-claude-agents.sh` is launched with `CLAUDE_PERMISSION_MODE=auto` before the user has opted in interactively, Claude Code can fail before creating a background session. In that case Agents View will be empty and the package status file should remain `pending`. Run `claude --permission-mode auto` once interactively, or rerun the dispatcher with the default permission mode:

```bash
CLAUDE_PERMISSION_MODE=default rtk bash docs/plans/real-device-ui-upgrade-remediation/launchers/dispatch-claude-agents.sh
```
