# Deep Audit & Optimization Analysis - 全面深度检查与优化分析

## Goal

对 OpenCamera 项目进行全面、彻底、深度的项目检查、优化分析和架构分析。利用 abstraction-architect、renewal-architect、systematic-debugging 等专业分析技能，产出高质量的分析报告和优化建议。

**这是一个纯分析任务，不修改任何源代码，只产出分析报告。**

## Analysis Scope

### Wave 1: 基础分析（并行）

| ID | 分析类型 | 核心工具 | 产出文件 |
|---|---|---|---|
| A1 | 架构结构分析 | abstraction-architect | `output/01-architecture-structural-analysis.md` |
| A2 | 技术债务审计 | renewal-architect | `output/02-technical-debt-audit.md` |
| A3 | 模块依赖分析 | CodeGraph | `output/03-module-dependency-analysis.md` |
| A4 | 代码质量度量 | 静态分析 | `output/04-code-quality-metrics.md` |

### Wave 2: 深度分析（依赖 Wave 1）

| ID | 分析类型 | 核心工具 | 产出文件 |
|---|---|---|---|
| A5 | Session Kernel 深度分析 | abstraction-architect + systematic-debugging | `output/05-session-kernel-deep-dive.md` |
| A6 | 性能优化分析 | 性能分析工具 | `output/06-performance-optimization-analysis.md` |
| A7 | 测试覆盖率缺口分析 | 测试覆盖率工具 | `output/07-test-coverage-gap-analysis.md` |
| A8 | 架构边界违规检测 | abstraction-architect | `output/08-architecture-boundary-violations.md` |

### Wave 3: 综合规划（依赖 Wave 2）

| ID | 分析类型 | 核心工具 | 产出文件 |
|---|---|---|---|
| A9 | 优化路线图 | 综合分析 | `output/09-optimization-roadmap.md` |
| A10 | 可执行行动计划 | 综合分析 | `output/10-executable-action-plan.md` |

### Final: 综合报告

| ID | 分析类型 | 核心工具 | 产出文件 |
|---|---|---|---|
| A99 | 最终综合报告 | 所有分析结果整合 | `output/FINAL_REPORT.md` |

## Directory Structure

```
docs/plans/deep-audit-optimization-orchestration/
├── INDEX.md                    # 本文件
├── analysis/                   # 分析任务说明
│   ├── 01-architecture-structural-analysis.md
│   ├── 02-technical-debt-audit.md
│   ├── 03-module-dependency-analysis.md
│   ├── 04-code-quality-metrics.md
│   ├── 05-session-kernel-deep-dive.md
│   ├── 06-performance-optimization-analysis.md
│   ├── 07-test-coverage-gap-analysis.md
│   ├── 08-architecture-boundary-violations.md
│   ├── 09-optimization-roadmap.md
│   ├── 10-executable-action-plan.md
│   └── 99-finalize.md
└── output/                     # 分析产出
    ├── 01-architecture-structural-analysis.md
    ├── 02-technical-debt-audit.md
    ├── 03-module-dependency-analysis.md
    ├── 04-code-quality-metrics.md
    ├── 05-session-kernel-deep-dive.md
    ├── 06-performance-optimization-analysis.md
    ├── 07-test-coverage-gap-analysis.md
    ├── 08-architecture-boundary-violations.md
    ├── 09-optimization-roadmap.md
    ├── 10-executable-action-plan.md
    └── FINAL_REPORT.md
```

## Analysis Guidelines

### 1. 分析原则
- **只读不写**：分析源代码，但不修改任何文件
- **数据驱动**：基于实际代码数据进行分析
- **可操作性**：提供具体、可执行的建议
- **优先级明确**：按影响程度和实施难度排序

### 2. 分析方法
- **架构分析**：识别层次边界、职责分配、依赖方向
- **技术债务**：评估代码质量、现代化程度、维护成本
- **模块依赖**：分析耦合度、循环依赖、边界清晰度
- **性能分析**：识别瓶颈、优化机会、资源使用
- **测试分析**：评估覆盖率、质量、策略

### 3. 产出规范
- **Markdown 格式**：所有报告使用 Markdown
- **结构化内容**：包含目录、摘要、详细分析、建议
- **数据支撑**：包含具体数据、统计、示例
- **可追溯性**：标注代码位置、文件路径

## Analysis Tasks

### A1: Architecture Structural Analysis
**目标**：使用 abstraction-architect 进行结构性架构分析
**工具**：abstraction-architect, CodeGraph
**产出**：架构结构分析报告、抽象机会识别、边界问题检测

### A2: Technical Debt Audit
**目标**：使用 renewal-architect 进行技术债务审计
**工具**：renewal-architect, 静态分析
**产出**：技术债务清单、现代化路线图、治理策略

### A3: Module Dependency Analysis
**目标**：分析模块依赖关系和边界
**工具**：CodeGraph, Gradle 依赖分析
**产出**：依赖图、循环依赖检测、耦合度分析

### A4: Code Quality Metrics
**目标**：度量代码质量和复杂度
**工具**：静态分析、自定义扫描
**产出**：代码质量报告、复杂度热点、坏味道识别

### A5: Session Kernel Deep Dive
**目标**：深度分析 Session Kernel 架构
**工具**：abstraction-architect, systematic-debugging
**产出**：Session 架构优化建议、状态管理分析

### A6: Performance Optimization Analysis
**目标**：识别性能瓶颈和优化机会
**工具**：性能分析工具、代码分析
**产出**：性能瓶颈清单、优化建议

### A7: Test Coverage Gap Analysis
**目标**：分析测试覆盖率缺口
**工具**：测试覆盖率工具、自定义分析
**产出**：测试缺口清单、质量评估、改进建议

### A8: Architecture Boundary Violations
**目标**：检测架构边界违规
**工具**：abstraction-architect、自定义扫描
**产出**：违规清单、修复建议

### A9: Optimization Roadmap
**目标**：制定优先级排序的优化路线图
**工具**：综合分析
**产出**：优化路线图、优先级排序、时间估算

### A10: Executable Action Plan
**目标**：将路线图转化为可执行行动计划
**工具**：综合分析
**产出**：详细实施计划、代码位置、验证方法

### A99: Final Report
**目标**：整合所有分析结果
**工具**：所有分析结果整合
**产出**：最终综合报告、执行摘要

## Usage

### 启动分析

每个分析任务可以独立启动。在 Claude Code 中执行：

```
分析 OpenCamera 项目的架构结构，使用 abstraction-architect skill，将分析报告写入 docs/plans/deep-audit-optimization-orchestration/output/01-architecture-structural-analysis.md
```

### 查看产出

所有分析报告都在 `output/` 目录中，使用 Markdown 格式。

### 依赖关系

- Wave 1 的 4 个分析可以并行执行
- Wave 2 的分析依赖 Wave 1 的结果
- Wave 3 的分析依赖 Wave 2 的结果
- 最终报告依赖所有分析结果

## Project Context

**项目规模**：10,938 个 Kotlin 文件，571,619 行代码
**架构**：四层主链路（Mode Plugin / Session Kernel / Device Adapter / Media Pipeline）+ 横切治理能力
**当前状态**：Stage 7，进度 80%
**已有分析**：pragmatic_renewal_architect_report.html, structural_abstraction_architect_report.html

## Notes

- 这是纯分析任务，不修改任何源代码
- 所有产出都在 `output/` 目录中
- 分析报告使用 Markdown 格式
- 提供具体、可执行的建议
- 按优先级排序优化建议
