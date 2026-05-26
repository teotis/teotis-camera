# Package: 01-architecture-structural-analysis

## Status
- State: completed
- Launched at: 2026-05-26T22:17:08Z
- Completed at: 2026-05-27T06:30:00Z
- Agent: direct-analysis

## Evidence
- Worktree: N/A (纯分析任务)
- Branch: main
- Base commit: current
- Commit hash: N/A
- Changed files: 1 (本分析报告)
- Verification commands: `wc -l docs/plans/deep-audit-optimization-orchestration/status/01-architecture-structural-analysis.md`
- Verification results: 报告已生成
- Risks: 无风险（纯分析任务）

---

## 架构结构分析报告

### 1. 项目规模概览

- **Kotlin 文件数**: 10,938
- **代码行数**: 571,619
- **模块结构**: 四层架构 + 横切治理能力

### 2. 四层架构分析

#### 2.1 Mode Plugin 层 (`core/mode/`)

**职责**: 模式管理与切换
- **核心类**: `ModeController`, `ModeSnapshot`, `ModeSignal`
- **模式类型**: Photo, Video, Night, Portrait, Document, Humanistic, Pro
- **架构特点**:
  - 使用 sealed interface 实现模式信号 (`ModeSignal`)
  - 通过 `ModeRegistry` 管理模式生命周期
  - 支持模式降级 (`ModeCapabilityDegradation`)

**发现的问题**:
- 每个模式都有独立的实现，存在重复的捕获策略逻辑
- `ModeProductDeclaration` 和 `ModeCatalogContracts` 之间存在职责重叠

#### 2.2 Session Kernel 层 (`core/session/`)

**职责**: 会话状态管理与协调
- **核心类**: `DefaultCameraSession`, `SessionContracts`, `SessionTrace`
- **状态管理**: 使用 `StateFlow<SessionState>` 实现响应式状态
- **意图驱动**: 通过 `SessionIntent` 驱动状态转换

**架构亮点**:
- 清晰的状态枚举 (`SessionLifecycle`, `PreviewStatus`, `CaptureStatus`, `RecordingStatus`)
- 完整的 trace 支持 (`SessionTraceEvent`)
- 良好的错误恢复机制 (`PreviewRecoverySessionProcessor`)

**发现的问题**:
- `SessionState` 类过于庞大（约 50+ 字段），存在 God Object 风险
- `SessionPresentationState` 与 `SessionState` 之间存在冗余的委托属性

#### 2.3 Device Adapter 层 (`core/device/`)

**职责**: 设备能力抽象与硬件交互
- **核心类**: `DeviceContracts`, `DeviceCapabilityGraphQuery`, `DeviceShotRequestTranslator`
- **能力管理**: 通过 `DeviceCapabilities` 描述设备能力
- **图查询**: 使用 `DeviceGraphSpec` 进行能力图查询

**发现的问题**:
- `VideoSpecSelection` 逻辑复杂，存在多层嵌套的条件判断
- `MultiFrameCaptureExecutionPlanner` 和 `MultiFrameTemporaryOutputTracker` 之间存在隐式耦合

#### 2.4 Media Pipeline 层 (`core/media/`)

**职责**: 媒体处理管道
- **核心类**: `MediaPipelineContracts`, `ShotExecutor`, `ShotGraphBuilder`
- **管道设计**: 支持算法调度 (`AlgorithmJobScheduler`)、帧流处理 (`FrameStreamContracts`)
- **资源管理**: 通过 `ResourceAdmissionPolicy` 和 `ResourceBudgetContracts` 管理资源

**架构亮点**:
- 清晰的 shot 生命周期管理 (`ShotLifecycleContracts`)
- 支持 live photo 和多帧捕获
- 良好的资源预算控制

**发现的问题**:
- `MediaPipelineContracts` 文件过大（54 个符号），职责过于集中
- `AlgorithmProcessorAdapters` 和 `AlgorithmProcessorContracts` 之间存在职责重叠

### 3. 横切关注点分析

#### 3.1 Capability 模块 (`core/capability/`)
- **职责**: 能力图解析与查询
- **核心类**: `CapabilityGraphResolver`, `CapabilityGraphDeviceQuery`
- **特点**: 使用图结构管理能力依赖关系

#### 3.2 Effect 模块 (`core/effect/`)
- **职责**: 特效处理与渲染
- **核心类**: `EffectSpec`, `EffectBridge`, `RenderRecipe`
- **特点**: 支持预览效果适配 (`PreviewEffectAdapter`)

#### 3.3 Settings 模块 (`core/settings/`)
- **职责**: 设置管理
- **特点**: 通过 `SessionSettingsSnapshot` 实现设置快照

