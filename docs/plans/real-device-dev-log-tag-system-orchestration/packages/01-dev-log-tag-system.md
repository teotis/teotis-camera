# Package: 01-dev-log-tag-system — Dev Log 标签系统 + ColorLab 滑动 + 链路耗时

## Goal

完成三个真机问题的代码实现与测试修复：

1. **ColorLab 页面禁用不必要滑动**：当 route 为 `CockpitPanelRoute.ColorLab` 时禁用 filterPanel 的 nested scrolling 和 overscroll。
2. **链路 Tab 具备耗时分析**：将 `PerformanceLinkRecorder` 的链路事件从 `AppContainer` 贯通到 `devLogRenderModel`，LINK Tab 内容格式化包含人类可读的 flow 摘要和机器可读的时序行；移除 summary 中基于 `.timing` 后缀的"最后耗时"展示。
3. **DevLogTag 标签系统**：新增 `DevLogTag` 枚举，`SessionTraceEvent` 增加 `tags` 字段；用标签过滤替代硬编码事件名集合；未显式标注事件通过名称规则推断标签。

## Allowed Paths

```
core/session/src/main/kotlin/com/opencamera/core/session/SessionTrace.kt
core/session/src/main/kotlin/com/opencamera/core/session/PerformanceLinkEvent.kt
app/src/main/java/com/opencamera/app/AppContainer.kt
app/src/main/java/com/opencamera/app/MainActivity.kt
app/src/main/java/com/opencamera/app/MainActivityRenderer.kt
app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt
app/src/test/java/com/opencamera/app/DevLogRenderModelTest.kt
```

## Forbidden Paths

Everything not listed above, especially:
- `docs/plans/real-device-dev-log-tag-system-orchestration/INDEX.md`
- Any package status file other than `status/01-dev-log-tag-system.md`
- `state.tsv` (mutate only via `mark-state`)

## Acceptance Criteria

### 1. ColorLab 滑动禁用

- [ ] `MainActivityRenderer.renderPanelVisibility()` 在 route 为 `ColorLab` 时设置 `filterLab.panel.isNestedScrollingEnabled = false` 和 `overScrollMode = OVER_SCROLL_NEVER`
- [ ] StyleLab 和其他 panel 保持正常滚动行为

### 2. 链路耗时分析

- [ ] `AppContainer` 创建并暴露 `PerformanceLinkRecorder`（通过 `createPerformanceLinkRecorder()` 工厂函数）
- [ ] `AppContainer` 将 `linkRecorder` 传给 `DefaultCameraSession`
- [ ] `MainActivity.render()` 和 `refreshDevLogModel()` 调用 `devLogRenderModel()` 时传入 `linkEvents = container.linkRecorder.snapshot()`
- [ ] LINK Tab 内容格式包含人工可读的 flow 摘要（事件数、完成/降级/失败统计、总耗时）和机器可读的 `toLinkLogLine()` 输出
- [ ] Summary 中移除 `lastTiming` ("最后耗时") 展示
- [ ] Core Summary (export) 包含 link flow 摘要

### 3. DevLogTag 标签系统

- [ ] `DevLogTag` 枚举定义在 `SessionTrace.kt`，包含 LIFECYCLE/MODE/CAPTURE/RECORDING/PREVIEW/PERFORMANCE/ERROR/RECOVERY/LENS/PERMISSION/SETTINGS/TIMING/INTENT/RESOURCE/ZOOM
- [ ] `SessionTraceEvent` 增加 `tags: Set<DevLogTag> = emptySet()` 字段
- [ ] `SessionTrace.record()` 和 `InMemorySessionTrace.record()` 支持 `tags` 参数（默认 `emptySet()`）
- [ ] `devLogRenderModel()` 使用标签过滤替代硬编码事件名集合
- [ ] `hasTag()` 扩展函数：优先使用显式 tags，无标签事件通过 `inferredTags()` 名称规则推断（保持向后兼容）
- [ ] KEY Tab = LIFECYCLE | MODE | CAPTURE | RECORDING | PERFORMANCE | TIMING
- [ ] CORE Tab = PREVIEW | RECOVERY | SETTINGS | PERMISSION | LENS | INTENT | ZOOM | RESOURCE
- [ ] ERROR Tab = ERROR
- [ ] 移除旧 `KEY_EVENT_NAMES`/`CORE_EVENT_NAMES`/`ERROR_EVENT_NAMES`/`isErrorEvent()`

### 4. 编译与测试

- [ ] `./gradlew :app:compileDebugKotlin` BUILD SUCCESSFUL
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.opencamera.app.DevLogRenderModelTest"` 全部 21 个测试通过
- [ ] `./gradlew :core:session:test` 全部通过

## Verification Commands

```bash
# 编译检查
./gradlew :app:compileDebugKotlin

# DevLogRenderModel 测试（21 tests, 0 failures）
./gradlew :app:testDebugUnitTest --tests "com.opencamera.app.DevLogRenderModelTest"

# Core session 测试
./gradlew :core:session:test

# 全量 app 单元测试
./gradlew :app:testDebugUnitTest
```

## Expected Evidence

- [ ] `./gradlew :app:compileDebugKotlin` 输出 `BUILD SUCCESSFUL`
- [ ] `./gradlew :app:testDebugUnitTest --tests "com.opencamera.app.DevLogRenderModelTest"` 输出 `21 tests completed, 0 failed`
- [ ] `./gradlew :core:session:test` 输出 `BUILD SUCCESSFUL`
- [ ] 本轮改动文件的 git diff 摘要

## Branch / Worktree

- Branch: `agent/dev-log-tag-system/01-dev-log-tag-system`
- Worktree: 由 `orchestrate.sh` 创建

## Implementation Notes

### 当前 main 分支已完成的基础变更（直接继续修复）

以下变更已在 main 分支上部分实现但未提交。本 package 应在此基础之上继续：

1. `SessionTrace.kt`: `DevLogTag` 枚举和 `SessionTraceEvent.tags` 字段已添加
2. `PerformanceLinkEvent.kt`: `createPerformanceLinkRecorder()` 工厂函数已添加
3. `AppContainer.kt`: `linkRecorder` 已创建并传给 `DefaultCameraSession`
4. `MainActivity.kt`: `devLogRenderModel()` 已传入 `linkEvents`
5. `MainActivityRenderer.kt`: ColorLab 滑动逻辑已添加
6. `SessionUiRenderModel.kt`: 标签系统过滤、link 格式化、移除"最后耗时"已实现

### 待完成

- `DevLogRenderModelTest.kt` 中 `key tab shows only key events` 测试失败：需要验证测试逻辑与标签分组规则的一致性
- 如需，微调 `inferredTags()` 规则以确保旧测试通过

### 测试修复策略

若 `key tab shows only key events` 仍失败，检查：
1. 测试期望 `preview.first.frame` 在 KEY tab 中出现 → 需要 `PERFORMANCE` 标签（已覆盖）
2. 测试期望 `intent.received` 不在 KEY tab 中 → 只有 `INTENT` 标签（CORE），不在 KEY ✓
3. 可能原因：`formatEvents()` 增加了标签显示格式，导致 `contains` 检查失败？→ 检查测试的 assert 逻辑
