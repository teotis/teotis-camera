#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/real-device-hotfix-rework"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/packages/01-shutter-data-boundary.md" ] || { echo "ERROR: package 01 missing"; exit 1; }
[ -f "$PLAN_DIR/packages/02-watermark-mainline.md" ] || { echo "ERROR: package 02 missing"; exit 1; }
[ -f "$PLAN_DIR/packages/03-zoom-scaleend-sync.md" ] || { echo "ERROR: package 03 missing"; exit 1; }

echo "=== Launching real-device hotfix rework agents ==="

claude --bg --name "agent-01-shutter-boundary" \
  "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/01-shutter-data-boundary.md. Implement package 01-shutter-data-boundary. Write evidence to $PLAN_DIR/status/01-shutter-data-boundary.md when done. Do NOT edit INDEX.md."

claude --bg --name "agent-02-watermark-mainline" \
  "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/02-watermark-mainline.md. Implement package 02-watermark-mainline. Write evidence to $PLAN_DIR/status/02-watermark-mainline.md when done. Do NOT edit INDEX.md."

claude --bg --name "agent-03-zoom-sync" \
  "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/03-zoom-scaleend-sync.md. Implement package 03-zoom-scaleend-sync. Write evidence to $PLAN_DIR/status/03-zoom-scaleend-sync.md when done. Do NOT edit INDEX.md."

echo "=== All agents launched ==="
echo "Run 'claude agents' to check status."
echo "After all agents complete, run the integration audit:"
echo "$PLAN_DIR/validation/final-audit-prompt.md"
