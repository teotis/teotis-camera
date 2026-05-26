#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAN_DIR/../../.." && pwd)"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/packages/00-mode-order-regression.md" ] || { echo "ERROR: package 00 doc missing"; exit 1; }
[ -f "$PLAN_DIR/status/00-mode-order-regression.md" ] || { echo "ERROR: package 00 status file missing"; exit 1; }

echo "=== UI Animation V2 Fix Orchestration ==="
echo "Plan: $PLAN_DIR"
echo
echo "Launching prerequisite package only: 00-mode-order-regression"
echo "After it completes and is merged/rebased, launch the next safe Agent View wave from:"
echo "$PLAN_DIR/launchers/agent-view-prompts.md"

claude --bg --name "ui-v2-00-mode-order" "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/00-mode-order-regression.md. Implement package 00-mode-order-regression only. Use an isolated worktree. Run the listed rtk verification commands. Write evidence to $PLAN_DIR/status/00-mode-order-regression.md when done. Do NOT edit INDEX.md or other status files. Do NOT force-push, hard reset, delete worktrees, use network, or touch forbidden paths."

echo
echo "=== Agent launched ==="
echo "Run 'claude agents' to check status."
echo "Next: launch packages 01 and 04 manually after package 00 is complete."
