# Package 09: Optimization Roadmap

## Package ID
`09-optimization-roadmap`

## Objective

综合前序分析结果，制定优先级排序的优化路线图。将所有发现转化为可执行的优化计划，提供清晰的实施路径。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/09-optimization-roadmap.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- `05-session-kernel-deep-dive` must be completed
- `06-performance-optimization-analysis` must be completed
- `07-test-coverage-gap-analysis` must be completed
- `08-architecture-boundary-violations` must be completed

## Acceptance Criteria

1. 产出详细的优化路线图到 status 文件
2. 包含优先级排序的优化项
3. 提供清晰的时间线
4. 包含资源估算
5. 提供风险评估

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/09-optimization-roadmap.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/09-optimization-roadmap.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 优化项清单
- 优先级排序
- 时间线规划
- 资源估算
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/09-optimization-roadmap`
- Worktree: `.worktrees/09-optimization-roadmap`
- Base: `main`

## Unlock Condition

- `05-session-kernel-deep-dive` must be completed
- `06-performance-optimization-analysis` must be completed
- `07-test-coverage-gap-analysis` must be completed
- `08-architecture-boundary-violations` must be completed

## Analysis Scope

### 1. 发现整合
- 架构问题汇总
- 技术债务汇总
- 性能问题汇总
- 测试缺口汇总
- 边界违规汇总

### 2. 优先级排序
- 影响程度评估
- 实施难度评估
- 风险评估
- ROI 评估

### 3. 路线图制定
- 短期优化（1-2 周）
- 中期优化（1-2 月）
- 长期优化（3-6 月）
- 里程碑规划

### 4. 资源估算
- 人力需求
- 时间需求
- 技能需求
- 工具需求

## Analysis Tools

### Primary: 综合分析
- 多维度数据整合
- 优先级算法
- 路线图生成

### Secondary: 可视化工具
- 甘特图
- 依赖图
- 优先级矩阵

## Input Files (Read from other package status files)

- `status/01-architecture-structural-analysis.md`
- `status/02-technical-debt-audit.md`
- `status/03-module-dependency-analysis.md`
- `status/04-code-quality-metrics.md`
- `status/05-session-kernel-deep-dive.md`
- `status/06-performance-optimization-analysis.md`
- `status/07-test-coverage-gap-analysis.md`
- `status/08-architecture-boundary-violations.md`
