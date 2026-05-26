# Stage 7 Session Test Hang Repair - Orchestration Index

## Goal

Investigate and repair the new mainline verification risk where `SessionDiagnosticsTest` passes but `DefaultCameraSessionTest` can hang inside `:core:session:test` after the real-device UI remediation merge. This package must distinguish product regressions from Gradle/build-root contamination or leaked coroutine/test resources, then restore a deterministic focused verification path without changing camera runtime behavior outside the proven root cause.

## Execution Mode Recommendation

- Recommended mode: SINGLE_AGENT
- Why: the symptom is one tightly scoped Stage 7 verification hang in `core:session`; concurrency would add noise and increase the chance of build-root/test-daemon contamination.
- Alternatives rejected:
  - AGENT_VIEW - not enough independent packages to justify multiple agents.
  - BACKGROUND_AGENT_SCRIPT - included only as an optional launcher for a single background session.
  - BATCH - this is not a mechanical repo-wide migration.
  - AGENT_TEAM - this is a deterministic debugging and repair task, not multi-hypothesis research.
- Max parallel agents: 1
- Codex-retained work: final audit of evidence, focused verification, and decision whether the fix is safe to merge.

## Execution Authorization

You (the external agent) are authorized to do the following WITHOUT asking for confirmation:
- Read the plan, index, and all referenced package documents.
- Create or reuse an isolated git worktree for implementation.
- Make scope-bounded edits, add/update tests, and update docs as described in your assigned package.
- Run the listed verification commands.
- Commit locally within the worktree branch.
- Merge, push, or create PRs for worktree branches (incremental, non-destructive operations).
- Write to ONLY your assigned `status/<package-id>.md` file - never edit INDEX.md or another package's status file.

## Stop Gates - Must Ask

STOP and ask the user before:
- Crossing Stage boundaries or changing Stage 7 scope.
- Making product behavior changes unrelated to the confirmed hang root cause.
- Destructive git operations: force-push, hard reset, deleting branches/worktrees.
- Network access, external API calls, or adding secrets/credentials.
- Overwriting unrelated dirty changes outside your assigned Allowed Paths.
- Blaming a product regression before rerunning the smallest failed command in an isolated build root.
- Killing processes that are not clearly owned by your verification run.

## Completion Policy

After completing your assigned package:
- Write your evidence pack to `status/<package-id>.md` - do NOT edit INDEX.md.
- Merge, push, or create a PR as the final step - no need to ask for non-destructive local integration.
- Report: what changed, test results, merge/PR status, and branch/worktree path.
- Do NOT delete the worktree unless explicitly instructed.

## Concurrency Plan

| Group | Packages | Can Run In Parallel | Must Wait For | Conflict Risk |
|---|---|---|---|---|
| G1 | 01-session-test-hang-diagnosis-and-repair | no | none | caution - session tests and build isolation evidence |
| G2 | 99-integration-audit | no | 01 completed | low - audit only |

## Dependency Graph

| Package | Depends On | Unlocks | Ready When | Notes |
|---|---|---|---|---|
| 01-session-test-hang-diagnosis-and-repair | none | 99-integration-audit | immediately | single implementation/debugging loop |
| 99-integration-audit | 01-session-test-hang-diagnosis-and-repair | none | status/01 shows completed or blocked with evidence | Codex retained final decision |

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `core/session/src/test/**/DefaultCameraSessionTest.kt` | 01 | 99 |
| `core/session/src/test/**` focused helpers/fixtures | 01 | 99 |
| `core/session/src/main/**` only if root cause is confirmed | 01 | 99 |
| `scripts/run_isolated_gradle.sh` only if isolation behavior is root cause | 01 | 99 |
| `docs/plans/stage7-session-test-hang-orchestration/status/01-session-test-hang-diagnosis-and-repair.md` | 01 | 99 |
| `docs/plans/stage7-session-test-hang-orchestration/status/99-integration-audit.md` | 99 | 01 |

## Agent Budget

- Recommended Claude Code agents: 1 implementation/debugging agent + Codex audit
- Max parallel agents: 1
- Codex usage: final audit only
- When to pause: if the hang cannot be reproduced twice under isolated build conditions, or if the fix requires broad session kernel redesign.

## Dispatch Plan

| Package | Mode | Agent Name | Prompt File | Status File | Depends On | Auto-Dispatch Rule |
|---|---|---|---|---|---|---|
| 01-session-test-hang-diagnosis-and-repair | single-agent | stage7-session-hang-01 | launchers/agent-view-prompts.md#package-01-session-test-hang-diagnosis-and-repair | status/01-session-test-hang-diagnosis-and-repair.md | none | ready immediately |
| 99-integration-audit | codex | - | validation/final-audit-prompt.md | status/99-integration-audit.md | 01 | manual Codex audit |

## Status Ledger

| Package | Agent | Status | Worktree | Commit/PR | Verification | Evidence |
|---|---|---|---|---|---|---|
| 01-session-test-hang-diagnosis-and-repair | - | pending | - | - | - | - |
| 99-integration-audit | - | pending | - | - | - | - |

## Merge Strategy

- Merge order: 01 then 99 audit.
- Rebase policy: rebase on latest main before merge/PR.
- Conflict owner: 01 owns any `core/session` test conflict.
- Final integration agent: Codex.
- Do not delete worktrees until: final audit passes and the user confirms cleanup.

## Evidence Pack Required From Each Agent

Each agent MUST write to its own `status/<package-id>.md` file after completion.
Do NOT edit INDEX.md directly - that causes concurrent-write conflicts.

Evidence pack must include:
- [ ] worktree path
- [ ] branch name
- [ ] git status
- [ ] git diff --stat
- [ ] changed files (full list)
- [ ] commands run (verification commands + output summary)
- [ ] test result summary (pass/fail/hang counts)
- [ ] process evidence when a hang is observed
- [ ] commit hash / PR link
- [ ] unresolved risks
- [ ] whether it touched only allowed paths (self-certify)

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-session-test-hang-diagnosis-and-repair.md](packages/01-session-test-hang-diagnosis-and-repair.md) | implementation/debugging agent | none | no parallel | Reproduce, isolate, and repair the `DefaultCameraSessionTest` hang |
| [99-integration-audit.md](packages/99-integration-audit.md) | Codex retained | 01 | no parallel | Validate root cause, scope, and verification honesty |

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts MUST default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit that mode without the repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first. Without that opt-in, the first `claude --bg` command can fail before creating a session, leaving Agents View empty.
- Do not use `--dangerously-skip-permissions`.

## Launch Options

- Option A: Agent View manual dispatch - copy prompts from `launchers/agent-view-prompts.md`.
- Option B: background single-agent script - run `rtk bash docs/plans/stage7-session-test-hang-orchestration/launchers/dispatch-claude-agents.sh g1`.
- Option C: final integration audit - give `validation/final-audit-prompt.md` to Codex after package 01 writes evidence.
