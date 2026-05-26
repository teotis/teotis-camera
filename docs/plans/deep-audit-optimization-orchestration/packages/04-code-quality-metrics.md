# Package 04: Code Quality Metrics - 代码质量度量

## Package ID
`04-code-quality-metrics`

## Objective

对 OpenCamera 项目的代码质量进行全面度量。识别代码复杂度热点、重复代码、坏味道等问题，为优化提供数据支持。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/04-code-quality-metrics.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files)

## Forbidden Paths

- All source code files
- Build files
- Configuration files

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

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/04-code-quality-metrics.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/04-code-quality-metrics.md
```

## Acceptance Criteria

1. 产出详细的代码质量度量报告
2. 识别 Top 20 复杂度热点
3. 识别 Top 10 重复代码热点
4. 识别至少 15 种代码坏味道
5. 提供质量改进建议

## Evidence Requirements

- 复杂度分布统计
- 重复代码统计
- 坏味道清单
- 质量改进建议
- 验证命令执行结果

## Branch Policy

- Branch: `agent/deep-audit/04-code-quality-metrics`
- Worktree: `.worktrees/04-code-quality-metrics`
- Base: `main`

## Unlock Condition

- 无依赖，可立即开始

## Expected Duration

- 分析时间：30-60 分钟
- 报告生成：20-30 分钟

## Notes

- 重点关注 CameraXCaptureAdapter.kt（132KB）等大文件
- 分析 Session Kernel 的复杂度
- 识别可重构的热点
- 不要修改任何源代码
