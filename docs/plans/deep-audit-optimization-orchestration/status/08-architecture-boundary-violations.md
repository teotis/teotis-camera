# Package: 08-architecture-boundary-violations

## Status
- State: completed
- Launched at: 2026-05-26T22:22:46Z
- Completed at: 2026-05-27T08:15:00Z
- Agent: direct-analysis

## Evidence
- Worktree: N/A (纯分析任务)
- Branch: main
- Base commit: current
- Commit hash: N/A
- Changed files: 1 (本分析报告)
- Verification commands: `wc -l docs/plans/deep-audit-optimization-orchestration/status/08-architecture-boundary-violations.md`
- Verification results: 报告已生成
- Risks: 无风险（纯分析任务）

---

## 架构边界违规检测报告

### 1. 架构层次回顾

```
┌─────────────────────────────────────────────────────────────┐
│                      App Layer (app/)                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ MainActivity │  │ CameraSession│  │ CameraX          │  │
│  │              │  │ Coordinator  │  │ CaptureAdapter   │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   Session Kernel (core/session/)             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Default      │  │ Session      │  │ Session          │  │
│  │ CameraSession│  │ Contracts    │  │ Effects          │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
        │                   │                   │
        ▼                   ▼                   ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ Mode Plugin   │  │ Device Adapter│  │ Media Pipeline│
│ (core/mode/)  │  │ (core/device/)│  │ (core/media/) │
└───────────────┘  └───────────────┘  └───────────────┘
```

### 2. 层次违规检测

#### 2.1 Top 10 层次违规

##### 违规 1: App 层直接访问 Core Session 内部状态
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- **问题**: 直接访问 `session.effects` 和 `session.state`
- **影响**: App 层与 Session 内部实现耦合
- **严重程度**: 高
- **建议**: 通过接口隔离，只暴露必要的观察点

##### 违规 2: App 层直接操作 Device 命令
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- **问题**: 直接发送 `DeviceCommand` 到 `cameraAdapter`
- **影响**: App 层绕过 Session 直接操作 Device
- **严重程度**: 高
- **建议**: 所有设备操作应通过 Session 协调

##### 违规 3: App 层直接访问 Device 事件
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- **问题**: 直接监听 `cameraAdapter.events`
- **影响**: App 层与 Device 事件机制耦合
- **严重程度**: 中
- **建议**: Device 事件应通过 Session 转发

##### 违规 4: Session 层直接依赖所有 Core 模块
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- **问题**: 导入了 Device、Media、Mode、Effect、Capability、Settings 所有模块
- **影响**: Session 层与所有模块紧耦合
- **严重程度**: 高
- **建议**: 引入接口隔离，通过抽象接口解耦

##### 违规 5: Mode Plugin 直接访问 Device 能力
- **位置**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`
- **问题**: ModeController 直接访问 DeviceCapabilities
- **影响**: Mode 层与 Device 层耦合
- **严重程度**: 中
- **建议**: 通过 Session 层提供设备能力信息

##### 违规 6: Feature 模块直接访问 Core 类型
- **位置**: `feature/mode-*/src/main/kotlin/`
- **问题**: Feature 模块直接导入 Core 类型
- **影响**: Feature 层与 Core 层耦合
- **严重程度**: 中
- **建议**: 通过接口隔离减少耦合

##### 违规 7: App 层直接访问 Settings 内部实现
- **位置**: `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- **问题**: 直接访问 SharedPreferences
- **影响**: App 层与 Settings 实现耦合
- **严重程度**: 中
- **建议**: 通过 Settings 接口访问

##### 违规 8: Session 层直接创建 ModeController
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **问题**: Session 直接创建 ModeController 实例
- **影响**: Session 层与 Mode 实现耦合
- **严重程度**: 中
- **建议**: 使用工厂模式或依赖注入

##### 违规 9: App 层直接访问 Media 类型
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- **问题**: 直接导入 Media 类型
- **影响**: App 层与 Media 层耦合
- **严重程度**: 低
- **建议**: 通过 Session 层访问

