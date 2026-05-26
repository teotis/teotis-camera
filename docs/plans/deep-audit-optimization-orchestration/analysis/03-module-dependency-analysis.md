# A3: Module Dependency Analysis - 模块依赖分析

## Objective

使用 CodeGraph 和自定义分析工具对 OpenCamera 项目的模块依赖关系进行深度分析。识别循环依赖、过度耦合、缺失抽象等问题。

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

## Analysis Output

产出文件：`output/03-module-dependency-analysis.md`

### 报告结构
1. **执行摘要**：关键发现和建议
2. **模块依赖图**：可视化依赖关系
3. **循环依赖清单**：识别的循环依赖
4. **耦合度分析**：模块间耦合度
5. **边界问题**：检测到的边界问题
6. **优化建议**：具体改进建议

## Analysis Guidelines

- **只读不写**：分析源代码，但不修改任何文件
- **数据驱动**：基于实际代码数据进行分析
- **具体示例**：提供具体的代码示例和位置
- **可操作性**：提供具体、可执行的建议

## Expected Findings

1. 模块间的依赖关系是否合理
2. 是否存在循环依赖
3. 模块边界是否清晰
4. 接口隔离是否恰当
5. 依赖方向是否符合架构原则
