# Package 08: Architecture Boundary Violations

## Package ID
`08-architecture-boundary-violations`

## Objective

检测 OpenCamera 项目中的架构边界违规。使用 abstraction-architect 和自定义扫描工具，识别层次违规、职责混乱、依赖方向错误等问题。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/08-architecture-boundary-violations.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- `01-architecture-structural-analysis` must be completed
- `02-technical-debt-audit` must be completed

## Acceptance Criteria

1. 产出详细的架构边界违规检测报告到 status 文件
2. 识别至少 10 个层次违规
3. 识别至少 5 个职责混乱问题
4. 识别至少 3 个依赖方向违规
5. 提供具体的修复建议

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/08-architecture-boundary-violations.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/08-architecture-boundary-violations.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 违规清单
- 违规严重程度排序
- 修复建议
- 修复优先级
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/08-architecture-boundary-violations`
- Worktree: `.worktrees/08-architecture-boundary-violations`
- Base: `main`

## Unlock Condition

- `01-architecture-structural-analysis` must be completed
- `02-technical-debt-audit` must be completed

## Analysis Scope

### 1. 层次违规检测
- UI 层直接访问设备层
- Mode Plugin 直接调用 CameraX/Camera2
- Session Kernel 外部的状态修改
- Media Pipeline 的职责越界

### 2. 职责混乱检测
- God Class 识别
- 职责重叠检测
- 职责缺失识别
- 职责分配不当

### 3. 依赖方向违规
- 稳定依赖原则违反
- 抽象依赖具体
- 高层依赖低层
- 循环依赖

### 4. 接口隔离违反
- 胖接口识别
- 接口污染
- 接口隔离原则违反
- 依赖倒置原则违反

## Analysis Tools

### Primary: abstraction-architect
- 架构边界分析
- 违规模式识别
- 修复建议

### Secondary: 源码搜索与静态阅读
- 依赖方向分析
- 调用链追踪
- 影响范围分析

### Tertiary: 自定义扫描
- 架构规则检查
- 边界违规检测
- 模式匹配

## Key Files to Analyze

### Architecture Boundaries
- `core/session/` vs `app/` - Session 和 App 边界
- `core/device/` vs `app/camera/` - Device 和 Camera 边界
- `core/mode/` vs `feature/` - Mode 和 Feature 边界
- `core/media/` vs `app/camera/` - Media 和 Camera 边界

### Cross-Cutting Concerns
- `core/settings/` - 设置
- `core/capability/` - 能力
- `core/effect/` - 效果

### Integration Points
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
