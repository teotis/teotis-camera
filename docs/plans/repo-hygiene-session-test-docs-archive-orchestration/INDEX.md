# Repo Hygiene, Session Test Split, And Plans Archive - Orchestration Index

## Goal

Resolve the three confirmed maintenance problems without turning governance work into accidental product churn: reduce repository/worktree volume through an evidence-first cleanup gate, split the 5810-line `DefaultCameraSessionTest` into maintainable domain files while preserving behavior, and turn `docs/plans/` from an ever-growing flat planning pile into an indexed, reversible archive system.

This orchestration links back to the earlier repo-health and document-cleanup plans instead of replacing them:

- `docs/plans/2026-05-25-repo-health-triage-index.md`
- `docs/plans/2026-05-25-git-worktree-hygiene-and-status-repair.md`
- `docs/plans/stage7-session-test-hang-orchestration/INDEX.md`
- `docs/plans/2026-05-29-codex-document-asset-cleanup-p1.md`

## Current Evidence

- Current main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Current branch at planning time: `main`
- Base commit at planning time: `ae0358df4b46`
- Repository size snapshot: `.` = `81G`, `.worktrees` = `19G`, `.claude/worktrees` = `51G`, `docs/plans` = `1.1G`, `public/teotis-camera` = `1.4G`.
- `public/teotis-camera` is a protected public repository location governed by `scripts/PUBLIC_VERSION_RULES.md`; it is explicitly out of scope for deletion.
- `DefaultCameraSessionTest.kt` is `5810` lines and mixes recovery, capture lifecycle, mode behavior, device graph controls, presentation state, document batch, zoom/lens-node helpers, and capture-readiness tests.
- `docs/plans/` is the canonical planning home and must remain discoverable; this package creates archive policy and a small pilot rather than deleting historical records.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/repo-hygiene-session-test-docs-archive/integration`
- Functional package branches: `agent/repo-hygiene-session-test-docs-archive/<package-id>`
- Implementation isolation: one worktree per functional package under `/private/tmp/open_camera-orchestration/repo-hygiene-session-test-docs-archive/<package-id>`.
- Gradle in package worktrees must use `rtk ./scripts/run_isolated_gradle.sh <gradle-args>`.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Worktree cleanup packages must inspect the main checkout by absolute path; package worktrees under `/private/tmp/...` are not cleanup targets.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes or intermediate artifacts only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:

- Inspect package docs, status files, state, branches, commits, diffs, and package outputs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline only after verification passes and the worktree cleanup approval gate is satisfied or explicitly deferred by the user.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:

- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- delete or rewrite `public/teotis-camera`
- delete or move `docs/plans/**` outside the approved archive pilot list
- add secrets or credentials
- edit outside allowed paths
- run non-`rtk` shell commands in this repository

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| `01-worktree-inventory-and-safety-plan` | none | status | completed | 1 |
| `02-session-test-fixture-recovery-split` | none | code | completed and verified | 1 |
| `03-docs-plan-taxonomy-and-retention` | none | code | completed and verified | 1 |
| `manual-worktree-cleanup-approval` | `01-worktree-inventory-and-safety-plan` | external-assist | user approves exact cleanup list or records explicit deferral | manual |
| `04-session-test-capture-mode-split` | `02-session-test-fixture-recovery-split` | code | upstream merged to integration branch or explicit branch base | 2 |
| `05-docs-archive-pilot` | `03-docs-plan-taxonomy-and-retention` | code | upstream policy/index branch available | 2 |
| `06-session-test-device-presentation-split` | `04-session-test-capture-mode-split` | code | upstream merged to integration branch or explicit branch base | 3 |
| `07-worktree-cleanup-execution` | `manual-worktree-cleanup-approval` | status | approval/deferral package completed | 3 |
| `99-finalize` | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order:
  1. `01-worktree-inventory-and-safety-plan`
  2. `02-session-test-fixture-recovery-split`
  3. `03-docs-plan-taxonomy-and-retention`
  4. `04-session-test-capture-mode-split`
  5. `05-docs-archive-pilot`
  6. `06-session-test-device-presentation-split`
  7. `07-worktree-cleanup-execution`
- Code dependency policy:
  - Session test packages are sequential code dependencies because they touch the same large test file and fixtures.
  - Docs taxonomy precedes archive pilot.
  - Worktree cleanup execution is status-gated by manual approval.
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Worktree cleanup approval is missing, ambiguous, or broader than `01` inventory evidence.
- A package tries to delete `public/teotis-camera`, `docs/plans/`, or unapproved user/agent work.
- Test split changes production behavior or edits `DefaultCameraSession.kt` without proving a pre-existing test-only defect.
- Archive pilot breaks active links from `docs/plans/INDEX.md`, `codex/documentation.md`, `AGENTS.md`, or active package indexes.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| `01-worktree-inventory-and-safety-plan` | autonomous | Claude Code | n/a | inventory, `du`, `git worktree list`, status reports | none | normal graph |
| `manual-worktree-cleanup-approval` | external-assist | user/Codex | deciding which old agent work can be deleted is a user-owned destructive choice | exact cleanup candidate TSV and defer option | approved cleanup TSV or explicit deferral note | cleanup execution and final merge |
| `02-session-test-fixture-recovery-split` | autonomous | Claude Code | n/a | focused isolated Gradle tests | none | normal graph |
| `03-docs-plan-taxonomy-and-retention` | autonomous | Claude Code | n/a | retention docs, generated archive candidate report, link checks | none | normal graph |
| `04-session-test-capture-mode-split` | autonomous | Claude Code | n/a | focused isolated Gradle tests | none | normal graph |
| `05-docs-archive-pilot` | autonomous | Claude Code | n/a | small reversible archive pilot and link checks | none | normal graph |
| `06-session-test-device-presentation-split` | autonomous | Claude Code | n/a | focused isolated Gradle tests | none | normal graph |
| `07-worktree-cleanup-execution` | agent-verifiable substitute | Claude Code after approval | actual deletion is only allowed after the manual approval artifact exists | cleanup transcript, before/after `du`, `git worktree list`, status | approved cleanup TSV from manual gate | normal graph after approval |
| full repository size reduction | external-assist | user/Codex | disk cleanup success may depend on user approval and local files outside Git | before/after size report | user-approved deletion or deferral | final success wording |

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-worktree-inventory-and-safety-plan.md](packages/01-worktree-inventory-and-safety-plan.md) | audit agent | none | safe | Inventory worktrees and produce a user-approval cleanup list without deleting anything |
| [02-session-test-fixture-recovery-split.md](packages/02-session-test-fixture-recovery-split.md) | implementation agent | none | caution | Establish test baseline, extract shared fixtures, split recovery/host tests |
| [03-docs-plan-taxonomy-and-retention.md](packages/03-docs-plan-taxonomy-and-retention.md) | documentation agent | none | safe | Create retention taxonomy, archive rules, and candidate inventory |
| [manual-worktree-cleanup-approval.md](packages/manual-worktree-cleanup-approval.md) | user/Codex manual gate | after 01 | manual | Approve or defer exact cleanup candidates |
| [04-session-test-capture-mode-split.md](packages/04-session-test-capture-mode-split.md) | implementation agent | after 02 | unsafe in parallel with session split | Split capture lifecycle, mode behavior, and re-arm tests |
| [05-docs-archive-pilot.md](packages/05-docs-archive-pilot.md) | documentation agent | after 03 | caution | Move a small approved docs/plans archive pilot and update links |
| [06-session-test-device-presentation-split.md](packages/06-session-test-device-presentation-split.md) | implementation agent | after 04 | unsafe in parallel with session split | Split device controls, presentation, document batch, zoom, and readiness tests |
| [07-worktree-cleanup-execution.md](packages/07-worktree-cleanup-execution.md) | cleanup agent | after manual gate | caution | Execute only approved/deferred worktree cleanup path |
| [99-finalize.md](packages/99-finalize.md) | finalizer agent | all functional packages | no parallel | Verify, merge, run integration checks, report outcome |

## Verification Baseline

Run focused commands first from package worktrees through the isolated wrapper. `99-finalize` should run at minimum:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
rtk rg -n "docs/plans/(<moved-path-placeholder>)" docs/plans codex AGENTS.md CLAUDE.md
rtk git worktree list
rtk git status --short --branch
```

If `DefaultCameraSessionTest` has pre-existing failures on current main, the split packages must record a focused baseline before moving tests and prove no new failure was introduced. Do not hide baseline failures by weakening assertions.

## Manual Worktree Cleanup Gate

The manual gate expects one of two artifacts:

- Approved cleanup: `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/manual-worktree-cleanup-approval/approved-cleanup.tsv`
- Explicit deferral: `docs/plans/repo-hygiene-session-test-docs-archive-orchestration/output/manual-worktree-cleanup-approval/cleanup-deferred.md`

`07-worktree-cleanup-execution` must refuse to delete anything unless one of those artifacts exists and matches the `01` inventory output.

## Launch Options

- Manual: copy prompts from `launchers/agent-prompts.md`.
- Script: run `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/repo-hygiene-session-test-docs-archive-orchestration/launchers/orchestrate.sh start`.
- Downstream dispatch is triggered only by package tail calls to `advance`, or by manual `advance` fallback.
