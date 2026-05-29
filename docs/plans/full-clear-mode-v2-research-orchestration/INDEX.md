# Full Clear Mode V2 Research - Orchestration Index

## Goal

Research and design a V2 implementation path for `全清` / Full Clear mode in this project, using public Apple/vivo/OPPO/Android evidence and the existing OpenCamera architecture.

V2 is not a direct runtime implementation package. It is a design orchestration whose output should answer:

- What should Full Clear V2 promise beyond V1?
- Which vendor ideas are worth copying, translating, or rejecting?
- How should V2 fit this project without creating a second hidden session kernel?
- What technical contracts, algorithms, UI states, diagnostics, and real-device gates are required before implementation?

Static design deliverables live beside this index:

- [`competitive-research.md`](competitive-research.md)
- [`v2-product-definition.md`](v2-product-definition.md)
- [`v2-implementation-design.md`](v2-implementation-design.md)
- [`v2-roadmap.md`](v2-roadmap.md)

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/full-clear-mode-v2-research/integration`
- Functional package branches: `agent/full-clear-mode-v2-research/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/full-clear-mode-v2-research/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; this is a docs-first research/design package, so Gradle should be used only for optional source validation.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v2-research-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- edit runtime code or build files
- edit outside `docs/plans/full-clear-mode-v2-research-orchestration/**` and `docs/plans/INDEX.md`
- claim V2 implementation or real-device acceptance from research docs alone
- copy vendor marketing claims into product requirements without capability/support/degradation mapping

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-vendor-and-literature-research | none | status | initial ready package | 1 |
| 02-v2-product-capability-model | 01-vendor-and-literature-research | status | package 01 completed | 2 |
| 03-v2-android-architecture-design | 01-vendor-and-literature-research, 02-v2-product-capability-model | status | packages 01 and 02 completed | 3 |
| 04-v2-algorithm-and-media-pipeline-design | 01-vendor-and-literature-research, 02-v2-product-capability-model | status | packages 01 and 02 completed | 3 |
| 05-v2-roadmap-validation-and-handoffs | 03-v2-android-architecture-design, 04-v2-algorithm-and-media-pipeline-design | status | packages 03 and 04 completed | 4 |
| 99-finalize | all functional packages | status+docs | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-vendor-and-literature-research -> 02-v2-product-capability-model -> 03-v2-android-architecture-design -> 04-v2-algorithm-and-media-pipeline-design -> 05-v2-roadmap-validation-and-handoffs`
- Code dependency policy: docs-only status dependencies; no runtime code merge expected.
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
- A package proposes UI-owned camera execution, mode-plugin CameraX calls, or media-pipeline bypass.
- A package treats external real-device evidence, vendor private algorithms, or paid/proprietary APIs as autonomously available.
- V2 plan lacks explicit unsupported/degraded semantics.

## Research Findings Baseline

- Apple public docs show a product pattern of automatic close-up handling with user-visible Macro Control rather than exposing technical focus-stack controls.
- vivo and OPPO public product pages emphasize hardware range, telephoto/macro reach, and AI imaging rather than promising generic app-layer focus stacking.
- Android public APIs expose manual focus distance and CameraX camera2 interop, but app-layer implementation must manage 3A state, lens-switch latency, and per-frame request consistency.
- Multi-focus image fusion literature supports all-in-focus fusion as a plausible algorithm family, but real handheld smartphone scenes require alignment confidence, ghost rejection, and fallback.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| [01-vendor-and-literature-research.md](packages/01-vendor-and-literature-research.md) | Curate Apple/vivo/OPPO/Android/research evidence and translate it into Full Clear V2 lessons | research docs |
| [02-v2-product-capability-model.md](packages/02-v2-product-capability-model.md) | Define V2 product promise, support matrix, and UX states | product docs |
| [03-v2-android-architecture-design.md](packages/03-v2-android-architecture-design.md) | Design Android/CameraX/Camera2 architecture and contracts | architecture docs |
| [04-v2-algorithm-and-media-pipeline-design.md](packages/04-v2-algorithm-and-media-pipeline-design.md) | Design fusion, alignment, confidence, and media pipeline | algorithm/media docs |
| [05-v2-roadmap-validation-and-handoffs.md](packages/05-v2-roadmap-validation-and-handoffs.md) | Convert V2 into implementation waves and real-device validation gates | roadmap docs |
| [99-finalize.md](packages/99-finalize.md) | Merge, verify, report, and clean up recorded resources after success | integration branch and coordinator files |

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-vendor-and-literature-research | agent-verifiable substitute | Claude Code | live web access may vary for spawned agents | use supplied source list, cite current docs, record stale/unreachable sources | none | normal graph |
| 02-v2-product-capability-model | autonomous | Claude Code | n/a | docs review and consistency checks | none | normal graph |
| 03-v2-android-architecture-design | autonomous | Claude Code | n/a | local source inspection and design docs | none | normal graph |
| 04-v2-algorithm-and-media-pipeline-design | agent-verifiable substitute | Claude Code | cannot prove handheld real-scene fusion quality locally | synthetic tests and future implementation criteria | real-scene image pairs later | normal graph |
| 05-v2-roadmap-validation-and-handoffs | agent-verifiable substitute | Claude Code | cannot perform physical device QA | implementation wave docs, APK/log/checklist requirements | real-device evidence later | normal graph |
| vivo-x300-v2-qa | external-assist | user/Codex device owner | requires physical camera, controlled scenes, user visual judgment, and saved media/logs | no autonomous substitute beyond checklist and local build evidence | comparison photos, pipeline notes, latency logs, artifacts report | final product confidence only unless user later makes it release-blocking |