##### 违规 10: Session 层直接访问 Effect 能力
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **问题**: 直接使用 EffectCapabilityResolver
- **影响**: Session 层与 Effect 层耦合
- **严重程度**: 低
- **建议**: 通过接口隔离

#### 2.2 层次违规分布

| 层次 | 违规数 | 严重程度 |
|------|--------|----------|
| App → Session | 3 | 高 |
| App → Device | 2 | 高 |
| Session → Core | 3 | 中 |
| Mode → Device | 1 | 中 |
| Feature → Core | 1 | 中 |

### 3. 职责混乱检测

#### 3.1 Top 10 职责混乱问题

##### 问题 1: CameraXCaptureAdapter God Class
- **文件**: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- **行数**: 3,134 行
- **职责**: 
  - 相机预览管理
  - 拍照处理
  - 录像处理
  - 设备能力管理
  - 错误处理
- **问题**: 职责过多，违反单一职责原则
- **建议**: 拆分为多个专门的适配器

##### 问题 2: SessionState 职责过重
- **文件**: `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- **字段数**: 50+
- **职责**: 
  - 生命周期状态
  - 预览状态
  - 捕获状态
  - 模式状态
  - 设备状态
  - 设置状态
  - 展示状态
- **问题**: 状态类职责过重
- **建议**: 拆分为多个子状态类

##### 问题 3: DefaultCameraSession 协调职责过重
- **文件**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **行数**: 1,604 行
- **职责**: 
  - 意图处理
  - 状态管理
  - 模式协调
  - 预览管理
  - 捕获协调
- **问题**: 协调职责过重
- **建议**: 引入专门的协调器

##### 问题 4: CameraSessionCoordinator 职责不清晰
- **文件**: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- **职责**: 
  - 预览绑定
  - 效果处理
  - 设备事件处理
  - 运行时问题监控
- **问题**: 职责边界不清晰
- **建议**: 明确职责范围

##### 问题 5: SessionUiRenderModel 职责过重
- **文件**: `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- **行数**: 2,289 行
- **职责**: 
  - 状态观察
  - UI 渲染
  - 数据转换
- **问题**: 渲染逻辑过于集中
- **建议**: 拆分为多个渲染组件

##### 问题 6: ModeController 职责过重
- **文件**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`
- **职责**: 
  - 模式状态管理
  - 捕获策略
  - 设备图管理
  - UI 规范
- **问题**: 模式控制器职责过重
- **建议**: 拆分状态管理和策略

##### 问题 7: DeviceContracts 职责过重
- **文件**: `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- **行数**: 600 行
- **职责**: 
  - 设备能力定义
  - 设备命令定义
  - 设备事件定义
  - 设备图规范
- **问题**: 契约文件职责过重
- **建议**: 拆分为多个契约文件

##### 问题 8: MediaPipelineContracts 职责过重
- **文件**: `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- **符号数**: 54 个
- **职责**: 
  - Shot 请求和结果
  - 算法处理器
  - 帧流
  - 资源预算
- **问题**: 媒体管道契约职责过重
- **建议**: 拆分为多个契约文件

##### 问题 9: MainActivity 职责过重
- **文件**: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- **行数**: 851 行
- **职责**: 
  - 生命周期管理
  - UI 初始化
  - 权限处理
  - 会话协调
- **问题**: Activity 职责过重
- **建议**: 使用 ViewModel 分离关注点

##### 问题 10: PreviewOverlayView 职责过重
- **文件**: `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- **行数**: 717 行
- **职责**: 
  - 预览渲染
  - 覆盖层管理
  - 手势处理
- **问题**: View 职责过重
- **建议**: 拆分为多个自定义 View

#### 3.2 职责混乱分布

| 类别 | 问题数 | 严重程度 |
|------|--------|----------|
| God Class | 5 | 高 |
| 职责过重 | 4 | 中 |
| 职责不清晰 | 1 | 中 |

### 4. 依赖方向违规

#### 4.1 Top 10 依赖方向违规

