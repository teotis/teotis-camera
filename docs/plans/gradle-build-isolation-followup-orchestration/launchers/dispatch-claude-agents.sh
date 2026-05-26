#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/gradle-build-isolation-followup-orchestration"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/packages/01-stage-script-isolation.md" ] || { echo "ERROR: package 01 not found"; exit 1; }
[ -f "$PLAN_DIR/packages/02-ledger-and-rules-restoration.md" ] || { echo "ERROR: package 02 not found"; exit 1; }

echo "=== Launching Gradle build-isolation follow-up agents ==="

claude --bg --name "01-stage-script-isolation" "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/01-stage-script-isolation.md. Implement package 01-stage-script-isolation. Write evidence to $PLAN_DIR/status/01-stage-script-isolation.md when done. Do NOT edit INDEX.md."
claude --bg --name "02-ledger-and-rules-restoration" "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/02-ledger-and-rules-restoration.md. Implement package 02-ledger-and-rules-restoration. Write evidence to $PLAN_DIR/status/02-ledger-and-rules-restoration.md when done. Do NOT edit INDEX.md."

echo "=== All implementation agents launched ==="
echo "Run 'claude agents' to check status."
echo "After both agents complete, run the integration audit in $PLAN_DIR/validation/final-audit-prompt.md."

