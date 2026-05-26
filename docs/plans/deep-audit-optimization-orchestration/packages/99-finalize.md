# Package 99: Finalize

## Package ID
`99-finalize`

## Objective

整合所有分析结果，生成最终综合报告。验证所有 package 的产出，确保分析质量和完整性。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/FINAL_REPORT.md`
- `docs/plans/deep-audit-optimization-orchestration/status/99-finalize.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- All package status files (read only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

- All functional packages (01-10) must be completed

## Finalize Steps

### 1. 验证所有 Package 产出
- 检查所有 status 文件
- 验证分析报告完整性
- 确认 acceptance criteria 满足
- 验证未修改任何源代码

### 2. 整合分析结果
- 汇总所有发现
- 消除重复和冲突
- 统一优先级排序
- 整合优化建议

### 3. 生成最终报告
- 执行摘要
- 详细发现
- 优化路线图
- 实施建议

### 4. 清理工作
- 删除临时文件
- 整理文档结构
- 更新状态文件

## Verification Commands

```bash
# 验证最终报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/FINAL_REPORT.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/FINAL_REPORT.md

# 验证所有 package 状态
cat docs/plans/deep-audit-optimization-orchestration/status/state.tsv

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/" || echo "No source code changes"
```

## Acceptance Criteria

1. 所有 functional packages 已完成
2. 最终综合报告已生成
3. 报告包含所有分析结果
4. 报告提供清晰的优化路线图
5. 报告包含可执行的实施建议
6. 确认未修改任何源代码

## Expected Evidence

- 最终综合报告
- 所有 package 状态验证
- 分析结果整合
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/99-finalize`
- Worktree: `.worktrees/99-finalize`
- Base: `main`

## Unlock Condition

- All functional packages (01-10) must be completed

## Input Files (Read from other package status files)

- `status/01-architecture-structural-analysis.md`
- `status/02-technical-debt-audit.md`
- `status/03-module-dependency-analysis.md`
- `status/04-code-quality-metrics.md`
- `status/05-session-kernel-deep-dive.md`
- `status/06-performance-optimization-analysis.md`
- `status/07-test-coverage-gap-analysis.md`
- `status/08-architecture-boundary-violations.md`
- `status/09-optimization-roadmap.md`
- `status/10-executable-action-plan.md`
- `status/state.tsv`
