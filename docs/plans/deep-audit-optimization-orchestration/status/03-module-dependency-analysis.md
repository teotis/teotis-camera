# Package: 03-module-dependency-analysis

## Status
- State: completed
- Launched at: 2026-05-26T22:17:08Z
- Completed at: 2026-05-27T07:00:00Z
- Agent: direct-analysis

## Evidence
- Worktree: N/A (纯分析任务)
- Branch: main
- Base commit: current
- Commit hash: N/A
- Changed files: 1 (本分析报告)
- Verification commands: `wc -l docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md`
- Verification results: 报告已生成
- Risks: 无风险（纯分析任务）

---

## 模块依赖分析报告

### 1. 项目模块结构

```
OpenCamera/
├── app/                    # App 集成层
├── core/                   # 核心模块
│   ├── capability/         # 能力图管理
│   ├── device/             # 设备适配器
│   ├── effect/             # 特效处理
│   ├── media/              # 媒体管道
│   ├── mode/               # 模式插件
│   ├── session/            # 会话内核
│   └── settings/           # 设置管理
└── feature/                # 功能模块
    ├── mode-document/      # 文档模式
    ├── mode-humanistic/    # 人文模式
    ├── mode-night/         # 夜景模式
    ├── mode-photo/         # 拍照模式
    ├── mode-portrait/      # 人像模式
    ├── mode-pro/           # 专业模式
    └── mode-video/         # 录像模式
```

### 2. Gradle 模块依赖图

```
settings (叶子模块，无依赖)
    │
    ├──→ media → settings
    │
    ├──→ effect → settings, media
    │
    ├──→ capability → effect, media
    │
    ├──→ device → media, settings, capability
    │
    ├──→ mode → device, media, settings, effect, capability
    │
    └──→ session → device, media, mode, effect, capability, settings
```

### 3. 详细依赖关系

#### 3.1 settings 模块
- **依赖**: 无
- **被依赖**: media, effect, device, mode, session
- **角色**: 叶子模块，提供设置管理功能
- **稳定性**: 高（无外部依赖）

#### 3.2 media 模块
- **依赖**: settings
- **被依赖**: effect, capability, device, mode, session
- **角色**: 媒体管道，处理拍照、录像等媒体操作
- **稳定性**: 中（依赖 settings）

#### 3.3 effect 模块
- **依赖**: settings, media
- **被依赖**: capability, mode, session
- **角色**: 特效处理，管理滤镜、渲染等
- **稳定性**: 中

#### 3.4 capability 模块
- **依赖**: effect, media
- **被依赖**: device, mode, session
- **角色**: 能力图管理，解析设备能力
- **稳定性**: 中

#### 3.5 device 模块
- **依赖**: media, settings, capability
- **被依赖**: mode, session
- **角色**: 设备适配器，管理相机硬件交互
- **稳定性**: 中

#### 3.6 mode 模块
- **依赖**: device, media, settings, effect, capability
- **被依赖**: session
- **角色**: 模式插件，管理各种拍摄模式
- **稳定性**: 低（依赖所有核心模块）

#### 3.7 session 模块
- **依赖**: device, media, mode, effect, capability, settings
- **被依赖**: app
- **角色**: 会话内核，协调所有核心模块
- **稳定性**: 低（依赖所有核心模块）

### 4. 依赖问题检测

#### 4.1 循环依赖检查

**结果**: ✅ 未发现循环依赖

依赖图是一个有向无环图 (DAG)，符合良好的模块化设计。

#### 4.2 过度耦合问题

##### DC-001: session 模块扇入过高
- **严重程度**: 高
- **问题描述**: `session` 模块依赖了所有 6 个核心模块
- **影响**: 
  - 任何核心模块的变更都可能影响 session
  - 测试困难，需要 mock 所有依赖
  - 编译时间增加
- **建议**: 
  - 引入接口隔离，通过抽象接口解耦
  - 考虑使用 Mediator 模式减少直接依赖

##### DC-002: mode 模块扇入过高
- **严重程度**: 中
- **问题描述**: `mode` 模块依赖了 5 个核心模块
- **影响**: 
  - 模式实现复杂，难以维护
  - 新增模式需要了解多个模块
