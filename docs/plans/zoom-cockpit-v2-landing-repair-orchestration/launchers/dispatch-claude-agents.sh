#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR" && git rev-parse --show-toplevel)"

exec bash "$REPO_ROOT/docs/plans/zoom-cockpit-v2-landing-repair-orchestration/launchers/orchestrate.sh" start "$@"

