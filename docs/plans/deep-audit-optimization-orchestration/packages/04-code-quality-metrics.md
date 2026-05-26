# Package 04: Code Quality Metrics

## Package ID
`04-code-quality-metrics`

## Objective

对 OpenCamera 项目的代码质量进行全面度量。识别代码复杂度热点、重复代码、坏味道等问题，为优化提供数据支持。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/04-code-quality-metrics.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

None

## Acceptance Criteria

1. 产出详细的代码质量度量报告到 status 文件
2. 识别 Top 20 复杂度热点
3. 识别 Top 10 重复代码热点
4. 识别至少 15 种代码坏味道
5. 提供质量改进建议

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/04-code-quality-metrics.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/04-code-quality-metrics.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 复杂度分布统计
- 重复代码统计
- 坏味道清单
- 质量改进建议
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/04-code-quality-metrics`
- Worktree: `.worktrees/04-code-quality-metrics`
- Base: `main`

## Unlock Condition

- 无依赖，可立即开始

## Analysis Scope

### 1. 代码复杂度分析
- 圈复杂度（Cyclomatic Complexity）
- 认知复杂度（Cognitive Complexity）
- 方法长度分布
- 类大小分布
- 嵌套深度分析

### 2. 代码重复检测
- 文件级重复
- 方法级重复
- 代码块重复
- 重复代码热点

### 3. 代码坏味道识别
- Long Method
- Large Class
- Long Parameter List
- Divergent Change
- Shotgun Surgery
- Feature Envy
- Data Clumps
- Primitive Obsession
- Switch Statements
- Parallel Inheritance Hierarchies
- Lazy Class
- Speculative Generality
- Temporary Field
- Message Chains
- Middle Man

## Analysis Tools

### Primary: 自定义分析脚本
- Kotlin 代码解析
- 复杂度计算
- 重复检测

### Secondary: CodeGraph
- 符号分析
- 代码结构分析

## Key Files to Analyze

### Large Files (High Priority)
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` (132KB)
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` (44KB)
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` (49KB)
- `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt` (41KB)

### Core Session Files
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`

### App Layer Files
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
