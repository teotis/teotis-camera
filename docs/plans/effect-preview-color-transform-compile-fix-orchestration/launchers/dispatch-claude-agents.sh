#!/usr/bin/env bash

set -euo pipefail

plan_dir="docs/plans/effect-preview-color-transform-compile-fix-orchestration"
agent_name="effect-preview-transform-compile"
prompt_file="${plan_dir}/launchers/agent-view-prompts.md"

cmd=(claude --bg --name "${agent_name}" "$(cat "${prompt_file}")")

if [[ "${1:-}" == "--print-only" ]]; then
  printf '%q ' "${cmd[@]}"
  printf '\n'
  exit 0
fi

exec "${cmd[@]}"
