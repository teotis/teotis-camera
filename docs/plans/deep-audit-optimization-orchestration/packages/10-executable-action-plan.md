# Package 10: Executable Action Plan

## Package ID
`10-executable-action-plan`

## Objective

将优化路线图转化为详细的可执行行动计划。提供具体的实施步骤、代码位置、修改建议和验证方法。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/10-executable-action-plan.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- `09-optimization-roadmap` must be completed

## Acceptance Criteria

1. 产出详细的可执行行动计划到 status 文件
2. 包含具体的实施步骤
3. 提供代码位置标注
4. 包含验证方法
5. 提供风险缓解措施

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/10-executable-action-plan.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/10-executable-action-plan.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 行动项清单
- 实施步骤
- 代码位置
- 验证方法
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/10-executable-action-plan`
- Worktree: `.worktrees/10-executable-action-plan`
- Base: `main`

## Unlock Condition

- `09-optimization-roadmap` must be completed

## Analysis Scope

### 1. 行动项细化
- 具体实施步骤
- 代码位置标注
- 修改建议
- 验证方法

### 2. 实施指南
- 编码规范
- 测试要求
- 代码审查要点
- 文档要求

### 3. 风险缓解
- 风险识别
- 缓解措施
- 回滚方案
- 应急计划

### 4. 质量保证
- 验收标准
- 测试策略
- 监控指标
- 反馈机制

## Analysis Tools

### Primary: 综合分析
- 行动项生成
- 实施指南制定
- 风险评估

### Secondary: CodeGraph
- 代码位置标注
- 影响范围分析
- 依赖关系分析

## Input Files (Read from other package status files)

- `status/09-optimization-roadmap.md`
- All analysis reports from 01-08