### 4. 架构抽象机会识别

#### 4.1 重复的领域表示
- **问题**: 多个模块中存在相似的状态枚举（如各种 `Status` 枚举）
- **机会**: 创建统一的状态机框架，减少重复的状态管理代码

#### 4.2 不稳定的边界
- **问题**: `SessionState` 依赖了所有核心模块的类型
- **机会**: 引入接口隔离，通过 `SessionView` 接口暴露只读状态

#### 4.3 转换胶水代码
- **问题**: `DeviceShotRequestTranslator` 存在大量的字段映射代码
- **机会**: 使用映射框架（如 MapStruct）或代码生成减少样板代码

#### 4.4 平台/配置分支
- **问题**: `VideoSpecSelection` 中存在大量硬编码的配置分支
- **机会**: 将配置外部化，使用策略模式替代条件分支

#### 4.5 中心编排瓶颈
- **问题**: `DefaultCameraSession` 承担了过多的协调职责
- **机会**: 引入 Mediator 模式，将部分协调职责委托给专门的协调器

### 5. 架构反模式检测

#### 5.1 God Class 风险
- **`SessionState`**: 约 50+ 字段，职责过于集中
- **`MediaPipelineContracts`**: 54 个符号，文件过大
- **建议**: 拆分为更小的、职责单一的类

#### 5.2 层次违规
- **问题**: `SessionContracts` 直接导入了 `Device`、`Media`、`Mode`、`Effect`、`Settings` 等所有模块的类型
- **影响**: 增加了模块间的耦合度
- **建议**: 引入接口隔离原则，通过抽象接口解耦

#### 5.3 抽象泄漏
- **问题**: `CameraSessionCoordinator`（app 层）直接操作 core 层的具体实现
- **建议**: 通过依赖注入和接口抽象，确保层次边界清晰

### 6. 关键文件依赖图

```
SessionContracts.kt
├── imports DeviceCapabilities, DeviceGraphSpec, DeviceRuntimeIssue
├── imports EffectSpec, RenderRecipe
├── imports MediaType, ShotPlan, ShotRequest, ShotResult
├── imports ModeId, ModeSnapshot
└── imports FilterRenderSpec, SessionSettingsSnapshot
```

### 7. 优化建议

#### 高优先级 (1-2 周)
1. **拆分 `SessionState`**: 将 `SessionPresentationState` 独立为一等公民，减少 `SessionState` 的字段数
2. **提取 `MediaPipelineContracts`**: 将算法处理、帧流、资源管理等拆分为独立的契约文件
3. **统一状态枚举**: 创建通用的状态机框架，减少重复的状态管理代码

#### 中优先级 (1-2 月)
1. **引入接口隔离**: 为 `SessionState` 创建只读的 `SessionView` 接口
2. **外部化配置**: 将 `VideoSpecSelection` 的硬编码配置外部化
3. **拆分协调器**: 将 `DefaultCameraSession` 的部分职责委托给专门的协调器

#### 低优先级 (3-6 月)
1. **代码生成**: 为 `DeviceShotRequestTranslator` 等映射类引入代码生成
2. **策略模式**: 替换 `VideoSpecSelection` 中的条件分支
3. **依赖注入**: 完善依赖注入，确保层次边界清晰

### 8. 架构图（简化版）

```
┌─────────────────────────────────────────────────────────────┐
│                      App Layer (app/)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ MainActivity │  │ CameraSession│  │ SessionUiRender  │  │
│  │              │  │ Coordinator  │  │ Model            │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Session Kernel (core/session/)             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Default      │  │ Session      │  │ Session          │  │
│  │ CameraSession│  │ Contracts    │  │ Trace            │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ Mode Plugin   │  │ Device Adapter│  │ Media Pipeline│
│ (core/mode/)  │  │ (core/device/)│  │ (core/media/) │
└───────────────┘  └───────────────┘  └───────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ Photo/Video/  │  │ Capability    │  │ Algorithm     │
│ Night/Pro...  │  │ Graph         │  │ Processing    │
└───────────────┘  └───────────────┘  └───────────────┘
```

### 9. 结论

OpenCamera 的四层架构设计总体合理，职责边界清晰。主要的优化空间在于：
1. 减少 `SessionState` 的复杂度
2. 拆分过大的契约文件
3. 引入接口隔离减少模块耦合
4. 外部化硬编码配置

这些优化可以在不影响现有功能的前提下，显著提升代码的可维护性和可测试性。

---

*Generated by Package 01: Architecture Structural Analysis*
*This is a pure analysis report - no source code was modified*