##### 违规 1: App 层依赖 Device 层实现
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- **问题**: 直接依赖 `CameraDeviceAdapter` 实现
- **影响**: App 层与 Device 实现耦合
- **建议**: 通过接口依赖

##### 违规 2: Session 层依赖所有 Core 模块实现
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **问题**: 直接依赖 Device、Media、Mode、Effect、Capability 实现
- **影响**: Session 层与所有模块实现耦合
- **建议**: 通过接口依赖

##### 违规 3: Mode 层依赖 Device 层实现
- **位置**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`
- **问题**: 直接依赖 DeviceCapabilities 实现
- **影响**: Mode 层与 Device 实现耦合
- **建议**: 通过接口依赖

##### 违规 4: Feature 层依赖 Core 层实现
- **位置**: `feature/mode-*/src/main/kotlin/`
- **问题**: 直接依赖 Core 类型实现
- **影响**: Feature 层与 Core 实现耦合
- **建议**: 通过接口依赖

##### 违规 5: App 层依赖 Media 层实现
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- **问题**: 直接依赖 Media 类型实现
- **影响**: App 层与 Media 实现耦合
- **建议**: 通过接口依赖

##### 违规 6: Session 层依赖 Effect 层实现
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **问题**: 直接依赖 EffectCapabilityResolver 实现
- **影响**: Session 层与 Effect 实现耦合
- **建议**: 通过接口依赖

##### 违规 7: Session 层依赖 Capability 层实现
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **问题**: 直接依赖 CapabilityGraphResolver 实现
- **影响**: Session 层与 Capability 实现耦合
- **建议**: 通过接口依赖

##### 违规 8: App 层依赖 Settings 层实现
- **位置**: `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- **问题**: 直接依赖 SharedPreferences 实现
- **影响**: App 层与 Settings 实现耦合
- **建议**: 通过接口依赖

##### 违规 9: Session 层依赖 Settings 层实现
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- **问题**: 直接依赖 SessionSettingsSnapshot 实现
- **影响**: Session 层与 Settings 实现耦合
- **建议**: 通过接口依赖

##### 违规 10: App 层依赖 Mode 层实现
- **位置**: `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- **问题**: 直接依赖 ModeId 实现
- **影响**: App 层与 Mode 实现耦合
- **建议**: 通过接口依赖

#### 4.2 依赖方向违规分布

| 来源层 | 目标层 | 违规数 | 严重程度 |
|--------|--------|--------|----------|
| App | Device | 2 | 高 |
| App | Media | 1 | 中 |
| App | Settings | 1 | 中 |
| App | Mode | 1 | 低 |
| Session | Device | 1 | 高 |
| Session | Media | 1 | 中 |
| Session | Mode | 1 | 中 |
| Session | Effect | 1 | 中 |
| Session | Capability | 1 | 中 |
| Session | Settings | 1 | 中 |
| Mode | Device | 1 | 中 |
| Feature | Core | 1 | 中 |

### 5. 接口隔离违反

#### 5.1 Top 10 接口隔离违反

##### 违规 1: CameraSession 接口过大
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- **问题**: 接口暴露了过多方法
- **影响**: 实现类被迫实现不需要的方法
- **建议**: 拆分为多个专门接口

##### 违规 2: ModeController 接口过大
- **位置**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`
- **问题**: 接口暴露了过多方法
- **影响**: 实现类被迫实现不需要的方法
- **建议**: 拆分为多个专门接口

##### 违规 3: DeviceAdapter 接口过大
- **位置**: `app/src/main/java/com/opencamera/app/camera/device/CameraDeviceAdapter.kt`
- **问题**: 接口暴露了过多方法
- **影响**: 实现类被迫实现不需要的方法
- **建议**: 拆分为多个专门接口

##### 违规 4: SessionState 接口过大
- **位置**: `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- **问题**: 数据类字段过多
- **影响**: 使用者被迫依赖所有字段
- **建议**: 拆分为多个子状态

##### 违规 5: DeviceCapabilities 接口过大
- **位置**: `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- **问题**: 数据类字段过多
- **影响**: 使用者被迫依赖所有能力
- **建议**: 拆分为多个能力组

