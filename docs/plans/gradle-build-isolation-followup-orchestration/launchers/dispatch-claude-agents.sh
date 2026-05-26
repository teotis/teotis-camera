#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAN_DIR/../../.." && pwd)"
PROMPT_FILE="$PLAN_DIR/launchers/agent-view-prompts.md"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/packages/01-stage-script-isolation.md" ] || { echo "ERROR: package 01 not found"; exit 1; }
[ -f "$PLAN_DIR/packages/02-ledger-and-rules-restoration.md" ] || { echo "ERROR: package 02 not found"; exit 1; }
[ -f "$PROMPT_FILE" ] || { echo "ERROR: Agent View prompts missing at $PROMPT_FILE"; exit 1; }

echo "=== Gradle Build Isolation Follow-Up Orchestration ==="
echo "Plan: $PLAN_DIR"
echo
echo "Claude Code version:"
claude --version
echo
echo "Claude Code 2.1 uses Agent View through 'claude agents'."
echo "This helper does not auto-dispatch packages with the old 'claude --bg' flow."
echo
echo "Open Agent View, then paste package prompts from:"
echo "$PROMPT_FILE"
echo
echo "Launch order:"
echo "1. 01-stage-script-isolation and 02-ledger-and-rules-restoration may run in parallel."
echo "2. 99-integration-audit runs after both status files are completed."
echo
echo "Tip: pass --print-only to stop here without opening Agent View."

if [[ "${1:-}" == "--print-only" ]]; then
  exit 0
fi

echo
echo "=== Opening Claude Code Agent View ==="
exec claude agents --cwd "$REPO_ROOT" --permission-mode default --effort high
