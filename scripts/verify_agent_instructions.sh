#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

write_expected_wrapper() {
    local title="$1"
    local target="$2"

    cat > "${target}" <<EOF
# ${title} Instructions

This repository uses \`AGENTS.md\` as the single source of truth for all coding-agent instructions.

Before making changes, read and follow:

1. \`AGENTS.md\`
2. \`/Users/dingren/.codex/RTK.md\`

Important local rule: run shell commands through \`rtk\`, for example \`rtk rg --files\` or \`rtk ./scripts/verify_stage_7_observability.sh\`.

Do not duplicate or reinterpret the project contract in this file. Update \`AGENTS.md\` first, then run \`rtk ./scripts/verify_agent_instructions.sh\`.
EOF
}

check_wrapper() {
    local file="$1"
    local title="$2"
    local expected="${TMP_DIR}/${file}"

    write_expected_wrapper "${title}" "${expected}"

    if ! cmp -s "${expected}" "${ROOT_DIR}/${file}"; then
        echo "${file} is out of sync with the canonical wrapper template." >&2
        echo "Update AGENTS.md for project rules, keep ${file} as a thin wrapper, then rerun this script." >&2
        diff -u "${expected}" "${ROOT_DIR}/${file}" >&2 || true
        exit 1
    fi
}

check_agents_section() {
    local heading="$1"

    if ! grep -Fq "## ${heading}" "${ROOT_DIR}/AGENTS.md"; then
        echo "AGENTS.md is missing required shared section: ${heading}" >&2
        exit 1
    fi
}

check_wrapper "CLAUDE.md" "Claude Code"
check_wrapper "GEMINI.md" "Gemini CLI"

check_agents_section "Local Command Rule"
check_agents_section "Architecture Contract"
check_agents_section "Current Stage"
check_agents_section "Required Working Loop"
check_agents_section "Verification"
check_agents_section "Documentation Rules"
check_agents_section "Edit Constraints"

if ! grep -Fq "/Volumes/Extreme_SSD/project/open_camera" "${ROOT_DIR}/AGENTS.md"; then
    echo "AGENTS.md must name /Volumes/Extreme_SSD/project/open_camera as the authoritative workspace." >&2
    exit 1
fi

echo "Agent instruction files are synchronized with AGENTS.md."
