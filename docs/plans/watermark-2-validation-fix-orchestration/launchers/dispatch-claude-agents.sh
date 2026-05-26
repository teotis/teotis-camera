#!/usr/bin/env bash
set -euo pipefail

ROOT="/Volumes/Extreme_SSD/project/open_camera"
PLAN="$ROOT/docs/plans/watermark-2-validation-fix-orchestration"

claude --bg --name "agent-01-effect-preview-api-drift" "$(
  cat <<PROMPT
Mode: package executor
INDEX: $PLAN/INDEX.md
Package doc: $PLAN/packages/01-effect-preview-api-drift.md
Status file: $PLAN/status/01-effect-preview-api-drift.md

You may edit only the allowed paths in the package doc. Do not edit the orchestration INDEX, docs ledger, or other status files. Implement the package, run its verification commands through isolated Gradle when in a worktree, commit locally if possible, and write the full evidence pack to your status file.
PROMPT
)"

claude --bg --name "agent-02-blur-border-settings-guard" "$(
  cat <<PROMPT
Mode: package executor
INDEX: $PLAN/INDEX.md
Package doc: $PLAN/packages/02-blur-border-settings-guard.md
Status file: $PLAN/status/02-blur-border-settings-guard.md

You may edit only the allowed paths in the package doc. Do not edit the orchestration INDEX, docs ledger, or other status files. Implement the package, run its verification commands through isolated Gradle when in a worktree, commit locally if possible, and write the full evidence pack to your status file.
PROMPT
)"

echo "Launched Watermark 2.0 validation fix agents. Watch status files under $PLAN/status/."

