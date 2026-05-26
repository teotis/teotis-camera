# Package 99: Finalize - 最终收口

## Package ID
`99-finalize`

## Objective

整合所有分析结果，生成最终综合报告。验证所有 package 的产出，确保分析质量和完整性。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/FINAL_REPORT.md`
- `docs/plans/deep-audit-optimization-orchestration/status/99-finalize.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- All package status files

## Dependencies

- All functional packages (01-10) must be completed

## Finalize Steps

### 1. 验证所有 Package 产出
- 检查所有 status 文件
- 验证分析报告完整性
- 确认 acceptance criteria 满足

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
- 更新 INDEX.md 状态

## Verification Commands

```bash
# 验证最终报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/FINAL_REPORT.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/FINAL_REPORT.md

# 验证所有 package 状态
cat docs/plans/deep-audit-optimization-orchestration/status/state.tsv
```

## Acceptance Criteria

1. 所有 functional packages 已完成
2. 最终综合报告已生成
3. 报告包含所有分析结果
4. 报告提供清晰的优化路线图
5. 报告包含可执行的实施建议

## Evidence Requirements

- 最终综合报告
- 所有 package 状态验证
- 分析结果整合
- 验证命令执行结果

## Branch Policy

- Branch: `agent/deep-audit/99-finalize`
- Worktree: `.worktrees/99-finalize`
- Base: `main`

## Unlock Condition

- All functional packages (01-10) must be completed

## Expected Duration

- 整合时间：30-60 分钟
- 报告生成：30-45 分钟

## Notes

- 确保所有分析结果被整合
- 消除重复和冲突
- 提供清晰的执行摘要
- 不要修改任何源代码
