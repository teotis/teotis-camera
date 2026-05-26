# A4: Code Quality Metrics - 代码质量度量

## Objective

对 OpenCamera 项目的代码质量进行全面度量。识别代码复杂度热点、重复代码、坏味道等问题，为优化提供数据支持。

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

## Key Files to Analyze

### Large Files (High Priority)
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` (132KB)
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` (44KB)
- `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt` (49KB)
- `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt` (41KB)

### Core Session Files
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`

### App Layer Files
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`

## Analysis Output

产出文件：`output/04-code-quality-metrics.md`

### 报告结构
1. **执行摘要**：关键发现和建议
2. **复杂度统计**：复杂度分布和热点
3. **重复代码**：重复代码统计和热点
4. **坏味道清单**：识别的代码坏味道
5. **质量评分**：整体质量评分
6. **改进建议**：具体改进建议

## Analysis Guidelines

- **只读不写**：分析源代码，但不修改任何文件
- **数据驱动**：基于实际代码数据进行分析
- **具体示例**：提供具体的代码示例和位置
- **可操作性**：提供具体、可执行的建议

## Expected Findings

1. 代码复杂度分布情况
2. 重复代码的程度和位置
3. 常见的代码坏味道
4. 需要重构的热点区域
5. 整体代码质量评分
