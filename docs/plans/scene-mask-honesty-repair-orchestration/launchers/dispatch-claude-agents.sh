#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel)"
PLAN_DIR="$REPO_ROOT/docs/plans/scene-mask-honesty-repair-orchestration"

exec bash "$PLAN_DIR/launchers/orchestrate.sh" start