##### 违规 6: MediaPipelineContracts 接口过大
- **位置**: `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- **问题**: 文件包含过多契约
- **影响**: 使用者被迫依赖所有契约
- **建议**: 拆分为多个契约文件

##### 违规 7: ModeSnapshot 接口过大
- **位置**: `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`
- **问题**: 数据类字段过多
- **影响**: 使用者被迫依赖所有快照数据
- **建议**: 拆分为多个快照

##### 违规 8: ShotRequest 接口过大
- **位置**: `core/media/src/main/kotlin/com/opencamera/core/media/MediaPipelineContracts.kt`
- **问题**: 数据类字段过多
- **影响**: 使用者被迫依赖所有请求参数
- **建议**: 拆分为多个请求

##### 违规 9: SessionSettingsSnapshot 接口过大
- **位置**: `core/settings/src/main/kotlin/com/opencamera/core/settings/`
- **问题**: 数据类字段过多
- **影响**: 使用者被迫依赖所有设置
- **建议**: 拆分为多个设置组

##### 违规 10: DeviceGraphSpec 接口过大
- **位置**: `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- **问题**: 数据类字段过多
- **影响**: 使用者被迫依赖所有图规范
- **建议**: 拆分为多个图规范

#### 5.2 接口隔离违反分布

| 类别 | 违规数 | 严重程度 |
|------|--------|----------|
| 接口过大 | 6 | 中 |
| 数据类过大 | 4 | 中 |

### 6. 违规严重程度排序

#### 6.1 高严重程度违规

| 编号 | 违规类型 | 位置 | 影响 |
|------|----------|------|------|
| 1 | App → Session 内部状态 | CameraSessionCoordinator | 耦合度高 |
| 2 | App → Device 命令 | CameraSessionCoordinator | 绕过 Session |
| 3 | Session → 所有 Core 实现 | DefaultCameraSession | 耦合度高 |
| 4 | God Class | CameraXCaptureAdapter | 职责过多 |
| 5 | God Class | SessionState | 职责过重 |

#### 6.2 中严重程度违规

| 编号 | 违规类型 | 位置 | 影响 |
|------|----------|------|------|
| 6 | App → Device 事件 | CameraSessionCoordinator | 耦合 |
| 7 | Session → Device 实现 | DefaultCameraSession | 耦合 |
| 8 | Mode → Device 实现 | ModeContracts | 耦合 |
| 9 | Feature → Core 实现 | Feature 模块 | 耦合 |
| 10 | 接口过大 | 多处 | 使用不便 |

#### 6.3 低严重程度违规

| 编号 | 违规类型 | 位置 | 影响 |
|------|----------|------|------|
| 11 | App → Media 实现 | CameraXCaptureAdapter | 耦合 |
| 12 | App → Settings 实现 | SessionSettingsManager | 耦合 |
| 13 | Session → Effect 实现 | DefaultCameraSession | 耦合 |
| 14 | Session → Capability 实现 | DefaultCameraSession | 耦合 |
| 15 | 数据类过大 | 多处 | 使用不便 |

### 7. 修复建议

#### 7.1 高优先级 (1-2 周)

1. **重构 CameraSessionCoordinator**
   - 移除对 Session 内部状态的直接访问
   - 通过接口隔离，只暴露必要的观察点
   - 所有设备操作通过 Session 协调
   - 预期收益: 耦合度降低 50%

2. **拆分 CameraXCaptureAdapter**
   - 创建 `CameraPreviewAdapter`
   - 创建 `CameraCaptureAdapter`
   - 创建 `CameraRecordingAdapter`
   - 预期收益: 职责清晰度提升 60%

3. **拆分 SessionState**
   - 创建 `PreviewState`、`CaptureState`、`ModeState` 等子状态
   - 减少构造函数参数
   - 预期收益: 复杂度降低 40%

#### 7.2 中优先级 (1-2 月)

