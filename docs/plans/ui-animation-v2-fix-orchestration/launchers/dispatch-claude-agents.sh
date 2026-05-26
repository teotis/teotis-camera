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
[ -f "$PLAN_DIR/packages/00-mode-order-regression.md" ] || { echo "ERROR: package 00 doc missing"; exit 1; }
[ -f "$PLAN_DIR/status/00-mode-order-regression.md" ] || { echo "ERROR: package 00 status file missing"; exit 1; }
[ -f "$PROMPT_FILE" ] || { echo "ERROR: Agent View prompts missing at $PROMPT_FILE"; exit 1; }

echo "=== UI Animation V2 Fix Orchestration ==="
echo "Plan: $PLAN_DIR"
echo
echo "Claude Code version:"
claude --version
echo
echo "Claude Code 2.1 uses Agent View through 'claude agents'."
echo "This helper does not dispatch all packages automatically."
echo
echo "Open Agent View, then paste package prompts from:"
echo "$PROMPT_FILE"
echo
echo "Launch order:"
echo "1. 00-mode-order-regression"
echo "2. 01-focus-exposure-feedback-v2 and 04-panel-transition-route-continuity"
echo "3. 03-zoom-cockpit-v2"
echo "4. 02-shutter-state-animation-v2"
echo "5. 05-quick-panel-semantic-controls-v2"
echo "6. 99-integration-audit"
echo
echo "Tip: pass --print-only to stop here without opening Agent View."

if [[ "${1:-}" == "--print-only" ]]; then
  exit 0
fi

echo
echo "=== Opening Claude Code Agent View ==="
exec claude agents --cwd "$REPO_ROOT" --permission-mode default --effort high
