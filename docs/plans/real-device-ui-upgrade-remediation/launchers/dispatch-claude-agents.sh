#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
PLAN_DIR="$REPO_ROOT/docs/plans/real-device-ui-upgrade-remediation"

cd "$REPO_ROOT"

command -v claude >/dev/null 2>&1 || { echo "ERROR: claude CLI not found"; exit 1; }
git rev-parse --git-dir >/dev/null 2>&1 || { echo "ERROR: not a git repository"; exit 1; }
[ -f "$PLAN_DIR/INDEX.md" ] || { echo "ERROR: INDEX.md not found at $PLAN_DIR/INDEX.md"; exit 1; }
[ -f "$PLAN_DIR/packages/01-effect-test-contract.md" ] || { echo "ERROR: package 01 not found"; exit 1; }
[ -f "$PLAN_DIR/packages/05-focal-slider-interaction.md" ] || { echo "ERROR: package 05 not found"; exit 1; }

echo "=== Launching Group 1 agents ==="
echo "Only G1 is launched by script. Launch later groups after dependencies pass."

claude --bg --name "agent-01-effect-contract" \
  "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/01-effect-test-contract.md. Implement package 01-effect-test-contract. Write evidence to $PLAN_DIR/status/01-effect-test-contract.md when done. Do NOT edit INDEX.md."

claude --bg --name "agent-05-focal-slider" \
  "Read $PLAN_DIR/INDEX.md and $PLAN_DIR/packages/05-focal-slider-interaction.md. Implement package 05-focal-slider-interaction. Write evidence to $PLAN_DIR/status/05-focal-slider-interaction.md when done. Do NOT edit INDEX.md."

echo "=== Group 1 agents launched ==="
echo "Run 'claude agents' to check status."
echo "After package 01 passes, launch package 02 and package 03 from launchers/agent-view-prompts.md."
echo "After package 03 passes, launch package 04."
echo "After all packages complete, run the integration audit."
