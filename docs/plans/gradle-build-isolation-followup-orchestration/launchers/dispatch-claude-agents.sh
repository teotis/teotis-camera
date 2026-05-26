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

CLAUDE_VERSION="$(claude --version || true)"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_OPEN_AGENT_VIEW="${CLAUDE_OPEN_AGENT_VIEW:-0}"
CLAUDE_PERMISSION_ARGS=()
if [[ -n "$CLAUDE_PERMISSION_MODE" ]]; then
  CLAUDE_PERMISSION_ARGS=(--permission-mode "$CLAUDE_PERMISSION_MODE")
fi

echo "=== Gradle Build Isolation Follow-Up Orchestration ==="
echo "Plan: $PLAN_DIR"
echo
echo "Claude Code version:"
echo "$CLAUDE_VERSION"
echo
echo "Claude model: $CLAUDE_MODEL"
echo "Claude effort: $CLAUDE_EFFORT"
if [[ -n "$CLAUDE_PERMISSION_MODE" ]]; then
  echo "Claude permission mode override: $CLAUDE_PERMISSION_MODE"
else
  echo "Claude permission mode: inherited from user/project Claude Code settings"
fi
echo "Claude setting sources: $CLAUDE_SETTING_SOURCES"
echo
echo "Manual Agent View prompts:"
echo "$PROMPT_FILE"
echo
echo "Background launch order:"
echo "1. 01-stage-script-isolation and 02-ledger-and-rules-restoration may run in parallel."
echo "2. 99-integration-audit runs after both status files are completed."
echo
echo "Tip: pass --print-only to print the planned commands without launching agents."

launch_agent() {
  local package_id="$1"
  local package_doc="$2"
  local status_file="$3"
  local name="agent-${package_id}"
  local prompt

  prompt="Read $PLAN_DIR/INDEX.md and $package_doc. Implement package $package_id. Write evidence to $status_file when done. Do NOT edit INDEX.md or other status files. Respect the File Ownership Map and Stop Gates."

  echo
  echo "Launching $name"
  if [[ "${1:-}" == "--print-only" ]]; then
    return 0
  fi

  set +e
  output="$(claude \
    --bg \
    --name "$name" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    "${CLAUDE_PERMISSION_ARGS[@]}" \
    --setting-sources "$CLAUDE_SETTING_SOURCES" \
    "$prompt" 2>&1)"
  status=$?
  set -e
  if [[ "$status" -ne 0 ]]; then
    echo "$output" >&2
    if [[ "$CLAUDE_PERMISSION_MODE" == "auto" && "$output" == *"requires opting in"* ]]; then
      echo >&2
      echo "ERROR: Claude Code requires one interactive auto-mode opt-in before --bg can use --permission-mode auto." >&2
      echo "Run once interactively: claude --permission-mode auto" >&2
      echo "Or rerun this script without a permission override so user settings apply." >&2
    fi
    return "$status"
  fi
  echo "$output"
}

if [[ "${1:-}" == "--print-only" ]]; then
  echo
  echo "Would launch:"
  echo "- agent-01-stage-script-isolation"
  echo "- agent-02-ledger-and-rules-restoration"
  exit 0
fi

launch_agent "01-stage-script-isolation" "$PLAN_DIR/packages/01-stage-script-isolation.md" "$PLAN_DIR/status/01-stage-script-isolation.md"
launch_agent "02-ledger-and-rules-restoration" "$PLAN_DIR/packages/02-ledger-and-rules-restoration.md" "$PLAN_DIR/status/02-ledger-and-rules-restoration.md"

echo
echo "=== Background agents launched ==="
echo "View them with:"
if [[ -n "$CLAUDE_PERMISSION_MODE" ]]; then
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
else
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
fi
echo
echo "After both agents complete, run the integration audit in:"
echo "$PLAN_DIR/validation/final-audit-prompt.md"

if [[ "$CLAUDE_OPEN_AGENT_VIEW" == "1" && -t 1 ]]; then
  claude agents \
    --cwd "$REPO_ROOT" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    "${CLAUDE_PERMISSION_ARGS[@]}" \
    --setting-sources "$CLAUDE_SETTING_SOURCES"
fi
