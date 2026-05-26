#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/rendering-2-0-post-merge-followup-orchestration"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }

CLAUDE_VERSION="$(claude --version || true)"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_OPEN_AGENT_VIEW="${CLAUDE_OPEN_AGENT_VIEW:-0}"

echo "=== Claude Code ==="
echo "$CLAUDE_VERSION"
echo
echo "=== Launching background agents ==="

launch_agent() {
  local package_id="$1"
  local package_doc="$2"
  local status_file="$3"
  local name="$4"
  local prompt
  local output
  local status
  prompt="Read $PLAN_DIR/INDEX.md and $package_doc. Implement package $package_id. Write evidence to $status_file when done. Do NOT edit INDEX.md or other status files."
  echo "Launching $name"
  set +e
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    output="$(claude \
      --bg \
      --name "$name" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --permission-mode "$CLAUDE_PERMISSION_MODE" \
      --setting-sources "$CLAUDE_SETTING_SOURCES" \
      "$prompt" 2>&1)"
  else
    output="$(claude \
      --bg \
      --name "$name" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --setting-sources "$CLAUDE_SETTING_SOURCES" \
      "$prompt" 2>&1)"
  fi
  status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    echo "$output" >&2
    if [ "$CLAUDE_PERMISSION_MODE" = "auto" ] && grep -qi "requires opting in" <<<"$output"; then
      echo >&2
      echo "ERROR: Claude Code requires one interactive auto-mode opt-in before --bg can use --permission-mode auto." >&2
      echo "Run once interactively: claude --permission-mode auto" >&2
      echo "Or rerun this script without auto mode: CLAUDE_PERMISSION_MODE=default bash $PLAN_DIR/launchers/dispatch-claude-agents.sh" >&2
    fi
    return "$status"
  fi
  echo "$output"
}

launch_agent \
  "01-app-unit-test-gate-cleanup" \
  "$PLAN_DIR/packages/01-app-unit-test-gate-cleanup.md" \
  "$PLAN_DIR/status/01-app-unit-test-gate-cleanup.md" \
  "agent-render-v2-followup-01-app-gate"

launch_agent \
  "02-ledger-status-reconciliation" \
  "$PLAN_DIR/packages/02-ledger-status-reconciliation.md" \
  "$PLAN_DIR/status/02-ledger-status-reconciliation.md" \
  "agent-render-v2-followup-02-ledger"

echo
echo "=== Background agents launched ==="
echo "View them with:"
if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
else
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
fi
echo
echo "After all agents complete, run the integration audit from:"
echo "  $PLAN_DIR/validation/final-audit-prompt.md"

if [ "$CLAUDE_OPEN_AGENT_VIEW" = "1" ] && [ -t 1 ]; then
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    claude agents \
      --cwd "$REPO_ROOT" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --permission-mode "$CLAUDE_PERMISSION_MODE" \
      --setting-sources "$CLAUDE_SETTING_SOURCES"
  else
    claude agents \
      --cwd "$REPO_ROOT" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --setting-sources "$CLAUDE_SETTING_SOURCES"
  fi
fi
