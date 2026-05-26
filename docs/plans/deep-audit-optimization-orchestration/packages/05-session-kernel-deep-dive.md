# Package 05: Session Kernel Deep Dive

## Package ID
`05-session-kernel-deep-dive`

## Objective

对 OpenCamera 项目的核心模块 Session Kernel 进行深度分析。使用 abstraction-architect 和 systematic-debugging 技能，识别架构问题、优化机会和改进建议。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/05-session-kernel-deep-dive.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- `01-architecture-structural-analysis` must be completed first

## Acceptance Criteria

1. 产出详细的 Session Kernel 深度分析报告到 status 文件
2. 识别至少 5 个架构问题
3. 识别至少 3 个优化机会
4. 提供具体的改进建议
5. 包含状态机图或结构化表示

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/05-session-kernel-deep-dive.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/05-session-kernel-deep-dive.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- Session 架构分析
- 状态管理评估
- 并发安全分析
- 优化建议
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/05-session-kernel-deep-dive`
- Worktree: `.worktrees/05-session-kernel-deep-dive`
- Base: `main`

## Unlock Condition

- `01-architecture-structural-analysis` must be completed

## Analysis Scope

### 1. Session 状态管理分析
- 状态机设计
- 状态转换逻辑
- 状态持久化
- 状态恢复机制

### 2. Session 生命周期分析
- 创建和初始化
- 配置和重配置
- 暂停和恢复
- 销毁和清理

### 3. Session 并发分析
- 线程安全机制
- 协程使用模式
- 状态同步策略
- 死锁风险

### 4. Session 错误处理分析
- 错误传播路径
- 恢复机制
- 降级策略
- 用户反馈

## Analysis Tools

### Primary: abstraction-architect
- 结构性架构分析
- 抽象机会识别
- 迁移接缝分析

### Secondary: systematic-debugging
- 系统性问题诊断
- 根因分析
- 修复策略

### Tertiary: CodeGraph
- Session 相关符号分析
- 调用链追踪
- 依赖分析

## Key Files to Analyze

### Session Core
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionTrace.kt`

### Session State
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionState.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionEffect.kt`

### Session Integration
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
