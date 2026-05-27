# Public Release Safety Audit - Orchestration Index

## Goal

Stop further public leakage, harden the public-release rules, and run a comprehensive audit/remediation pass for `public/teotis-camera` covering Git identity history, public-facing content, source/test fixtures, screenshots/assets, export behavior, license/attribution, and remote-visible cleanup planning.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/public-release-safety-audit/integration`
- Functional package branches: `agent/public-release-safety-audit/<package-id>`
- Implementation isolation: one main-repo worktree per functional package, except that `public/teotis-camera` is an ignored nested public Git repository and must be accessed by absolute path.
- Public repo policy: packages may inspect `public/teotis-camera` by absolute path; packages that need to mutate public repo content must create a public-repo local branch or public-repo worktree and record it in their status file.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned main-repo worktree and branch.
- Create a local public-repo branch/worktree only when their package doc explicitly allows it.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes when the package edits tracked files.
- Write only their assigned coordinator status file and files under their assigned `output/<package-id>/` directory.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/public-release-safety-audit-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:

- Inspect package docs, status files, state, branches, commits, diffs, and package output reports.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local main-repo package branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:

- force-push
- hard reset
- rewrite `public/teotis-camera` published history
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths
- push to `git@github.com:teotis/teotis-camera.git`

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-public-exposure-inventory | none | status | completed | 1 |
| 02-public-rules-export-gate | none | status | completed | 1 |
| 03-brand-reference-content-scrub | 01-public-exposure-inventory | status | inventory completed | 2 |
| 04-public-history-remediation-plan | 01-public-exposure-inventory, 02-public-rules-export-gate | status | inventory and rules completed | 2 |
| 05-export-diff-release-verification | 02-public-rules-export-gate, 03-brand-reference-content-scrub, 04-public-history-remediation-plan | status | rules, content, and history plan completed | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-public-exposure-inventory`, `02-public-rules-export-gate`, `03-brand-reference-content-scrub`, `04-public-history-remediation-plan`, `05-export-diff-release-verification`
- Code dependency policy: status dependency for reports; package branches merge to integration only after the status ledger says `completed`.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Public repo merge/push: not automatic. `99-finalize` may report exact public-repo commands, but must not push or rewrite history.
- Cleanup: delete only recorded local main-repo package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Package changes `public/teotis-camera` without recording the public branch/worktree and changed files.
- Any proposed history rewrite lacks explicit user approval.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- Public release safety scan still reports high-confidence identity leakage, private paths, company email, secrets, or unreviewed competitor-reference traces.
