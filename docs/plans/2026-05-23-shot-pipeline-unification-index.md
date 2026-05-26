# 2026-05-23 Shot Pipeline Unification Index

> **For agentic workers:** Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if implementing one of these plans. Use `rtk` for every shell command. These plans are written for text-only agents and do not require screenshot, video, or image analysis.

## Goal

Unify the current media capture planning model so `ShotGraph` becomes the canonical representation of capture topology and algorithm/output nodes, while preserving the existing `CaptureStrategy -> session active shot -> device execution -> ShotResult` runtime behavior during migration.

## Review Verdict

The external review is directionally correct, with one important correction:

- Correct: `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt` currently contains two related models.
- Correct: `ShotExecutor.plan()` builds a command-style `ShotPlan` from `CaptureStrategy`.
- Correct: `ShotGraph 2.0` can represent richer capture, algorithm, and output topology.
- Correct: `ShotPlan.toShotGraph()` proves that the graph is intended to be at least a summary/superset of the plan.
- Needs correction: do not delete `ShotPlan` in one pass. `ShotPlan` is still the runtime carrier used by session effects, device commands, `CameraXCaptureAdapter`, video active recording state, device translation, result synthesis, and tests.

Recommended path: make `ShotPlan` a compatibility envelope around a canonical graph first, then migrate translators and executors to read graph semantics.

## Evidence

- `CaptureStrategy` is the mode-level intent model at `MediaPipelineContracts.kt:221`.
- `ShotRequest`, `MediaSaveTask`, and `ShotPlan` are defined at `MediaPipelineContracts.kt:257`.
- `ShotExecutor.plan()` creates `ShotPlan` at `MediaPipelineContracts.kt:503`.
- `ShotExecutor.resultFor()` still owns metadata merge and thumbnail source creation at `MediaPipelineContracts.kt:549`.
- `ShotGraph`, `CaptureNode`, `AlgorithmNode`, and `OutputNode` are defined at `MediaPipelineContracts.kt:593`.
- `ShotPlan.toShotGraph()` converts v1 to v2 at `MediaPipelineContracts.kt:893`.
- `SessionEffect.ExecuteShot` still carries `ShotPlan` at `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt:268`.
- `DeviceCommand.ExecuteShot` still carries `ShotPlan` at `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt:470`.
- `DeviceShotRequestTranslator.translate(plan: ShotPlan)` is the device boundary at `core/device/src/main/kotlin/com/opencamera/core/device/DeviceShotRequestTranslator.kt:25`.
- `CameraXCaptureAdapter.executeShot(plan: ShotPlan)` is the real CameraX execution entry at `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt:1207`.
- `CameraXCaptureAdapter.activeVideoPlan` stores `ShotPlan` while recording is active at `CameraXCaptureAdapter.kt:1019`.

## Current Risk

Current tests show the two models are consistent enough for the existing contract, but the architecture has drift risk:

- A future agent can add a new algorithm node to `ShotGraph` without making runtime execution consume it.
- A future agent can change `ShotExecutor.plan()` or device translation without updating `toShotGraph()`.
- `ShotGraph` has no canonical execution owner today; it is mostly a contract/test/bridge surface.
- The video graph currently uses `CaptureNodeRole.PRIMARY_STILL` for the video capture node, while the output node is `PRIMARY_VIDEO`. That should be fixed during graph planning cleanup.

## Work Packages

1. [ShotGraph Planning Source Of Truth](./2026-05-23-shot-graph-planning-source-of-truth.md)
   - Highest priority and lowest blast radius.
   - Keeps public runtime behavior stable.
   - Makes every `ShotPlan` carry a canonical graph built during planning.
   - Deprecates `ShotPlan.toShotGraph()` as a compatibility read.

2. [ShotGraph Device And Execution Migration](./2026-05-23-shot-graph-device-execution-migration.md)
   - Starts only after package 1 lands.
   - Migrates device translation and CameraX execution decisions to graph semantics in small, tested steps.
   - Keeps `ShotPlan` envelope until session/device/app tests are green.

