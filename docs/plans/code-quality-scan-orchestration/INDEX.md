# 全项目代码质量扫描 - 编排索引

## Goal

对 OpenCamera 项目进行全面代码质量扫描（只读，不自动修复），覆盖三个维度：静态分析（detekt + Android Lint + ktlint）、架构合规（模块依赖方向、越层调用、圈复杂度）、测试覆盖（运行测试 + 统计覆盖情况）。所有结果合并为一份大报告。

## User Entry Points

- Manual: 复制 `launchers/agent-prompts.md` 中的 prompt 到任意 agent 平台
- Script: `bash launchers/orchestrate.sh start`；用 `claude agents` 查看
- Status: `bash launchers/orchestrate.sh status`
- Retry: `bash launchers/orchestrate.sh retry <package-id>`
- Manual advancement fallback: `bash launchers/orchestrate.sh advance`

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/code-quality-scan-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/quality-scan/integration`
- Functional package branches: `agent/quality-scan/<package-id>`
- Implementation isolation: one worktree per functional package
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch
- Edit only allowed paths (mainly scratch/ for scan reports)
- Run listed verification commands
- Write only their assigned coordinator status file
- Update state ledger only through `bash <plan-root>/launchers/orchestrate.sh mark-state ...`
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded by this orchestration
- delete remote branches
- add secrets or credentials
- edit source code (this is scan-only)

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-static-analysis | none | status | completed | 1 |
| 02-arch-compliance | none | status | completed | 1 |
| 03-test-coverage | none | status | completed | 1 |
| 99-finalize | 01-static-analysis, 02-arch-compliance, 03-test-coverage | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01-static-analysis, 02-arch-compliance, 03-test-coverage
- Code dependency policy: status dependency (each package writes reports independently)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes
- Cleanup: delete only recorded local package worktrees/branches after finalize succeeds

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`
- Graph has duplicate package IDs, missing dependencies, or cycles
- Package evidence is incomplete
- Package changed forbidden paths (source code modifications)
- Merge conflict or verification failure occurs

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-static-analysis | autonomous | Claude Code | n/a | detekt/lint/ktlint reports | none | normal graph |
| 02-arch-compliance | autonomous | Claude Code | n/a | dependency graph + violation report | none | normal graph |
| 03-test-coverage | autonomous | Claude Code | n/a | test run results + coverage stats | none | normal graph |
| 99-finalize | autonomous | Claude Code | n/a | merged scan report | none | normal graph |

## Scan Dimensions

### 01 静态分析
- **detekt**: Kotlin 静态分析（代码风格、潜在 bug、性能、安全）
- **Android Lint**: Android 特有问题检查
- **ktlint**: Kotlin 代码格式检查
- 所有工具仅生成报告，不自动修复

### 02 架构合规
- 模块依赖方向检查（feature → core → app 是否合规）
- 越层调用检测（feature 直接调用其他 feature、core 调用 app 等）
- 圈复杂度分析
- 文件/类大小统计

### 03 测试覆盖
- 运行全部单元测试
- 统计各模块测试通过/失败/跳过
- 统计测试文件分布（哪些模块有测试、哪些没有）
- 生成测试结果汇总
