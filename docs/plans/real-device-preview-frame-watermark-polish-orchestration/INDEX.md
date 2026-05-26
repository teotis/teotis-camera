# Real Device Preview Frame And Blur Watermark Polish - Orchestration Index

## Goal

Resolve two real-device visual regressions as independent, verifiable closed loops: frame-ratio selection must keep the live preview as the current lens full-field view while only the UI frame and saved still crop change, and the `blur-four-border` watermark must generate a natural content-derived border instead of a pale fixed-looking frame. This package is intentionally scoped below a stage transition: it refines Stage 6B/7 product behavior without reopening frozen feature stages or adding a new camera runtime owner.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-preview-frame-watermark-polish-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/real-device-preview-frame-watermark-polish/integration`
- Functional package branches: `agent/real-device-preview-frame-watermark-polish/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
| 01-preview-frame-contract | none | status | completed | 1 |
| 02-natural-blur-border-rendering | none | status | completed | 1 |
| 99-finalize | 01-preview-frame-contract, 02-natural-blur-border-rendering | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-preview-frame-contract`, `02-natural-blur-border-rendering`
- Code dependency policy: status dependency (packages are independent, merge order is for conflict resolution)
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
- Creating a second hidden session/device owner in UI, coordinator, adapter, or postprocessor code.
- Claiming saved output matches the visible frame without deterministic crop tests or real-device evidence.
- Tuning `blur-four-border` by adding a fixed white/cream/dark overlay that hides the content-derived background.
- Adding network dependencies, external ML/image libraries, secrets, or API calls.

## File Ownership Map

| Path / Glob | Owner Package | Other Packages Must Not Edit |
|---|---|---|
| `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt` | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt` preview geometry sections | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` preview/use-case and zoom/frame sections | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt` | 01-preview-frame-contract | 02 |
| `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` | 01-preview-frame-contract | 02 |
| `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt` | 01-preview-frame-contract | 02 |
| `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt` | 01-preview-frame-contract | 02 |
| `core/device/**` narrowly scoped preview/capture capability contract files | 01-preview-frame-contract | 02 |
| `core/media/**` narrowly scoped composition crop contract files | 01-preview-frame-contract | 02 |
| `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` blur-frame background/rendering helpers | 02-natural-blur-border-rendering | 01 |
| `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt` | 02-natural-blur-border-rendering | 01 |
| `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt` only if resolver notes need strengthening | 02-natural-blur-border-rendering | 01 |
| `core/settings/**` | neither package by default | 01 and 02 must ask unless a failing test proves settings are the cause |
| `docs/plans/**` | package agents may edit only their own status file | all package agents must not edit indexes or other packages |

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-preview-frame-contract.md](packages/01-preview-frame-contract.md) | implementation agent | none | safe with 02 | Keep preview full-lens while frame ratio affects only UI crop frame and saved still crop |
| [02-natural-blur-border-rendering.md](packages/02-natural-blur-border-rendering.md) | implementation agent | none | safe with 01 | Replace fixed-looking blur-frame background with content-aware edge extension and tested visual constraints |
| [99-finalize.md](packages/99-finalize.md) | orchestration finalize | after 01 and 02 | - | Final cross-package integration, merge, and verification |

## Claude Background Permission Notes

- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- If the user has set `permissions.defaultMode` to `bypassPermissions` in `~/.claude/settings.json`, background sessions inherit that mode without the repository hard-coding it.
- Use `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If the user wants `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` once interactively first. If that fails, rerun with `CLAUDE_PERMISSION_MODE=default`.
- Do not use `--dangerously-skip-permissions`.
