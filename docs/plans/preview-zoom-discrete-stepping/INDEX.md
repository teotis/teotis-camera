# Preview Zoom Discrete Stepping - Orchestration Index

## Goal

修复真机实测发现的三个变焦预览问题：

1. **预览窗离散变焦**：预览窗不应连续变焦，而是到达对应焦段（物理镜头切换点）时直接跳跃变焦。在 参考设备 等三摄手机上，应在超广角/广角/长焦的本机焦距处保持预览窗放大倍率不变。
2. **画幅框面积约束**：保持画幅框占预览窗约 1/3 面积以上，让用户可以确认整体画面构图；同时避免高性能连续 SAT（平滑自动过渡）的实现难度。
3. **16:9 画幅框溢出修复**：16:9 时画幅框大于预览窗的 bug——预览窗使用的是更低倍率设定下的传感器全部面积，不应小于 16:9 拍照流。

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping`
- Mainline branch: `main`
- Integration branch: `agent/preview-zoom-discrete-stepping/integration`
- Functional package branches: `agent/preview-zoom-discrete-stepping/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/preview-zoom-discrete-stepping/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must run shell commands through `rtk`; inside assigned worktrees, Gradle must use `rtk ./scripts/run_isolated_gradle.sh ...`.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/preview-zoom-discrete-stepping/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
| 01-analyze-preview-zoom-strategy | none | status | initial ready package | 1 |
| 02-implement-discrete-preview-zoom | 01-analyze-preview-zoom-strategy | status+code | package 01 completed; package 01 branch merged to integration or base branch includes 01's data model definition | 2 |
| 03-fix-overlay-frame-geometry | 01-analyze-preview-zoom-strategy, 02-implement-discrete-preview-zoom | status+code | packages 01 and 02 completed; package 02's `previewZoomRatio` field available in data model | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

Package 03 depends on 02's code changes (needs the `previewZoomRatio` field added to `PreviewConfig`). 03's worktree should either rebase on 02's branch or base on the integration branch after 02 is merged.

## Merge Strategy

- Functional merge order: `01-analyze-preview-zoom-strategy -> 02-implement-discrete-preview-zoom -> 03-fix-overlay-frame-geometry`
- Code dependency policy: 02 builds on 01's analysis artifacts (reads scratch notes or the analysis doc). 03 builds on 02's data model changes (needs `previewZoomRatio` in `PreviewConfig`). Merged into integration branch in order.
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

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-analyze-preview-zoom-strategy | autonomous | Claude Code | n/a | analysis doc, zoom mapping table | none | normal graph |
| 02-implement-discrete-preview-zoom | autonomous | Claude Code | n/a | unit tests, compile check | none | normal graph |
| 03-fix-overlay-frame-geometry | autonomous | Claude Code | n/a | geometry unit tests, compile check | none | normal graph |
| real-device-zoom-preview-qa | external-assist | user/Codex/参考设备 | requires physical device to verify frame size and zoom stepping feel | APK path, install command, focused test checklist | screenshot/video of zoom stepping on device | release only |

## Key Design Decisions (Pre-Analysis)

以下分析基于现有代码结构，供 package 01 验证和细化：

### 当前数据流

```
User Zoom Gesture → SessionIntent.ApplyZoomRatio → DefaultCameraSession.handleApplyZoomRatio()
  → evaluateLensNode() [hysteresis: LENS_NODE_HYSTERESIS_DELTA=0.1]
  → resolveActiveDeviceGraph() → state.activeDeviceGraph.preview.zoomRatio
  → SessionEffect.ApplyZoomRatio → CameraXCaptureAdapter.updateZoomRatio()
  → camera.cameraControl.setZoomRatio(normalizedZoomRatio)

Overlay rendering:
  SessionPreviewRenderModel → PreviewFrameRenderModel(zoomRatio = state.activeDeviceGraph.preview.zoomRatio)
  → PreviewOverlayView.activeContentGeometry() → scale = 1f / zoom → frame scale
```

### 核心问题

`zoomRatio` 在 capture 和 preview 之间是同一个值，导致 preview 连续变焦时 frame 平滑缩放。实际上应当引入 `previewZoomRatio` 概念——它是用户看到的预览窗基准倍率，总是 ≤ `captureZoomRatio`，且在物理镜头切换点离散跳跃。

### 参考设备 三摄影像映射（待 01 实测验证）

| 镜头 | 物理焦距当量 | 建议预览基准倍率 | 覆盖焦段 |
|---|---|---|---|
| 超广角 (UW) | ~0.6x | 0.6x | 0.6x ~ 1.0x |
| 广角 (W) | 1.0x | 1.0x | 1.0x ~ 2.0x |
| 长焦 (T) | ~2.0x~3.0x | 2.0x | 2.0x ~ 5.0x |
| 超长焦 (P) | ~5.0x | 5.0x | 5.0x ~ max |

### 画幅框面积分析

- 当 captureZoom = previewZoomRatio * 1.73 时，画幅框面积为预览窗的 1/3
- 例如：previewZoom = 1.0x, captureZoom = 1.73x → frame area = (1/1.73)^2 ≈ 33%
- previewZoom = 1.0x, captureZoom = 2.0x → frame area = (1/2)^2 = 25% (< 1/3 但仍在可接受范围)
- 当 captureZoom >= previewZoomRatio * 2.0 时 (frame area < 25%)，应考虑提升 previewZoom 到下一级
