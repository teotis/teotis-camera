# Feature Module Direct Tests - Orchestration Index

## Goal

Add direct unit-test coverage for the seven feature mode plugins so each plugin has local tests for support checks, effect specs, post-process specs, metadata tags, capture strategy shape, and key session-event behavior. This is a test-safety pilot only; production mode behavior should not change.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/feature-module-direct-tests/integration`
- Functional package branches: `agent/feature-module-direct-tests/<package-id>`
- Implementation isolation: one worktree per functional package.
- Worktree root pattern: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/feature-module-direct-tests-<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- All shell commands must run through `rtk`, per `<HOME>/.codex/RTK.md`.
- Gradle in worktrees must use `rtk ./scripts/run_isolated_gradle.sh <gradle-args>`.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/feature-module-direct-tests-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- change production mode behavior to make tests pass
- revive `codex/agent_plans/`

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| `01-photo-night-still` | none | status | ready at start | 1 |
| `02-document-video-specialized` | none | status | ready at start | 1 |
| `03-portrait-pro-humanistic` | none | status | ready at start | 1 |
| `99-finalize` | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-photo-night-still`, `02-document-video-specialized`, `03-portrait-pro-humanistic`
- Code dependency policy: status dependency only; packages should not depend on each other's branches.
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
- A package needs production behavior changes to make tests pass.
- Gradle/build output contamination appears; rerun the smallest failure through `rtk ./scripts/run_isolated_gradle.sh` before declaring a product regression.
