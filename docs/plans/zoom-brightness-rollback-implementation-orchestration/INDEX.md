# Zoom And Brightness Rollback Implementation - Orchestration Index

## Goal

基于当前主线实际落地状态，修复真机变焦与快捷亮度快速拖动回弹：让 `FocalLengthSliderView` 显示节点数字并避免 active drag 被 render echo 覆盖；让 preview pinch zoom 的基准同步不在 `ScaleEnd` 后回到错误比例；让快捷亮度只发一次 device command，并在用户拖动时不被 session/device 回传值写回旧 progress。最终用 focused tests、assemble、Stage 7 gate 和真机 smoke 协议收口。

## Current Implementation Findings

- 主线当前已存在 `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt` 和 `FocalLengthSliderViewTest.kt`；旧研究包中“slider 不存在”的判断已过时。
- `FocalLengthSliderView` 当前已有 one-decimal floating label、tap preset、snap threshold、continuous release，但 dot 节点没有常驻数字标签。
- `FocalLengthSliderView.setCurrentRatio()` 在 `isDragging == true` 时仍会修改内部 `currentRatio`，只是不会 `invalidate()`；如果 renderer 在拖动中写入旧 `model.currentRatio`，release 使用的 `currentRatio` 仍可能被旧 echo 覆盖。
- `CockpitSurfaceRenderer.renderFocalLengthSlider()` 每次 render 都调用 `slider.setCurrentRatio(model.currentRatio)`；需要在 active drag 时 suppress 外部 ratio 回写，保留用户手指下的 optimistic UI。
- `GesturePolicy` 当前有 `ScaleEnd` 和 `syncZoomRatio(...)`，但 `MainActivityActionBinder` 在非 pinch event 上先 sync 当前 zoom，再 `GesturePolicy.map(ScaleEnd)` 内部又 reset 到 `1.0f`；下一次 pinch 可能从错误 local basis 开始。
- `CameraSessionCoordinator.latestPreviewBrightnessCommand(...)` 仍然既启动 `previewBrightnessJob` dispatch，又返回同一个 `DeviceCommand` 给外层 dispatch，导致亮度请求重复下发。
- `CockpitSurfaceRenderer.renderQuickBubble()` 仍无条件写 `brightnessSlider.progress`；`MainActivityActionBinder` 的 brightness `onStartTrackingTouch` / `onStopTrackingTouch` 仍为空。
- Focused verification baseline just run on 2026-05-27:
  `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.gesture.GesturePolicyTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest` passed.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/zoom-brightness-rollback/integration`
- Functional package branches: `agent/zoom-brightness-rollback/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-brightness-rollback-implementation-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
| 01-zoom-slider-render-latch | none | status | completed | 1 |
| 02-brightness-dispatch-and-latch | none | status | completed | 1 |
| 03-pinch-zoom-basis-repair | 01-zoom-slider-render-latch | code | upstream merged to integration branch or explicit branch base | 2 |
| 04-integration-verification-and-smoke | 01-zoom-slider-render-latch, 02-brightness-dispatch-and-latch, 03-pinch-zoom-basis-repair | status+code | upstream package branches merged or evidence complete | 3 |
| 99-finalize | 01-zoom-slider-render-latch, 02-brightness-dispatch-and-latch, 03-pinch-zoom-basis-repair, 04-integration-verification-and-smoke | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-zoom-slider-render-latch`, `02-brightness-dispatch-and-latch`, `03-pinch-zoom-basis-repair`, `04-integration-verification-and-smoke`
- Code dependency policy: merge-to-integration first for 03 and 04; 03 must see package 01 behavior before final verification because both affect zoom gestures/rendering.
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
- A package discovers that a supposedly runtime-only fix requires changing architecture ownership across UI / Session Kernel / Device Adapter.

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-zoom-slider-render-latch.md](packages/01-zoom-slider-render-latch.md) | implementation agent | none | safe with 02 | Add zoom node labels and active-drag render suppression in `FocalLengthSliderView` / cockpit wiring |
| [02-brightness-dispatch-and-latch.md](packages/02-brightness-dispatch-and-latch.md) | implementation agent | none | safe with 01 | Remove duplicate brightness dispatch and add brightness active-drag render suppression |
| [03-pinch-zoom-basis-repair.md](packages/03-pinch-zoom-basis-repair.md) | implementation agent | 01 | after 01 | Repair pinch zoom local basis around `ScaleEnd` / new pinch boundaries |
| [04-integration-verification-and-smoke.md](packages/04-integration-verification-and-smoke.md) | verification agent | 01, 02, 03 | after merges/evidence | Run focused gates, assemble, Stage 7 gate, and write real-device smoke checklist |
| [99-finalize.md](packages/99-finalize.md) | orchestrator | all functional packages | — | Final cross-package verification, integration, mainline merge, and cleanup |

