#!/usr/bin/env bash

set -euo pipefail

# Resolve project root from git or script location
if command -v git &>/dev/null; then
    project_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
fi

if [[ -z "${project_root:-}" ]]; then
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    project_root="$(cd "${script_dir}/.." && pwd)"
fi

project_root="$(cd "$project_root" && pwd)"

# Derive stable short workspace id from absolute root path
workspace_id="$(echo -n "$project_root" | shasum -a 256 | cut -c1-8)"

export OPENCAMERA_BUILD_ROOT="${HOME}/.codex-build/OpenCamera-${workspace_id}"

echo "[isolated-gradle] project_root=${project_root}"
echo "[isolated-gradle] build_root=${OPENCAMERA_BUILD_ROOT}"

exec ./gradlew "$@" --no-daemon