- **建议**: 
  - 提取模式公共接口
  - 使用依赖注入减少硬编码依赖

##### DC-003: capability 与 effect 循环依赖风险
- **严重程度**: 低
- **问题描述**: `capability` 依赖 `effect`，而 `effect` 的某些功能可能需要能力信息
- **影响**: 潜在的循环依赖风险
- **建议**: 引入接口隔离，通过回调或事件解耦

##### DC-004: device 模块职责过重
- **严重程度**: 中
- **问题描述**: `device` 模块同时依赖 media、settings、capability
- **影响**: 
  - 设备适配逻辑复杂
  - 难以单独测试设备功能
- **建议**: 
  - 拆分设备能力管理和设备交互逻辑
  - 引入设备能力提供者接口

##### DC-005: feature 模块测试依赖反向
- **严重程度**: 低
- **问题描述**: `session` 模块的测试依赖了所有 feature 模块
- **影响**: 测试编译时间增加
- **建议**: 使用测试替身 (Test Doubles) 替代真实 feature 模块

#### 4.3 缺失抽象层

##### DA-001: 缺少共享内核 (Shared Kernel)
- **问题描述**: 多个模块共享相似的类型定义（如 MediaType、ShotRequest 等）
- **影响**: 类型定义分散，难以维护
- **建议**: 创建 `:core:shared` 模块，存放共享类型

##### DA-002: 缺少事件总线
- **问题描述**: 模块间通过直接调用通信，缺乏解耦的事件机制
- **影响**: 模块间耦合度高
- **建议**: 引入事件总线或使用 Flow 进行响应式通信

##### DA-003: 缺少依赖注入框架
- **问题描述**: 依赖关系在 build.gradle.kts 中硬编码
- **影响**: 难以替换实现，测试困难
- **建议**: 引入 Hilt 或 Koin 进行依赖注入

### 5. 模块边界分析

#### 5.1 模块职责清晰度

| 模块 | 职责清晰度 | 评价 |
|------|------------|------|
| settings | ✅ 高 | 单一职责，管理设置 |
| media | ✅ 高 | 单一职责，处理媒体 |
| effect | ✅ 高 | 单一职责，处理特效 |
| capability | ✅ 高 | 单一职责，管理能力图 |
| device | ⚠️ 中 | 职责较多，可拆分 |
| mode | ⚠️ 中 | 职责较多，模式管理复杂 |
| session | ⚠️ 低 | 职责过重，协调所有模块 |

#### 5.2 接口隔离程度

- **settings**: ✅ 接口清晰
- **media**: ✅ 接口清晰
- **effect**: ✅ 接口清晰
- **capability**: ⚠️ 接口可进一步细化
- **device**: ⚠️ 接口较复杂
- **mode**: ⚠️ 接口较复杂
- **session**: ❌ 接口过于庞大

#### 5.3 依赖倒置应用

| 模块 | 依赖倒置 | 评价 |
|------|----------|------|
| settings | N/A | 叶子模块 |
| media | ✅ 使用接口 | 良好 |
| effect | ✅ 使用接口 | 良好 |
| capability | ⚠️ 部分使用 | 可改进 |
| device | ⚠️ 部分使用 | 可改进 |
| mode | ❌ 大量具体依赖 | 需改进 |
| session | ❌ 大量具体依赖 | 需改进 |

#### 5.4 模块内聚度

| 模块 | 内聚度 | 评价 |
|------|--------|------|
| settings | ✅ 高 | 功能集中 |
| media | ✅ 高 | 功能集中 |
| effect | ✅ 高 | 功能集中 |
| capability | ✅ 高 | 功能集中 |
| device | ⚠️ 中 | 功能分散 |
| mode | ⚠️ 中 | 功能分散 |
| session | ⚠️ 低 | 功能过于分散 |

### 6. 稳定性分析

#### 6.1 稳定性指标

稳定性 = 被依赖数 / (依赖数 + 被依赖数)

