# Full Clear Mode V1 - Orchestration Index

## Goal

Add a new `全清` / Full Clear mode and land a V1-level capability path for the hiking/travel use case: a nearby model, figurine, marker, or person-scale foreground object should be captured together with a distant landscape with the best achievable clarity.

This plan deliberately separates product truth from marketing promise:

- V1 target: a visible product mode, dual-focus bracket capture contract, CameraX execution path, V1 focus-stack fusion or honest best-frame fallback, diagnostics, and product docs.
- Non-goal for V1: vendor-grade HAL fusion, true depth, perfect optical-flow alignment, or claiming both foreground and background are always sharp.
- Product docs live beside this index:
  - [`product-definition.md`](product-definition.md)
  - [`v1-implementation-design.md`](v1-implementation-design.md)

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/full-clear-mode-v1/integration`
- Functional package branches: `agent/full-clear-mode-v1/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/full-clear-mode-v1/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; inside assigned worktrees, Gradle must use `rtk ./scripts/run_isolated_gradle.sh ...`.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/full-clear-mode-v1-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- resolve unrelated main-checkout conflicts or unrelated active orchestration state
- claim real-device Full Clear acceptance from unit tests, emulator checks, desktop inspection, or synthetic bitmap tests alone
- claim true depth, vendor HAL focus stacking, or guaranteed foreground/background sharpness without device evidence

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-full-clear-product-definition | none | status | initial ready package | 1 |
| 02-focus-bracket-capture-contract | none | status | initial ready package | 1 |
| 03-camerax-focus-bracket-execution | 02-focus-bracket-capture-contract | code | package 02 completed and branch available | 2 |
| 04-focus-stack-v1-honest-rendering | 01-full-clear-product-definition, 02-focus-bracket-capture-contract, 03-camerax-focus-bracket-execution | code | packages 01-03 completed and branch available | 3 |
| 05-full-clear-qa-and-product-docs | 04-focus-stack-v1-honest-rendering | status+code | package 04 completed | 4 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-full-clear-product-definition -> 02-focus-bracket-capture-contract -> 03-camerax-focus-bracket-execution -> 04-focus-stack-v1-honest-rendering -> 05-full-clear-qa-and-product-docs`
- Code dependency policy: package 03 consumes package 02 contracts; package 04 consumes mode metadata, focus-bracket contract, and captured temporary artifacts; package 05 consumes all implementation evidence.
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
- A package moves camera runtime ownership into UI, creates a hidden second session kernel, or directly drives CameraX from mode plugins/UI.
- Mode plugins call CameraX/Camera2/HAL directly.
- V1 output or docs claim guaranteed full-depth clarity when the pipeline only produced bracket frames, a best-frame fallback, or synthetic-test evidence.
- Real-device visual/device evidence is missing but final report claims product acceptance.

## Current Evidence Snapshot

- `ModeId` currently has seven modes and no Full Clear entry.
- Existing Mode Plugin architecture can host a new still mode without moving camera ownership out of the Session Kernel.
- Existing capture strategy model supports `SingleFrame`, `MultiFrame`, and `LivePhoto`, but not per-frame focus bracket metadata.
- Existing `ShotGraphBuilder` has `MULTI_FRAME_MERGE`, but current multi-frame post-processing is a placeholder diagnostic path.
- Existing CameraX adapter can apply manual `LENS_FOCUS_DISTANCE` through Camera2 interop, but the current multi-frame loop does not vary focus per frame.
- Scene Mask contracts exist, but default preview scene mask source can be unsupported. V1 must work without depending on true segmentation.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| [01-full-clear-product-definition.md](packages/01-full-clear-product-definition.md) | Add mode identity, product declaration, mode plugin scaffold, and user-visible labels | `core/mode`, `feature/mode-fullclear`, app mode rendering/i18n, Gradle settings |
| [02-focus-bracket-capture-contract.md](packages/02-focus-bracket-capture-contract.md) | Add reusable V1 focus bracket contracts across media/device graph | `core/media`, `core/device`, focused tests |
| [03-camerax-focus-bracket-execution.md](packages/03-camerax-focus-bracket-execution.md) | Execute per-frame focus bracket capture in CameraX adapter with diagnostics | `app/src/main/java/com/opencamera/app/camera`, adapter tests |
| [04-focus-stack-v1-honest-rendering.md](packages/04-focus-stack-v1-honest-rendering.md) | Add V1 fusion/best-frame postprocess and honest degradation notes | media postprocessor contracts, app postprocessors, synthetic image tests |
| [05-full-clear-qa-and-product-docs.md](packages/05-full-clear-qa-and-product-docs.md) | Produce APK, install commands, real-device checklist, and final product docs | plan docs, `codex/documentation.md`, verification evidence |
| [99-finalize.md](packages/99-finalize.md) | Merge, verify, report, and clean up recorded resources after success | integration branch and coordinator files |

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-full-clear-product-definition | autonomous | Claude Code | n/a | mode/plugin/render/i18n tests and build | none | normal graph |
| 02-focus-bracket-capture-contract | autonomous | Claude Code | n/a | core media/device contract tests | none | normal graph |
| 03-camerax-focus-bracket-execution | agent-verifiable substitute | Claude Code | local tools cannot prove physical lens motor movement on vivo X300 | adapter unit tests, diagnostics, build, APK path | none for implementation | normal graph |
| 04-focus-stack-v1-honest-rendering | autonomous | Claude Code | n/a for synthetic algorithm behavior; real scenes remain external | synthetic bitmap tests, failure/degradation tests, build | real-world visual proof later | normal graph |
| 05-full-clear-qa-and-product-docs | agent-verifiable substitute | Claude Code | cannot perform physical hiking/camera QA locally | APK path, install command, smoke checklist, log/export instructions | none for merge readiness | normal graph |
| vivo-x300-full-clear-qa | external-assist | user/Codex device owner | requires physical device, real foreground/background scenes, visual judgment, and optional logs/screenshots | APK, adb install command, local tests, checklist | foreground/background comparison photos, device model/build, pipeline notes/log export, pass/fail notes | final product confidence only unless user later makes it release-blocking |

## Real-Device Evidence Contract

Product acceptance remains incomplete until a device owner records:

- APK path and exact install command used.
- Device model, Android version, app build timestamp or commit.
- At least three scenes: near object + distant landscape, near object + city/building, and a failure case with motion/low light.
- Result JPEGs or screenshots, plus pipeline notes showing `full-clear`, focus bracket frame count, fusion/best-frame/degraded status, and any fallback reason.
- Subjective pass/fail notes: near subject clarity, background clarity, ghosting/edge artifacts, capture latency, preview/user guidance, and whether the output is better than ordinary Photo/Scenery.

