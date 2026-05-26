#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/rendering-2-0-validation-fix-orchestration"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }

echo "=== Launching Rendering 2.0 validation-fix agents ==="

claude --bg --name "01-postprocess-outer-guard" "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/01-postprocess-outer-guard.md. Implement package 01-postprocess-outer-guard. Write evidence to $PLAN_DIR/status/01-postprocess-outer-guard.md when done. Do NOT edit INDEX.md."
claude --bg --name "02-recipe-single-truth" "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/02-recipe-single-truth.md. Implement package 02-recipe-single-truth. Write evidence to $PLAN_DIR/status/02-recipe-single-truth.md when done. Do NOT edit INDEX.md."
claude --bg --name "04-ledger-and-gate-honesty" "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/04-ledger-and-gate-honesty.md. Implement package 04-ledger-and-gate-honesty. Write evidence to $PLAN_DIR/status/04-ledger-and-gate-honesty.md when done. Do NOT edit INDEX.md."

echo "=== Group 1 launched ==="
echo "Launch package 03 from Agent View after package 02 reports whether recipe/preview model names changed."
echo "Run 'claude agents' to check status."