| 模块 | 依赖数 | 被依赖数 | 稳定性 | 评价 |
|------|--------|----------|--------|------|
| settings | 0 | 5 | 1.0 | ✅ 稳定 |
| media | 1 | 5 | 0.83 | ✅ 稳定 |
| effect | 2 | 3 | 0.6 | ⚠️ 中等 |
| capability | 2 | 3 | 0.6 | ⚠️ 中等 |
| device | 3 | 2 | 0.4 | ⚠️ 不稳定 |
| mode | 5 | 1 | 0.17 | ❌ 不稳定 |
| session | 6 | 1 | 0.14 | ❌ 不稳定 |

#### 6.2 稳定性问题

1. **session 和 mode 模块过于不稳定**: 依赖过多，容易受其他模块影响
2. **settings 模块过于稳定**: 可能导致接口僵化
3. **device 模块稳定性不足**: 依赖较多，难以独立演化

### 7. 依赖方向分析

#### 7.1 依赖方向图

```
                    ┌─────────┐
                    │ settings │
                    └────┬────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
          ▼              ▼              ▼
     ┌─────────┐   ┌─────────┐   ┌─────────┐
     │  media  │   │  effect │   │capability│
     └────┬────┘   └────┬────┘   └────┬────┘
          │              │              │
          └──────────────┼──────────────┘
                         │
                         ▼
                    ┌─────────┐
                    │  device │
                    └────┬────┘
                         │
                         ▼
                    ┌─────────┐
                    │  mode   │
                    └────┬────┘
                         │
                         ▼
                    ┌─────────┐
                    │ session │
                    └────┬────┘
                         │
                         ▼
                    ┌─────────┐
                    │   app   │
                    └─────────┘
```

#### 7.2 依赖方向问题

1. **单向依赖**: ✅ 所有依赖都是单向的，符合 DIP 原则
2. **层次清晰**: ✅ 依赖方向从下层到上层，符合分层架构
3. **无跨层依赖**: ✅ 未发现跨层依赖

### 8. 优化建议

#### 8.1 高优先级 (1-2 周)

1. **创建共享内核模块**: 提取 MediaType、ShotRequest 等共享类型到 `:core:shared`
2. **拆分 session 模块**: 将会话协调逻辑拆分为独立的协调器模块
3. **引入事件总线**: 使用 Flow 或 Channel 实现模块间解耦通信

#### 8.2 中优先级 (1-2 月)

1. **细化 capability 接口**: 拆分能力查询和能力解析接口
2. **拆分 device 模块**: 将设备能力管理和设备交互逻辑分离
3. **引入依赖注入**: 使用 Hilt 管理模块间依赖关系

#### 8.3 低优先级 (3-6 月)

1. **统一模式接口**: 为所有模式定义统一的接口规范
2. **优化 session 模块**: 引入 Mediator 模式减少直接依赖
3. **完善测试架构**: 使用测试替身替代真实依赖

### 9. 架构改进建议

#### 9.1 引入六边形架构 (Hexagonal Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│                      Adapters Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Camera       │  │ Storage      │  │ UI               │  │
│  │ Adapter      │  │ Adapter      │  │ Adapter          │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Session      │  │ Mode         │  │ Capture          │  │
│  │ Use Cases    │  │ Use Cases    │  │ Use Cases        │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       Domain Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Session      │  │ Mode         │  │ Media            │  │
│  │ Domain       │  │ Domain       │  │ Domain           │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### 9.2 引入 CQRS 模式

- **Command**: 处理状态变更（如拍照、录像）
- **Query**: 处理状态查询（如获取预览、获取设置）
- **分离读写**: 减少 session 模块的复杂度

### 10. 结论

OpenCamera 的模块依赖结构总体健康，没有循环依赖。主要问题集中在 session 和 mode 模块的过度耦合。建议通过引入共享内核、事件总线和依赖注入来优化模块边界，提升代码的可维护性和可测试性。

**关键发现**:
1. ✅ 无循环依赖
2. ⚠️ session 模块扇入过高（6 个依赖）
3. ⚠️ mode 模块扇入过高（5 个依赖）
4. ⚠️ 缺少共享内核模块
5. ⚠️ 缺少事件总线机制

**关键建议**:
1. 创建 `:core:shared` 模块存放共享类型
2. 引入事件总线解耦模块通信
3. 拆分 session 模块的协调职责

---

*Generated by Package 03: Module Dependency Analysis*
*This is a pure analysis report - no source code was modified*