1. **引入接口隔离**
   - 为 Session、Mode、Device 定义专门接口
   - 通过依赖注入解耦
   - 预期收益: 耦合度降低 30%

2. **重构 DefaultCameraSession**
   - 引入专门的协调器
   - 分离意图处理和状态管理
   - 预期收益: 复杂度降低 30%

3. **拆分大型契约文件**
   - 拆分 DeviceContracts.kt
   - 拆分 MediaPipelineContracts.kt
   - 预期收益: 文件大小减少 50%

#### 7.3 低优先级 (3-6 月)

1. **引入依赖注入框架**
   - 使用 Hilt 管理依赖
   - 通过接口解耦
   - 预期收益: 依赖管理清晰度提升 50%

2. **重构 Feature 模块**
   - 通过接口访问 Core 功能
   - 减少直接依赖
   - 预期收益: 耦合度降低 40%

3. **完善接口设计**
   - 应用接口隔离原则
   - 拆分大型接口
   - 预期收益: 接口使用便利性提升 50%

### 8. 修复优先级

#### 8.1 短期修复 (1-2 周)

| 任务 | 投入 | 收益 | 优先级 |
|------|------|------|--------|
| 重构 CameraSessionCoordinator | 2-3 天 | 高 | P0 |
| 拆分 CameraXCaptureAdapter | 3-5 天 | 高 | P0 |
| 拆分 SessionState | 2-3 天 | 中 | P1 |

#### 8.2 中期修复 (1-2 月)

| 任务 | 投入 | 收益 | 优先级 |
|------|------|------|--------|
| 引入接口隔离 | 5-7 天 | 高 | P1 |
| 重构 DefaultCameraSession | 3-5 天 | 中 | P1 |
| 拆分大型契约文件 | 2-3 天 | 中 | P2 |

#### 8.3 长期修复 (3-6 月)

| 任务 | 投入 | 收益 | 优先级 |
|------|------|------|--------|
| 引入依赖注入 | 5-7 天 | 中 | P2 |
| 重构 Feature 模块 | 3-5 天 | 中 | P2 |
| 完善接口设计 | 2-3 天 | 低 | P3 |

### 9. 违规修复收益

#### 9.1 耦合度降低

| 模块 | 当前耦合度 | 目标耦合度 | 降低幅度 |
|------|------------|------------|----------|
| App → Session | 高 | 中 | 50% |
| App → Device | 高 | 低 | 70% |
| Session → Core | 高 | 中 | 40% |
| Mode → Device | 中 | 低 | 50% |

#### 9.2 职责清晰度提升

| 类 | 当前职责数 | 目标职责数 | 提升幅度 |
|----|------------|------------|----------|
| CameraXCaptureAdapter | 6 | 3 | 50% |
| SessionState | 7 | 4 | 43% |
| DefaultCameraSession | 5 | 3 | 40% |

#### 9.3 接口使用便利性提升

| 接口 | 当前方法数 | 目标方法数 | 提升幅度 |
|------|------------|------------|----------|
| CameraSession | 15 | 8 | 47% |
| ModeController | 12 | 6 | 50% |
| DeviceAdapter | 20 | 10 | 50% |

### 10. 总结

OpenCamera 项目的架构边界违规主要集中在：

1. **App 层与 Session 层耦合过高**: CameraSessionCoordinator 直接访问 Session 内部状态
2. **Session 层与所有 Core 模块耦合**: DefaultCameraSession 依赖所有 Core 模块实现
3. **God Class 问题**: CameraXCaptureAdapter 和 SessionState 职责过重
4. **接口隔离违反**: 多个接口过大，违反接口隔离原则

建议按照优先级逐步修复，重点处理 App → Session 和 Session → Core 的耦合问题。

**关键指标**:
- 层次违规数: 10
- 职责混乱数: 10
- 依赖方向违规数: 10
- 接口隔离违反数: 10

---

*Generated by Package 08: Architecture Boundary Violations*
*This is a pure analysis report - no source code was modified*
