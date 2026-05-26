# A2: Technical Debt Audit - 技术债务审计

## Objective

使用 renewal-architect skill 对 OpenCamera 项目进行全面的技术债务审计。识别技术债务、评估现代化机会、制定治理策略。

## Analysis Scope

### 1. 技术债务分类
- **代码债务**：重复代码、复杂代码、坏味道
- **架构债务**：层次违规、紧耦合、职责不清
- **测试债务**：测试缺失、测试脆弱、覆盖不足
- **文档债务**：文档缺失、文档过时、文档不一致
- **依赖债务**：过时依赖、安全漏洞、许可证问题

### 2. 现代化机会评估
- Kotlin 语言特性利用程度
- Android Jetpack 组件采用情况
- CameraX API 使用深度
- 协程和 Flow 的使用模式
- 依赖注入框架应用

### 3. 治理策略制定
- 债务优先级排序
- 偿还策略（增量 vs 重构）
- 风险评估
- 资源估算

## Analysis Tools

### Primary: renewal-architect
- 遗留系统现代化分析
- 技术债务治理框架
- ROI 驱动的优化建议
- 安全的现代化策略

### Secondary: 静态分析工具
- 代码复杂度分析
- 重复代码检测
- 依赖分析

## Key Files to Analyze

### Build Configuration
- `build.gradle.kts` - 根构建配置
- `app/build.gradle.kts` - App 构建配置
- `settings.gradle.kts` - 项目设置
- `gradle.properties` - Gradle 属性

### Dependencies
- Gradle 依赖树
- AndroidX 版本
- CameraX 版本
- Kotlin 版本

### Code Patterns
- 协程使用模式
- Flow 使用模式
- 依赖注入模式
- 错误处理模式

## Analysis Output

产出文件：`output/02-technical-debt-audit.md`

### 报告结构
1. **执行摘要**：关键发现和建议
2. **债务清单**：分类的技术债务
3. **优先级矩阵**：影响 vs 难度
4. **现代化路线图**：现代化建议
5. **ROI 估算**：投资回报分析
6. **治理策略**：债务偿还策略

## Analysis Guidelines

- **只读不写**：分析源代码，但不修改任何文件
- **数据驱动**：基于实际代码数据进行分析
- **具体示例**：提供具体的代码示例和位置
- **可操作性**：提供具体、可执行的建议

## Expected Findings

1. Kotlin 现代特性的使用程度
2. CameraX API 的采用深度
3. 协程和 Flow 的使用模式
4. 依赖注入的应用情况
5. 测试覆盖和质量
6. 文档完整性和准确性
