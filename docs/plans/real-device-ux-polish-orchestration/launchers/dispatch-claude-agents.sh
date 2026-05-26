#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAN_DIR/../../.." && pwd)"

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_OPEN_AGENT_VIEW="${CLAUDE_OPEN_AGENT_VIEW:-0}"
PHASE="${1:-g0}"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/launchers/agent-prompts.md" ] || { echo "ERROR: Agent prompts missing"; exit 1; }

require_package() {
  local package_id="$1"
  [ -f "$PLAN_DIR/packages/$package_id.md" ] || { echo "ERROR: package doc missing: $package_id"; exit 1; }
  [ -f "$PLAN_DIR/status/$package_id.md" ] || { echo "ERROR: status file missing: $package_id"; exit 1; }
}

launch_agent() {
  local package_id="$1"
  local depends_on="$2"
  local name="ux-polish-${package_id}"
  local package_doc="$PLAN_DIR/packages/$package_id.md"
  local status_file="$PLAN_DIR/status/$package_id.md"
  local prompt

  require_package "$package_id"

  prompt="You are executing Real Device UX Polish package $package_id for OpenCamera.

Repository: $REPO_ROOT
INDEX: $PLAN_DIR/INDEX.md
Package doc: $package_doc
Status file: $status_file
Dependencies: $depends_on

Read AGENTS.md, INDEX.md, and your package doc before editing. Create or reuse an isolated git worktree. In a worktree, run Gradle through rtk ./scripts/run_isolated_gradle.sh as required by AGENTS.md; in the main workspace, use rtk ./gradlew directly. Edit only allowed paths. Do not edit INDEX.md or any other package status file. Do not force-push, hard reset, delete branches/worktrees, use network, add secrets, or expand scope. The main workspace currently has unrelated dirty/conflict state; do not clean unrelated conflicts. If dependencies are incomplete or a stop gate is hit, write blocked evidence to your status file and stop.

When done, write the full evidence pack to $status_file: worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, acceptance criteria status, unresolved risks, and self-certification that you only touched allowed paths."

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
      echo "Or rerun this script without a permission override so user-level settings apply: unset CLAUDE_PERMISSION_MODE; bash launchers/dispatch-claude-agents.sh" >&2
    fi
    return "$status"
  fi
  echo "$output"
}

open_agent_view() {
  echo
  echo "View background sessions with:"
  if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
    echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
  else
    echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
  fi

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
}

echo "=== Real Device UX Polish Background Agent Dispatch ==="
echo "Plan: $PLAN_DIR"
echo "Phase: $PHASE"
echo "Claude Code: $(claude --version || true)"
echo "Model: $CLAUDE_MODEL"
echo "Effort: $CLAUDE_EFFORT"
if [ -n "$CLAUDE_PERMISSION_MODE" ]; then
  echo "Permission mode override: $CLAUDE_PERMISSION_MODE"
else
  echo "Permission mode: inherited from Claude Code settings"
fi
echo "Setting sources: $CLAUDE_SETTING_SOURCES"
echo "Tip: leave CLAUDE_PERMISSION_MODE empty to inherit user settings, including user-enabled bypassPermissions."
if [ "$CLAUDE_PERMISSION_MODE" = "auto" ]; then
  echo
  echo "Auto mode requested. Claude Code requires user opt-in before --bg can create auto-mode sessions."
  echo "If you have not opted in yet, run: bash $0 opt-in-auto"
fi
echo

case "$PHASE" in
  opt-in-auto)
    echo "Opening one interactive Claude Code session for auto-mode opt-in."
    echo "Accept the auto-mode prompt, then exit Claude Code and rerun the desired dispatch phase."
    exec claude --permission-mode auto
    ;;
  g0)
    launch_agent "00-mode-entry-visibility" "none"
    ;;
  g1)
    launch_agent "03-quick-panel-outside-dismiss" "00-mode-entry-visibility completed or app tests known unblocked"
    launch_agent "05-dev-log-storage-governance" "none; ideally after 00 if app tests were blocked"
    ;;
  g2)
    launch_agent "01-style-copy-noise-cleanup" "00-mode-entry-visibility completed"
    ;;
  g3)
    launch_agent "02-settings-third-level-navigation" "00-mode-entry-visibility completed; coordinate after 01 if shared render/text files changed"
    ;;
  g4)
    launch_agent "04-persistence-reset-unification" "01, 02, and 03 completed and merged/rebased"
    ;;
  audit|g5)
    launch_agent "99-integration-audit" "all implementation package status files completed"
    ;;
  start|all)
    echo "WARNING: launching all phases can create dependency-waiting agents. Prefer g0 -> g1 -> g2 -> g3 -> g4 -> audit."
    launch_agent "00-mode-entry-visibility" "none"
    launch_agent "03-quick-panel-outside-dismiss" "00-mode-entry-visibility completed or app tests known unblocked"
    launch_agent "05-dev-log-storage-governance" "none; ideally after 00 if app tests were blocked"
    launch_agent "01-style-copy-noise-cleanup" "00-mode-entry-visibility completed"
    launch_agent "02-settings-third-level-navigation" "00-mode-entry-visibility completed; coordinate after 01 if shared render/text files changed"
    launch_agent "04-persistence-reset-unification" "01, 02, and 03 completed and merged/rebased"
    ;;
  --print-only)
    echo "No agents launched. Use one of: opt-in-auto, g0, g1, g2, g3, g4, audit, all."
    exit 0
    ;;
  *)
    echo "ERROR: unknown phase '$PHASE'"
    echo "Use one of: opt-in-auto, g0, g1, g2, g3, g4, audit, all, --print-only"
    exit 1
    ;;
esac

echo
echo "=== Dispatch complete for phase: $PHASE ==="
open_agent_view
