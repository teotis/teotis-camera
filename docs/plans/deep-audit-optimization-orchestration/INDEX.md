# Deep Audit & Optimization Orchestration - 全面深度检查与优化

## Goal

对 OpenCamera 项目进行全面、彻底、深度的项目检查、优化分析和架构分析。利用 abstraction-architect、renewal-architect、systematic-debugging 等专业分析 skill，产出可执行的优化建议和实施计划。

**目标不是改代码，而是产出高质量的分析报告和优化路线图。**

## User Entry Points

- **Manual**: 从 `launchers/agent-prompts.md` 复制 prompts 到任何 agent 平台
- **Script**: 运行 `bash launchers/orchestrate.sh start`；用 `claude agents` 查看 Claude Code agents
- **Status**: 运行 `bash launchers/orchestrate.sh status`
- **Retry**: 运行 `bash launchers/orchestrate.sh retry <package-id>`
- **Manual fallback**: 运行 `bash launchers/orchestrate.sh advance`

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/deep-audit-optimization-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/deep-audit/integration`
- Functional package branches: `agent/deep-audit/<package-id>`
- Implementation isolation: one worktree per functional package
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch
- Edit only allowed paths (mainly `docs/plans/deep-audit-optimization-orchestration/status/`)
- Run listed verification commands
- Commit local package changes
- Write only their assigned coordinator status file and state row
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status

`99-finalize` is authorized by default to:
- Inspect package docs, status files, state, branches, commits, and diffs
- Create/update the integration branch
- Merge package branches into the integration branch
- Run integration verification
- Merge the verified integration branch back to mainline
- Write `FINAL_REPORT.md` and `status/99-finalize.md`
- Delete only local branches/worktrees created by this orchestration

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-architecture-structural-analysis | none | status | completed | 1 |
| 02-technical-debt-audit | none | status | completed | 1 |
| 03-module-dependency-analysis | none | status | completed | 1 |
| 04-code-quality-metrics | none | status | completed | 1 |
| 05-session-kernel-deep-dive | 01-architecture-structural-analysis | status | completed | 2 |
| 06-performance-optimization-analysis | 03-module-dependency-analysis | status | completed | 2 |
| 07-test-coverage-gap-analysis | 04-code-quality-metrics | status | completed | 2 |
| 08-architecture-boundary-violations | 01-architecture-structural-analysis, 02-technical-debt-audit | status | completed | 2 |
| 09-optimization-roadmap | 05-session-kernel-deep-dive, 06-performance-optimization-analysis, 07-test-coverage-gap-analysis, 08-architecture-boundary-violations | status | completed | 3 |
| 10-executable-action-plan | 09-optimization-roadmap | status | completed | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01, 02, 03, 04, 05, 06, 07, 08, 09, 10
- Code dependency policy: status dependency (each package writes to its own status file)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`
- Graph has duplicate package IDs, missing dependencies, or cycles
- Package evidence is incomplete
- Package changed forbidden paths
- Merge conflict or verification failure occurs
- Status/state mismatch cannot be reconciled

## Package Overview

### Wave 1: 基础分析（并行）

| Package | 分析类型 | 核心工具 | 产出 |
|---|---|---|---|
| 01-architecture-structural-analysis | 架构结构分析 | abstraction-architect | 结构性架构报告、抽象机会识别 |
| 02-technical-debt-audit | 技术债务审计 | renewal-architect | 技术债务清单、现代化路线图 |
| 03-module-dependency-analysis | 模块依赖分析 | CodeGraph + 自定义分析 | 依赖图、循环依赖检测、耦合度分析 |
| 04-code-quality-metrics | 代码质量度量 | 静态分析 + 自定义扫描 | 代码质量报告、复杂度热点 |

### Wave 2: 深度分析（依赖 Wave 1）

| Package | 分析类型 | 核心工具 | 产出 |
|---|---|---|---|
| 05-session-kernel-deep-dive | Session Kernel 深度分析 | abstraction-architect + systematic-debugging | Session 架构优化建议 |
| 06-performance-optimization-analysis | 性能优化分析 | 性能分析工具 + 自定义扫描 | 性能瓶颈识别、优化建议 |
| 07-test-coverage-gap-analysis | 测试覆盖率缺口分析 | 测试覆盖率工具 + 自定义分析 | 测试缺口清单、优先级排序 |
| 08-architecture-boundary-violations | 架构边界违规检测 | abstraction-architect + 自定义扫描 | 违规清单、修复建议 |

### Wave 3: 综合规划（依赖 Wave 2）

| Package | 分析类型 | 核心工具 | 产出 |
|---|---|---|---|
| 09-optimization-roadmap | 优化路线图 | 综合分析 | 优先级排序的优化路线图 |
| 10-executable-action-plan | 可执行行动计划 | 综合分析 | 详细实施计划、时间估算 |

### Final: 收口

| Package | 分析类型 | 核心工具 | 产出 |
|---|---|---|---|
| 99-finalize | 综合报告 | 所有分析结果整合 | 最终综合报告、执行摘要 |
