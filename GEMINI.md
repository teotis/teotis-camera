# Gemini CLI Instructions

This repository uses `AGENTS.md` as the single source of truth for all coding-agent instructions.

Before making changes, read and follow:

1. `AGENTS.md`
2. `~/.codex/RTK.md`（RTK 配置，个人环境特定）

Important local rule: run shell commands through `rtk`, for example `rtk rg --files` or `rtk ./scripts/verify_stage_7_observability.sh`.

Do not duplicate or reinterpret the project contract in this file. Update `AGENTS.md` first, then run `rtk ./scripts/verify_agent_instructions.sh`.
