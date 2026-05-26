# Status Files

Each package executor writes to its own status file. Do NOT edit `INDEX.md`.

Background sessions may be created with `claude --bg --name` and monitored in `claude agents`. The dispatch script omits `--permission-mode` by default so each task unit inherits the user's configured Claude Code permissions, including user-level `bypassPermissions` if enabled. If auto permission mode is desired, the user must opt in once interactively with `claude --permission-mode auto` before running the script with `CLAUDE_PERMISSION_MODE=auto`.

## How to use
1. Fill in your assigned `<package-id>.md`.
2. Include the complete evidence pack.
3. Do not edit other packages' status files.
