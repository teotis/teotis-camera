# Package 03: Module Dependency Analysis

## Package ID
`03-module-dependency-analysis`

## Objective

使用 Gradle 依赖输出、rg/rg --files 和自定义分析工具对 OpenCamera 项目的模块依赖关系进行深度分析。识别循环依赖、过度耦合、缺失抽象等问题。

## Allowed Paths

- `docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md`
- `docs/plans/deep-audit-optimization-orchestration/status/state.tsv`
- `.tmp/` (temporary analysis files only)

## Forbidden Paths

- All source code files
- Build files
- Configuration files
- **任何运行时代码文件**（纯分析任务）

## Dependencies

None

## Acceptance Criteria

1. 产出详细的模块依赖分析报告到 status 文件
2. 生成模块依赖图（文本或可视化）
3. 识别所有循环依赖
4. 识别至少 5 个过度耦合问题
5. 提供模块边界优化建议

## Verification Commands

```bash
# 验证分析报告生成
ls -la docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md

# 验证报告内容完整性
wc -l docs/plans/deep-audit-optimization-orchestration/status/03-module-dependency-analysis.md

# 验证未修改源代码
git diff --name-only HEAD | grep -v "docs/plans/deep-audit-optimization-orchestration/status/" || echo "No source code changes"
```

## Expected Evidence

- 模块依赖图
- 循环依赖清单
- 耦合度度量
- 优化建议
- 验证命令执行结果
- 确认未修改任何源代码

## Branch Policy

- Branch: `agent/deep-audit/03-module-dependency-analysis`
- Worktree: `.worktrees/03-module-dependency-analysis`
- Base: `main`

## Unlock Condition

- 无依赖，可立即开始

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

### Primary: Gradle 与源码静态分析
- 符号索引和依赖分析
- 调用链追踪
- 影响范围分析

### Secondary: Gradle 依赖分析
- `./gradlew dependencies`
- `./gradlew :app:dependencies`
- 模块依赖可视化

## Key Modules to Analyze

### Core Modules
- `:core:session` - Session Kernel
- `:core:device` - Device Adapter
- `:core:media` - Media Pipeline
- `:core:mode` - Mode Plugin
- `:core:settings` - Settings
- `:core:capability` - Capability
- `:core:effect` - Effect

### Feature Modules
- `:feature:mode-photo` - 拍照模式
- `:feature:mode-video` - 录像模式
- `:feature:mode-night` - 夜景模式
- `:feature:mode-portrait` - 人像模式
- `:feature:mode-document` - 文档模式
- `:feature:mode-humanistic` - 人文模式
- `:feature:mode-pro` - 专业模式

### App Module
- `:app` - App 集成层
