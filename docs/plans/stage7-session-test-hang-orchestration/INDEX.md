# Stage 7 Session Test Hang Repair - Orchestration Index

## Goal

Investigate and repair the new mainline verification risk where `SessionDiagnosticsTest` passes but `DefaultCameraSessionTest` can hang inside `:core:session:test` after the real-device UI remediation merge. This package must distinguish product regressions from Gradle/build-root contamination or leaked coroutine/test resources, then restore a deterministic focused verification path without changing camera runtime behavior outside the proven root cause.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry 01-session-test-hang-diagnosis-and-repair`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/stage7-session-test-hang/integration`
- Functional package branches: `agent/stage7-session-test-hang/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-session-test-hang-diagnosis-and-repair | none | status | completed | 1 |
| 99-finalize | 01-session-test-hang-diagnosis-and-repair | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-session-test-hang-diagnosis-and-repair`
- Code dependency policy: status dependency; `99-finalize` merges the functional branch to `agent/stage7-session-test-hang/integration`.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- The hang cannot be reproduced twice under isolated build conditions.
- The fix requires broad session kernel redesign or Stage boundary changes.

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-session-test-hang-diagnosis-and-repair.md](packages/01-session-test-hang-diagnosis-and-repair.md) | implementation/debugging agent | none | no parallel | Reproduce, isolate, and repair the `DefaultCameraSessionTest` hang |
| [99-finalize.md](packages/99-finalize.md) | finalizer agent | all functional packages | no parallel | Validate evidence, merge the functional branch, run integration verification, merge to mainline, and write the final report |

## Claude Background Permission Notes

- `claude --bg --name` is valid for creating Claude Code background sessions that appear in Agents View.
- Generated scripts MUST default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, generated background sessions inherit that mode without the repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first. Without that opt-in, the first `claude --bg` command can fail before creating a session, leaving Agents View empty.
- Do not use `--dangerously-skip-permissions`.

## Launch Options

- Manual: copy prompts from `launchers/agent-prompts.md`.
- Script: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/stage7-session-test-hang-orchestration/launchers/orchestrate.sh start`.
- Downstream dispatch is triggered only by package tail calls to `advance`, or by manual `advance` fallback.
