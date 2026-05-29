# 01 Architecture Boundary Integrity - Analysis Report

**扫描范围**: `app/` (55 .kt files), `core/` (109 files across 7 modules), `feature/` (14 files across 8 plugins)
**分析维度**: import 语句跨层依赖、类实例化边界违规、方法调用链隐式耦合、Session Kernel 状态所有权
**发现总数**: 14 findings (1 critical, 4 warning, 9 info)

---

## CRITICAL

### C-1: core/media ↔ core/capability 循环依赖

| 项 | 值 |
|---|---|
| 违规类型 | 模块级循环依赖 |
| 涉及模块 | `core:media` ↔ `core:capability` |
| 违反规则 | 核心模块之间不应存在循环依赖，破坏可测试性和编译隔离 |

**证据**:
- `core/media/build.gradle.kts` 声明 `implementation(project(":core:capability"))`
- `core/capability/build.gradle.kts` 声明 `implementation(project(":core:media"))`
- 源码层面: `core/capability` 的 `CapabilityGraphResolver.kt` 确实导入 `core.media.MediaProcessorAvailability`
- 源码层面: `core/media` 源码中 **零** 导入 `core.capability` 包的任何类

**影响**: 编译时耦合循环，无法单独测试任一模块。media → capability 的依赖在源码层面完全未使用，应删除。

**建议修复**: 从 `core/media/build.gradle.kts` 移除 `implementation(project(":core:capability"))`。

---

## WARNING

### W-1: CameraXCaptureAdapter 导入 7 个 core 实现类

| 项 | 值 |
|---|---|
| 文件 | `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` |
| 违规类型 | 具体实现类耦合 |
| 违反规则 | adapter 应通过接口/契约编程，而非直接依赖实现类 |

**证据**:
- `:61` — `DefaultDeviceShotRequestTranslator` (具体类)
- `:76` — `MultiFrameCaptureExecutionPlanner` (具体类)
- `:78` — `MultiFrameTemporaryOutputTracker` (具体类)
- `:96` — `CompositeMediaPostProcessor` (具体类)
- `:108` — `MultiFrameMergePlaceholderPostProcessor` (具体类)
- `:109` — `PipelineMetadataPostProcessor` (具体类)
- `:111` — `ShotExecutor` (具体类)

**影响**: adapter 与 core.device 和 core.media 的具体实现强耦合，替换实现需要修改 adapter 代码。不过 adapter 作为硬件桥接层，直接使用实现类在实践中可接受（当前不存在对应接口）。

**严重程度下调理由**: 这些实现类大多属于"值类型/工具类"而非"可替换策略"，耦合的实际风险较低。但 `ShotExecutor` 和 `CompositeMediaPostProcessor` 作为核心媒体处理管道，值得考虑通过接口注入。

---

### W-2: core/media 声明了两个未使用的模块依赖

| 项 | 值 |
|---|---|
| 文件 | `core/media/build.gradle.kts` |
| 违规类型 | 构建配置残留 |

**证据**:
- `api(project(":core:device"))` — media 源码中零导入 `core.device` 包
- `implementation(project(":core:capability"))` — media 源码中零导入 `core.capability` 包

**影响**: 传递依赖膨胀（`api` scope 会暴露给所有 media 消费者），增加编译时间。

---

### W-3: core/mode 的 api 依赖扇出过宽

| 项 | 值 |
|---|---|
| 文件 | `core/mode/build.gradle.kts` |
| 违规类型 | 过度传播 |

**证据**: mode 以 `api` scope 依赖 5 个 core 模块:
- `api(project(":core:device"))`
- `api(project(":core:media"))`
- `api(project(":core:settings"))`
- `api(project(":core:effect"))`
- `api(project(":core:capability"))`

mode 的 7 个源文件只导入 20 个唯一符号。使用 `api` 意味着所有 mode 消费者（session、app）都传递性获得这 5 个模块的全部公开 API。

**影响**: session 模块已经是 mode 的消费者，它本身也直接依赖这 5 个模块，所以实际影响有限。但如果未来有新的消费者只依赖 mode，会不必要地获得所有传递 API。

---

### W-4: core/session 包含一个 macOS 资源叉文件

| 项 | 值 |
|---|---|
| 文件 | `core/session/src/main/kotlin/com/opencamera/core/session/._CaptureRecordingSessionProcessor.kt` |
| 违规类型 | 源码目录中的二进制/元数据文件 |

**影响**: macOS `._` 前缀文件是 extended attribute 的资源叉，不应纳入源码控制。可能在某些构建工具中造成解析异常。

---

## INFO

### I-1: AppContainer.kt 作为 DI 组装点，导入所有具体实现类（预期行为）

| 项 | 值 |
|---|---|
| 文件 | `app/src/main/java/com/opencamera/app/AppContainer.kt` |
| 分类 | 预期行为 |

AppContainer 是 DI/组装根，导入以下具体实现类是正确的:
- `DefaultCameraSession`, `InMemorySessionTrace` (session)
- `CompositeMediaPostProcessor`, `MultiFrameMergePlaceholderPostProcessor`, `PipelineMetadataPostProcessor`, `ShotExecutor` (media)
- `ModeRegistry` (mode)
- `CapabilityGraphResolver` (capability)
- `EffectCapabilityResolver`, `PreviewEffectAdapter` (effect)
- 8 个 feature mode plugins

**结论**: 这是唯一应该知道所有具体实现的文件。架构正确。

---

### I-2: SessionPreviewRenderModel 导入具体类 PreviewEffectAdapter

| 项 | 值 |
|---|---|
| 文件 | `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt:4` |
| 分类 | 轻微耦合 |

