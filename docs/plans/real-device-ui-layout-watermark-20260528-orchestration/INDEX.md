# Real Device UI Layout Watermark 20260528 - Orchestration Index

## Goal

Repair the latest real-device UI findings from the 2026-05-28 screenshots:

- Issue 2: the visible frame-ratio guide must never be larger than the live preview content or feel detached from the preview window.
- Issue 3: the bottom capture deck and mode track must become more compact; the mode strip should not feel vertically empty.
- Issue 4: the Quick panel must include a Watermark shortcut that cycles through available watermark templates and reflects the current selection.

The plan keeps final visual acceptance device-dependent. Local agents must land deterministic layout/model/action tests first; the real-device package records the final screenshot/smoke protocol.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/real-device-ui-layout-watermark-20260528/integration`
- Functional package branches: `agent/real-device-ui-layout-watermark-20260528/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes or intermediate artifacts only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ui-layout-watermark-20260528-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
| `01-preview-frame-containment` | none | status | completed | 1 |
| `03-quick-watermark-cycle` | none | status | completed | 1 |
| `02-bottom-cockpit-density` | `01-preview-frame-containment` | code | upstream completed and mergeable; avoid fighting preview/mode constraints | 2 |
| `04-real-device-acceptance` | `01-preview-frame-containment`, `02-bottom-cockpit-density`, `03-quick-watermark-cycle` | status+code | all implementation packages completed; manual/device evidence required | 3 |
| `99-finalize` | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order:
  - `01-preview-frame-containment`
  - `02-bottom-cockpit-density`
  - `03-quick-watermark-cycle`
  - `04-real-device-acceptance`
- Code dependency policy: merge `01-preview-frame-containment` before `02-bottom-cockpit-density`; `03-quick-watermark-cycle` is independent unless it touches shared Quick panel layout.
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
- Final real-device acceptance cannot be made meaningful; record the blocker instead of overclaiming.
