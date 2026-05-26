#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLAN_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$PLAN_DIR/../../.." && pwd)"

CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-auto}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_OPEN_AGENT_VIEW="${CLAUDE_OPEN_AGENT_VIEW:-1}"
PHASE="${1:-g0}"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/launchers/agent-view-prompts.md" ] || { echo "ERROR: Agent View prompts missing"; exit 1; }

require_package() {
  local package_id="$1"
  [ -f "$PLAN_DIR/packages/$package_id.md" ] || { echo "ERROR: package doc missing: $package_id"; exit 1; }
  [ -f "$PLAN_DIR/status/$package_id.md" ] || { echo "ERROR: status file missing: $package_id"; exit 1; }
}

launch_agent() {
  local package_id="$1"
  local depends_on="$2"
  local name="ui-v2-${package_id}"
  local package_doc="$PLAN_DIR/packages/$package_id.md"
  local status_file="$PLAN_DIR/status/$package_id.md"
  local prompt

  require_package "$package_id"

  prompt="You are executing UI Animation V2 package $package_id for OpenCamera.

Repository: $REPO_ROOT
INDEX: $PLAN_DIR/INDEX.md
Package doc: $package_doc
Status file: $status_file
Dependencies: $depends_on

Read INDEX.md, your package doc, and AGENTS.md before editing. Use an isolated git worktree. In a worktree, run Gradle through rtk ./scripts/run_isolated_gradle.sh as required by AGENTS.md; in the main workspace, use rtk ./gradlew directly. Edit only allowed paths. Do not edit INDEX.md or any other package status file. Do not force-push, hard reset, delete branches/worktrees, use network, add secrets, or expand scope. If dependencies are incomplete or a stop gate is hit, write blocked evidence to your status file and stop.

When done, write the full evidence pack to $status_file: worktree path, branch, git status, git diff --stat, changed files, commands run, test results, commit/PR, unresolved risks, and self-certification that you only touched allowed paths."

  echo "Launching $name"
  claude \
    --bg \
    --name "$name" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    --permission-mode "$CLAUDE_PERMISSION_MODE" \
    --setting-sources "$CLAUDE_SETTING_SOURCES" \
    "$prompt"
}

open_agent_view() {
  echo
  echo "View background sessions with:"
  echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""

  if [ "$CLAUDE_OPEN_AGENT_VIEW" = "1" ] && [ -t 1 ]; then
    claude agents \
      --cwd "$REPO_ROOT" \
      --model "$CLAUDE_MODEL" \
      --effort "$CLAUDE_EFFORT" \
      --permission-mode "$CLAUDE_PERMISSION_MODE" \
      --setting-sources "$CLAUDE_SETTING_SOURCES"
  fi
}

echo "=== UI Animation V2 Background Agent Dispatch ==="
echo "Plan: $PLAN_DIR"
echo "Phase: $PHASE"
echo "Claude Code: $(claude --version || true)"
echo "Model: $CLAUDE_MODEL"
echo "Effort: $CLAUDE_EFFORT"
echo "Permission mode: $CLAUDE_PERMISSION_MODE"
echo "Setting sources: $CLAUDE_SETTING_SOURCES"
echo

case "$PHASE" in
  g0)
    launch_agent "00-mode-order-regression" "none"
    ;;
  g1)
    launch_agent "01-focus-exposure-feedback-v2" "00-mode-order-regression completed and merged/rebased"
    launch_agent "04-panel-transition-route-continuity" "00-mode-order-regression completed and merged/rebased"
    ;;
  g2)
    launch_agent "03-zoom-cockpit-v2" "00-mode-order-regression completed; preferably after G1 has started or completed"
    ;;
  g3)
    launch_agent "02-shutter-state-animation-v2" "00-mode-order-regression completed; coordinate after 03 if both edit CockpitSurfaceRenderer.kt"
    ;;
  g4)
    launch_agent "05-quick-panel-semantic-controls-v2" "00-mode-order-regression completed; packages 02 and 03 settled if they changed shared UI files"
    ;;
  audit|g5)
    launch_agent "99-integration-audit" "all implementation package status files completed"
    ;;
  all)
    echo "WARNING: launching all phases can create dependency-waiting agents. Prefer g0 -> g1 -> g2 -> g3 -> g4 -> audit."
    launch_agent "00-mode-order-regression" "none"
    launch_agent "01-focus-exposure-feedback-v2" "00-mode-order-regression completed and merged/rebased"
    launch_agent "04-panel-transition-route-continuity" "00-mode-order-regression completed and merged/rebased"
    launch_agent "03-zoom-cockpit-v2" "00-mode-order-regression completed; preferably after G1 has started or completed"
    launch_agent "02-shutter-state-animation-v2" "00-mode-order-regression completed; coordinate after 03 if both edit CockpitSurfaceRenderer.kt"
    launch_agent "05-quick-panel-semantic-controls-v2" "00-mode-order-regression completed; packages 02 and 03 settled if they changed shared UI files"
    ;;
  --print-only)
    echo "No agents launched. Use one of: g0, g1, g2, g3, g4, audit, all."
    exit 0
    ;;
  *)
    echo "ERROR: unknown phase '$PHASE'"
    echo "Use one of: g0, g1, g2, g3, g4, audit, all, --print-only"
    exit 1
    ;;
esac

echo
echo "=== Dispatch complete for phase: $PHASE ==="
open_agent_view
