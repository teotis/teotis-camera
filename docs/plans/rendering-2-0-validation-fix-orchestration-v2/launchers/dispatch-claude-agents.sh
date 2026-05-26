#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/rendering-2-0-validation-fix-orchestration-v2"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }

CLAUDE_VERSION="$(claude --version || true)"
CLAUDE_MODEL="${CLAUDE_MODEL:-sonnet}"
CLAUDE_EFFORT="${CLAUDE_EFFORT:-xhigh}"
CLAUDE_PERMISSION_MODE="${CLAUDE_PERMISSION_MODE:-auto}"
CLAUDE_SETTING_SOURCES="${CLAUDE_SETTING_SOURCES:-user,project,local}"
CLAUDE_OPEN_AGENT_VIEW="${CLAUDE_OPEN_AGENT_VIEW:-1}"

echo "=== Claude Code ==="
echo "$CLAUDE_VERSION"
echo
echo "=== Launching Rendering 2.0 V2 background agents ==="

launch_agent() {
  local package_id="$1"
  local package_doc="$2"
  local status_file="$3"
  local name="$4"
  local prompt
  prompt="Repo: $REPO_ROOT
Read $PLAN_DIR/INDEX.md and $package_doc. Implement ONLY package $package_id. Create or reuse an isolated git worktree. Use rtk for all shell commands; use scripts/run_isolated_gradle.sh for Gradle when in a worktree. Write evidence to $status_file when done. Do NOT edit INDEX.md or other status files. Do NOT force-push, hard reset, delete worktrees, use network access, or touch forbidden paths."

  echo "Launching $name"
  claude \
    --bg \
    --name "$name" \
    --worktree "$name" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    --permission-mode "$CLAUDE_PERMISSION_MODE" \
    --setting-sources "$CLAUDE_SETTING_SOURCES" \
    "$prompt"
}

launch_agent "01-postprocess-outer-guard" "$PLAN_DIR/packages/01-postprocess-outer-guard.md" "$PLAN_DIR/status/01-postprocess-outer-guard.md" "agent-render-v2-01-postprocess"
launch_agent "02-recipe-single-truth" "$PLAN_DIR/packages/02-recipe-single-truth.md" "$PLAN_DIR/status/02-recipe-single-truth.md" "agent-render-v2-02-recipe"
launch_agent "03-preview-fidelity-honesty" "$PLAN_DIR/packages/03-preview-fidelity-honesty.md" "$PLAN_DIR/status/03-preview-fidelity-honesty.md" "agent-render-v2-03-preview"
launch_agent "04-ledger-and-gate-honesty" "$PLAN_DIR/packages/04-ledger-and-gate-honesty.md" "$PLAN_DIR/status/04-ledger-and-gate-honesty.md" "agent-render-v2-04-ledger"

echo
echo "=== Background agents launched ==="
echo "View them with:"
echo "  claude agents --cwd \"$REPO_ROOT\" --model \"$CLAUDE_MODEL\" --effort \"$CLAUDE_EFFORT\" --permission-mode \"$CLAUDE_PERMISSION_MODE\" --setting-sources \"$CLAUDE_SETTING_SOURCES\""
echo
echo "After all package status files are completed, run the Codex audit prompt:"
echo "  $PLAN_DIR/validation/final-audit-prompt.md"

if [ "$CLAUDE_OPEN_AGENT_VIEW" = "1" ] && [ -t 1 ]; then
  claude agents \
    --cwd "$REPO_ROOT" \
    --model "$CLAUDE_MODEL" \
    --effort "$CLAUDE_EFFORT" \
    --permission-mode "$CLAUDE_PERMISSION_MODE" \
    --setting-sources "$CLAUDE_SETTING_SOURCES"
fi
