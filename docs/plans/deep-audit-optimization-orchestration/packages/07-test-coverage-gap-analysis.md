# Package 07: Test Coverage Gap Analysis

## Package ID
`07-test-coverage-gap-analysis`

## Objective

对 OpenCamera 项目的测试覆盖率进行全面分析。识别测试缺口、测试质量问题和测试策略改进建议。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/07-test-coverage-gap-analysis.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- `04-code-quality-metrics` must be completed first

## Acceptance Criteria

1. 产出详细的测试覆盖率缺口分析报告到 status 文件
2. 识别 Top 20 测试缺口
3. 识别至少 10 个测试质量问题
4. 提供测试策略改进建议
5. 包含覆盖率数据和趋势

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/07-test-coverage-gap-analysis.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/07-test-coverage-gap-analysis.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 覆盖率统计
- 测试缺口清单
- 质量问题清单
- 改进建议
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/07-test-coverage-gap-analysis`
- Worktree: `.worktrees/07-test-coverage-gap-analysis`
- Base: `main`

## Unlock Condition

- `04-code-quality-metrics` must be completed

## Analysis Scope

### 1. 测试覆盖率分析
- 行覆盖率
- 分支覆盖率
- 方法覆盖率
- 类覆盖率

### 2. 测试缺口识别
- 未测试的类
- 未测试的方法
- 未测试的分支
- 边界条件覆盖

### 3. 测试质量分析
- 测试隔离性
- 测试可重复性
- 测试执行速度
- 测试维护性

### 4. 测试策略评估
- 单元测试策略
- 集成测试策略
- 端到端测试策略
- 测试金字塔平衡

## Analysis Tools

### Primary: 测试覆盖率工具
- JaCoCo 覆盖率分析
- 测试执行报告
- 覆盖率趋势

### Secondary: 源码搜索与静态阅读
- 测试代码分析
- 测试依赖分析
- 测试调用链

### Tertiary: 自定义测试分析
- 测试模式识别
- 测试质量问题检测
- 测试策略评估

## Key Files to Analyze

### Test Files
- `app/src/test/java/com/opencamera/app/` - App 测试
- `core/session/src/test/kotlin/com/opencamera/core/session/` - Session 测试
- `core/device/src/test/kotlin/com/opencamera/core/device/` - Device 测试
- `core/media/src/test/kotlin/com/opencamera/core/media/` - Media 测试
- `core/settings/src/test/kotlin/com/opencamera/core/settings/` - Settings 测试
- `core/effect/src/test/kotlin/com/opencamera/core/effect/` - Effect 测试

### Key Test Files
- `DefaultCameraSessionTest.kt` - Session 测试
- `SessionDiagnosticsTest.kt` - 诊断测试
- `CameraSessionCoordinatorTest.kt` - 协调器测试
- `CameraXCaptureAdapterTest.kt` - 适配器测试
- `SessionUiRenderModelTest.kt` - UI 模型测试