导入 `PreviewEffectAdapter` 具体类，而 `DeviceEffectCapabilityQuery.kt:4` 已使用接口 `EffectCapabilityQuery`。render model 可能只需接口。

**影响**: 低。render model 是纯读取层，不持有状态。

---

### I-3: CameraX/Camera2 导入分布 — 集中且可控

| 项 | 值 |
|---|---|
| 分类 | 架构合规确认 |

- Camera2 导入: 仅 `CameraXCaptureAdapter.kt` 一个文件 (7 个 import)
- CameraX 导入: 集中在 `CameraXCaptureAdapter.kt` (28 个 import)，其余为 `PreviewView` (UI 组件) 或 `ImageProxy` (帧处理)
- feature/ 模块: **零** CameraX/Camera2 导入

`PreviewView` 出现在 4 个文件 (CameraSessionCoordinator, MainActivityViews, CameraDeviceAdapter, PreviewSceneBrightnessMonitor)，`ImageProxy` 出现在 4 个文件。这些是合理的框架引用，不违反"不直接驱动 camera runtime"的规则。

---

### I-4: feature/ 模块完全遵守架构契约

| 项 | 值 |
|---|---|
| 分类 | 架构合规确认 |

所有 8 个 mode plugin 检查结果:
- CameraX/Camera2/HAL 导入: **零** ✅
- app/ 层导入: **零** ✅
- core.session 导入: **零** ✅
- 所有导入来自: core.mode, core.device, core.media, core.effect, core.settings (均为契约/值类型)

**结论**: "Mode plugins must not call CameraX/Camera2/HAL directly" 规则完全遵守。

---

### I-5: Session 状态所有权边界完全清洁

| 项 | 值 |
|---|---|
| 分类 | 架构合规确认 |

逐文件验证:
| 组件 | 角色 | 读取 Session State? | 修改 Session State? | 结论 |
|---|---|---|---|---|
| DefaultCameraSession | Session Kernel | 拥有 | 是 (唯一拥有者) | ✅ 正确 |
| CameraSessionCoordinator | 消息路由器 | 是 (对比用) | 否 (dispatch intent) | ✅ 正确 |
| SessionSettingsManager | 设置持久化 | 是 (guard 条件) | 否 (dispatch intent) | ✅ 正确 |
| SessionUiRenderModel | UI 渲染 | 是 (只读) | 否 (纯函数) | ✅ 正确 |
| CameraXCaptureAdapter | 硬件适配器 | 否 | 否 (emit DeviceEvents) | ✅ 正确 |

架构遵循 intent-driven 模式: session kernel 拥有所有状态，外部组件只读状态并通过 SessionIntent dispatch 变更。无隐藏 session kernel。

---

### I-6: core 内部依赖方向正确 — 下层不依赖上层

| 项 | 值 |
|---|---|
| 分类 | 架构合规确认 |

| 检查 | 结果 |
|---|---|
| core.mode → core.session | ✅ 无导入 |
| core.device → core.session | ✅ 无导入 |
| core.media → core.session | ✅ 无导入 |
| core.settings → 任何 core | ✅ 无导入（纯叶子模块） |
| core.session → core.mode/device/media | ✅ 预期方向（编排者依赖服务契约） |
| core.effect → core.media | ✅ 预期方向 |
| core.capability → core.media, core.effect | ✅ 预期方向 |

core/session 的 55 个跨模块符号导入全部指向 mode/device/media 的契约类型（接口、sealed interface、data class、enum），符合"session kernel 编排服务"的定位。

---

### I-7: core.session 的公开 API 设计正确

| 项 | 值 |
|---|---|
| 分类 | 架构合规确认 |

`CameraSession` 接口仅暴露:
- `state: StateFlow<SessionState>` (只读)
- `effects: Flow<SessionEffect>` (只读)
- `suspend fun dispatch(intent: SessionIntent)` (唯一变更入口)

`_state: MutableStateFlow<SessionState>` 为 private，无法从外部直接修改。intent 通过 channel 顺序处理，无并发状态变更。

---

### I-8: 75 个 app/ 源文件中仅 3 个存在具体类导入

| 项 | 值 |
|---|---|
| 分类 | 架构合规确认 |

| 文件 | 具体类导入数 | 评估 |
|---|---|---|
| AppContainer.kt | ~20 | 预期 (DI 组装) |
| CameraXCaptureAdapter.kt | 7 | 适配器层可接受 |
| SessionPreviewRenderModel.kt | 1 | 轻微，可通过接口消除 |

其余 72 个文件仅导入接口、enum、data class、sealed interface 或 top-level function。

---

### I-9: 核心层依赖拓扑总结

```
                    ┌─────────────┐
                    │  settings   │ (leaf)
                    └──────┬──────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
     ┌──────▼──────┐ ┌────▼─────┐ ┌──────▼──────┐
     │   device    │ │  effect  │ │ capability  │
     └──────┬──────┘ └────┬─────┘ └──────┬──────┘
            │              │              │
            │    ┌─────────▼─────────┐    │
            └───▶│      media        │◀───┘
                 └─────────┬─────────┘
                           │
                 ┌─────────▼─────────┐
                 │       mode        │ (api fan-out to device,media,settings,effect,capability)
                 └─────────┬─────────┘
                           │
                 ┌─────────▼─────────┐
                 │      session      │ (implementation dep on all above)
                 └─────────┬─────────┘
                           │
            ┌──────────────▼──────────────┐
            │           app/              │ (composition root, adapters, UI)
            └──────────────┬──────────────┘
                           │
            ┌──────────────▼──────────────┐
            │        feature/             │ (mode plugins, depends only on core contracts)
            └─────────────────────────────┘
```

唯一结构性问题: media ↔ capability 循环 (C-1)。
