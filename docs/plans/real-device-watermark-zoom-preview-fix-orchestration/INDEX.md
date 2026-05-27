# Real Device Watermark And Zoom Preview Fix - Orchestration Index

## Goal

Turn the latest real-device feedback into a narrow tail-driven multi-agent implementation plan for two visible preview problems:

- Watermark selection must immediately show a truthful approximate preview on the live surface. If exact saved-output rendering is too expensive for live preview, use template data, a border, icon, or simplified drawing so the user can predict the final watermark placement and style. For `professional-bottom-bar`, the preview must show a bottom parameter-bar style at the corresponding position, not a generic watermark label.
- Dragging the focal-length slider across physical thresholds must switch the live preview/runtime node. Crossing above `2x` should preview the `2x` node, crossing above `5x` should preview the `5x` node, and dragging back down should reverse without jitter. This is explicitly about the preview image changing, not only the slider label or saved crop metadata.

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/watermark-zoom-preview-fix/integration`
- Functional package branches: `agent/watermark-zoom-preview-fix/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/watermark-zoom-preview-fix/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; inside assigned worktrees, Gradle must use `rtk ./scripts/run_isolated_gradle.sh ...`.
- Current known repo hygiene risk: `git` may print `non-monotonic index .git/objects/pack/._*.idx` from AppleDouble files. Treat it as a preflight hygiene warning unless a command actually fails.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-watermark-zoom-preview-fix-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- resolve unrelated main-checkout conflicts
- claim real-device visual acceptance from unit tests or desktop checks alone

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-watermark-template-preview-expectation | none | status | initial ready package | 1 |
| 02-zoom-threshold-live-preview-switch | none | status | initial ready package | 1 |
| 03-integration-real-device-smoke-protocol | 01-watermark-template-preview-expectation, 02-zoom-threshold-live-preview-switch | status+code | both implementation packages completed or merged to integration | 2 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-watermark-template-preview-expectation -> 02-zoom-threshold-live-preview-switch -> 03-integration-real-device-smoke-protocol`
- Code dependency policy: packages 01 and 02 are independent and can run in parallel. Package 03 consumes both branches or the integration branch and must not introduce product behavior changes unless it records a blocker.
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
- A package moves CameraX/runtime ownership into UI, creates a hidden second session kernel, or hard-codes vivo-only camera ids without capability/degraded semantics.
- Watermark preview is represented only as prose/settings text and not visible on the preview surface.
- Zoom threshold logic changes slider labels but the still-photo live preview remains visually pinned to the old lens/runtime path at `2x` or `5x`.

## Current Evidence Snapshot

- Existing watermark preview primitives already exist: `WatermarkHintSpec`, `WatermarkPreviewShape`, `PreviewEffectAdapter.buildWatermarkHint(...)`, and `PreviewOverlayView.drawWatermarkHint(...)`.
- Current preview shape mapping treats `travel-polaroid`, `retro-frame`, and `professional-bottom-bar` as the same `EXPANDED_FRAME`; the latest feedback requires template-specific visual affordances, especially for the professional parameter bottom bar.
- The overlay currently has text, expanded frame, and four-border hints, but selectors/details may not make the chosen template visibly predictable on the live surface while the user is choosing.
- Existing zoom threshold primitives already exist: `SessionIntent.ApplyZoomRatio`, `SessionEffect.SwitchLensNode`, `DefaultCameraSession.evaluateLensNode(...)`, `DeviceCommand.SwitchLensNode`, `CameraXCaptureAdapter.switchLensNode(...)`, and tests around 2x/5x thresholds.
- Current CameraX still-preview path intentionally keeps CameraX zoom at `1x` and applies zoom via overlay/postprocess. That may directly conflict with the latest user expectation that the preview image itself switches to the `2x`/`5x` view during slider drag.
- Final acceptance still needs real-device smoke; local unit tests can prove dispatch, model, and adapter contracts, but not actual physical lens preview perception.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| [01-watermark-template-preview-expectation.md](packages/01-watermark-template-preview-expectation.md) | Make selected watermark templates visible and distinguishable on the live preview surface | preview effect model, overlay drawing, render model plumbing, watermark settings UI bridge |
| [02-zoom-threshold-live-preview-switch.md](packages/02-zoom-threshold-live-preview-switch.md) | Make slider threshold crossing visibly switch live preview/runtime node for still preview and video, with degraded fallback | session zoom contract, CameraX adapter lens-node/zoom behavior, slider/action plumbing |
| [03-integration-real-device-smoke-protocol.md](packages/03-integration-real-device-smoke-protocol.md) | Run integration checks and write the exact real-device smoke checklist for both symptoms | verification only plus coordinator status |
| [99-finalize.md](packages/99-finalize.md) | Merge, verify, report, and clean up recorded resources after success | integration branch and coordinator files |
