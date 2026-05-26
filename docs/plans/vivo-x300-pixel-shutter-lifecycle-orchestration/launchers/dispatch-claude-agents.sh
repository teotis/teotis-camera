#!/usr/bin/env bash
set -euo pipefail

# Backward compatibility wrapper. Prefer orchestrate.sh directly.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORCHESTRATE="$SCRIPT_DIR/orchestrate.sh"

echo "NOTE: dispatch-claude-agents.sh is deprecated. Forwarding to orchestrate.sh start."
echo "Use: bash $ORCHESTRATE start"
echo

exec bash "$ORCHESTRATE" start "$@"
