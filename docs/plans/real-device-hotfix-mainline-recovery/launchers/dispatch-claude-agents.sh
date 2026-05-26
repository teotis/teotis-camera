#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/real-device-hotfix-mainline-recovery"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/packages/01-watermark-mainline-recovery.md" ] || { echo "ERROR: package 01 missing"; exit 1; }
[ -f "$PLAN_DIR/packages/02-zoom-scaleend-mainline-recovery.md" ] || { echo "ERROR: package 02 missing"; exit 1; }
[ -f "$PLAN_DIR/packages/03-shutter-visual-closure.md" ] || { echo "ERROR: package 03 missing"; exit 1; }

CLAUDE_VERSION="$(claude --version || true)"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-default}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_OPEN_AGENT_VIEW="${CLAUDE_OPEN_AGENT_VIEW:-1}"

echo "=== Claude Code ==="
echo "$CLAUDE_VERSION"
echo
echo "Permission mode: $CLAUDE_PERMISSION_MODE"
if [ "$CLAUDE_PERMISSION_MODE" = "auto" ]; then
  echo "Auto mode requires prior interactive opt-in. If launch fails, run:"
  echo "  claude --permission-mode auto"
  echo "Then rerun this script, or use the default mode:"
  echo "  CLAUDE_PERMISSION_MODE=default bash $0"
  echo
fi
echo "=== Launching background agents ==="

launch_agent() {
  local package_id="$1"
  local package_doc="$2"
  local status_file="$3"
  local name="agent-${package_id}"
  local prompt
  local output
  local status
  prompt="Read $PLAN_DIR/INDEX.md and $package_doc. Implement package $package_id. Write evidence to $status_file when done. Do NOT edit INDEX.md or other status files. Rebase on current main before final verification and merge/PR when complete."
  echo "Launching $name"
  set +e
  output="$(claude \
    --bg \
    --name "$name" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    --permission-mode "$CLAUDE_PERMISSION_MODE" \
    --setting-sources "$CLAUDE_SETTING_SOURCES" \
    "$prompt" 2>&1)"
  status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    echo "$output" >&2
    if [ "$CLAUDE_PERMISSION_MODE" = "auto" ]; then
      echo >&2
      echo "ERROR: Claude Code background launch did not create a session." >&2
      echo "Auto mode requires one interactive opt-in before --bg can start with --permission-mode auto." >&2
      echo "Run once interactively:" >&2
      echo "  claude --permission-mode auto" >&2
      echo "Or rerun without auto mode:" >&2
      echo "  CLAUDE_PERMISSION_MODE=default bash $0" >&2
    fi
    return "$status"
  fi
  echo "$output"
}

launch_agent "01-watermark-mainline-recovery" "$PLAN_DIR/packages/01-watermark-mainline-recovery.md" "$PLAN_DIR/status/01-watermark-mainline-recovery.md"
launch_agent "02-zoom-scaleend-mainline-recovery" "$PLAN_DIR/packages/02-zoom-scaleend-mainline-recovery.md" "$PLAN_DIR/status/02-zoom-scaleend-mainline-recovery.md"
launch_agent "03-shutter-visual-closure" "$PLAN_DIR/packages/03-shutter-visual-closure.md" "$PLAN_DIR/status/03-shutter-visual-closure.md"

echo
echo "=== Background agents launched ==="
echo "View them with:"
echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
echo
echo "After all agents complete, run the integration audit:"
echo "  $PLAN_DIR/validation/final-audit-prompt.md"

if [ "$CLAUDE_OPEN_AGENT_VIEW" = "1" ] && [ -t 1 ]; then
  claude agents \
    --cwd "$REPO_ROOT" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    --permission-mode "$CLAUDE_PERMISSION_MODE" \
    --setting-sources "$CLAUDE_SETTING_SOURCES"
fi
