# Package 99: 合并报告

## Package ID
`99-finalize`

## Goal
将三个扫描维度的结果合并为一份完整的代码质量报告，写入项目 docs 目录。

## Allowed Paths
- `docs/plans/code-quality-scan-orchestration/scratch/01-static-analysis/` (只读)
- `docs/plans/code-quality-scan-orchestration/scratch/02-arch-compliance/` (只读)
- `docs/plans/code-quality-scan-orchestration/scratch/03-test-coverage/` (只读)
- `docs/plans/code-quality-scan-orchestration/status/` (读写)
- `docs/code-quality-scan-report.md` (最终输出)

## Forbidden Paths
- 不修改任何源代码
- 不修改 build 文件

## Dependencies
- 01-static-analysis (status)
- 02-arch-compliance (status)
- 03-test-coverage (status)

## Acceptance Criteria

1. 读取三个包的 scratch 报告
2. 合并为一份结构化报告 `docs/code-quality-scan-report.md`，包含：
   - 扫描概览（日期、范围、工具版本）
   - 静态分析结果（detekt + lint + ktlint）
   - 架构合规结果（依赖图 + 越层调用 + 大文件）
   - 测试覆盖结果（测试运行 + 分布 + 缺失）
   - 问题汇总（按严重程度排序的 top 问题列表）
   - 改进建议（基于扫描结果的优先级建议）
3. 报告格式清晰，支持按模块/按维度查阅
4. 记录扫描过程中遇到的工具问题或环境限制

## Verification Commands

```bash
# 检查报告文件存在且非空
wc -l docs/code-quality-scan-report.md
```

## Expected Evidence
- `docs/code-quality-scan-report.md` (最终合并报告)
- `status/99-finalize.md` (完成状态)

## Branch/Worktree Policy
- Branch: `agent/quality-scan/99-finalize`
- Worktree: 合并到 integration branch

## Unlock Conditions
所有功能包（01, 02, 03）状态为 completed

## Special Notes
- 本包不运行任何扫描工具，只做报告合并
- 如果某个包的报告缺失，在报告中标注"该维度扫描未完成"
- 不对扫描结果做价值判断，只做客观汇总和结构化呈现