## Recommended Parallelization

- Agent A should own package 1.
- Agent B may prepare package 2 by reading code, but must not land API renames until Agent A finalizes graph field names.
- If using multiple agents, one integrator should merge package 1 before package 2 starts editing `DeviceShotRequestTranslator` or `CameraXCaptureAdapter`.

## Non-Goals

- Do not start a new product stage.
- Do not tune visual algorithms or image quality here.
- Do not move media algorithms into mode plugins.
- Do not make UI or `MainActivity` aware of `ShotGraph`.
- Do not remove `CaptureStrategy`; it remains the mode plugin request language.
- Do not remove `ShotPlan` until all session, device, app, and test call sites are graph-backed.

## Verification Already Run For This Review

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest --tests com.opencamera.core.media.AlgorithmProcessorTest --tests com.opencamera.core.media.AlgorithmJobSchedulerTest :core:mode:test --tests com.opencamera.core.mode.ModeCaptureStrategyGraphTest
```

Result: pass.

## Final Acceptance

- `CaptureStrategy` still describes what a mode wants.
- `ShotGraph` becomes the canonical topology produced during planning.
- `ShotPlan` becomes a compatibility envelope, not an independent topology source.
- Device translation and CameraX execution read graph nodes where graph semantics exist.
- Stage 7 verification remains green:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Re-Verification 2026-05-23 (第二轮)

核验人：Claude，基于外部 agent 提交 (c90f87d, b10a63a) 后的仓库状态。

### 已通过

| 检查项 | 结果 |
|--------|------|
| `verify_stage_7_observability.sh` 汇编 | PASS |
| ShotExecutor / AlgorithmProcessor / AlgorithmJobScheduler 测试 | PASS |
| ModeCaptureStrategyGraph 测试 | PASS |
| DefaultDeviceShotRequestTranslator 测试 | PASS |
| CameraXCaptureAdapter 全量单元测试 (29 个) | PASS |

### 已修复的问题

1. **[P1] CameraX LivePhoto 测试失败** — `resolvePhotoOutputHandle` 的参数从 `String?` 改为 `Uri?` 后，JVM 单元测试中 `Uri.parse()` 返回 null（因 `isReturnDefaultValues = true`）。修复：将 Uri→String 转换移至调用点 `PhotoOutputRequest.resolveOutputHandle()`，函数参数恢复为 `String?`，JVM 测试可直接传字符串。

2. **[P2] ShotGraph 可选算法节点引用错误节点 ID** — `ShotGraphBuilder` 的 filter/watermark/thumbnail 节点硬编码引用 `${shotId}:primary`，但 LIVE_PHOTO 的 primary capture node 是 `${shotId}:still`，VIDEO_RECORDING 是 `${shotId}:video`。修复：在各 when 分支设置 `primaryCaptureNodeId` 变量，算法节点统一使用该变量。

### 保留风险（非 ShotGraph 回归）

**[P1] MediaStore editable handle** — Android Q+ 的 `createPhotoOutputRequest` 创建 `outputHandle` 时仅设 `displayPath`（相对路径）。如果 CameraX 省略 `savedUri`，下游 Color Lab/水印/画幅后处理可能拿不到 `contentUri`。这是 ShotGraph 工作之前就存在的设计限制，建议后续单独处理（如 Q+ 预插入 MediaStore 获取 content URI）。

### 图语义迁移状态

`executeShot()` 已成功迁移到图谓词调度：
```kotlin
when {
    graph.primaryVideoNode() != null -> startVideoRecording(...)
    graph.temporaryFrameNode() != null -> captureStillImage(...)
    graph.primaryStillNode() != null -> captureStillImage(...)
    else -> error(...)
}
```

`DeviceShotRequestTranslator.translate(plan)` 已改为读取 `plan.graph` 做设备翻译决策。

### 结论

ShotGraph 方向核心代码已落地，所有门禁和数据面测试通过。两个已修复的问题均为测试兼容性和图节点一致性 bug，不影响架构正确性。
