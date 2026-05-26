# Package 03: Module Dependency Analysis - 模块依赖分析

## Package ID
`03-module-dependency-analysis`

## Objective

使用 CodeGraph 和自定义分析工具对 OpenCamera 项目的模块依赖关系进行深度分析。识别循环依赖、过度耦合、缺失抽象等问题。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files)

## Forbidden Paths

- All source code files
- Build files
- Configuration files

## Analysis Scope

### 1. 模块依赖图构建
- Gradle 模块依赖关系
- 包级别依赖关系
- 类级别依赖关系
- 接口依赖关系

### 2. 依赖问题检测
- 循环依赖识别
- 过度耦合模块
- 缺失抽象层
- 依赖方向违规
- 稳定依赖原则违反

### 3. 模块边界分析
- 模块职责清晰度
- 接口隔离程度
- 依赖倒置应用
- 模块内聚度

## Analysis Tools

### Primary: CodeGraph
- 符号索引和依赖分析
- 调用链追踪
- 影响范围分析

### Secondary: Gradle 依赖分析
- `./gradlew dependencies`
- `./gradlew :app:dependencies`
- 模块依赖可视化

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md

# Gradle 依赖分析
./gradlew :app:dependencies --configuration implementation
```

## Acceptance Criteria

1. 产出详细的模块依赖分析报告
2. 生成模块依赖图（文本或可视化）
3. 识别所有循环依赖
4. 识别至少 5 个过度耦合问题
5. 提供模块边界优化建议

## Evidence Requirements

- 模块依赖图
- 循环依赖清单
- 耦合度度量
- 优化建议
- 验证命令执行结果

## Branch Policy

- Branch: `agent/deep-audit/03-module-dependency-analysis`
- Worktree: `.worktrees/03-module-dependency-analysis`
- Base: `main`

## Unlock Condition

- 无依赖，可立即开始

## Expected Duration

- 分析时间：30-60 分钟
- 报告生成：20-30 分钟

## Notes

- 关注 core 模块之间的依赖关系
- 分析 feature 模块对 core 的依赖模式
- 识别可提取的公共依赖
- 不要修改任何源代码
